package com.eazyfreight.logistics.controller;

import com.eazyfreight.support.FixedClockConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockConfiguration.class)
class LogisticsControllerTest {

    private static final String DRIVER = "9f8e7d6c-5b4a-3c2d-1e0f-9a8b7c6d5e4f";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unknownBookingReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/logistics/bookings/3fa85f64-5717-4562-b3fc-2c963f66afa6"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void noTruckGoesOutBeforeTheCarrierConfirms() throws Exception {
        String bookingId = requestedBooking();

        mockMvc.perform(post("/api/logistics/bookings/" + bookingId + "/outbound-dispatch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dispatchPayload("Carrier yard", "Customer premises")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("before the carrier confirms")));
    }

    @Test
    void theItnGateBlocksTheInboundTruckUntilComplianceClearsIt() throws Exception {
        String bookingId = confirmedBooking();
        sealedContainer(bookingId);

        mockMvc.perform(get("/api/logistics/bookings/" + bookingId + "/itn-gate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clear").value(false))
                .andExpect(jsonPath("$.reason").value(containsString("ITN not yet received")));

        mockMvc.perform(post("/api/logistics/bookings/" + bookingId + "/inbound-dispatch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dispatchPayload("Customer premises", "APM Terminals")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("Inbound dispatch refused")));

        // File the EEI. Acceptance publishes ItnGateCheckPassed, which the logistics
        // context consumes — nothing here touches the gate directly.
        acceptedFiling(bookingId);

        mockMvc.perform(get("/api/logistics/bookings/" + bookingId + "/itn-gate"))
                .andExpect(jsonPath("$.clear").value(true))
                .andExpect(jsonPath("$.reason").doesNotExist());

        mockMvc.perform(post("/api/logistics/bookings/" + bookingId + "/inbound-dispatch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dispatchPayload("Customer premises", "APM Terminals")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stage").value("INBOUND_DISPATCHED"))
                .andExpect(jsonPath("$.documentationPreconditionsMet").value(true));
    }

    @Test
    void aCustomsExaminationReplacesTheSealAndKeepsBoth() throws Exception {
        String bookingId = confirmedBooking();
        sealedContainer(bookingId);
        acceptedFiling(bookingId);

        mockMvc.perform(post("/api/logistics/bookings/" + bookingId + "/examination-hold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cbpOfficerId\":\"CBP-4471\",\"notes\":\"Random intensive exam\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("UNDER_CBP_EXAMINATION"));

        // A release without a replacement seal is refused — CBP cut the original.
        mockMvc.perform(post("/api/logistics/bookings/" + bookingId + "/examination-release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"RELEASED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("replacement seal number is required")));

        mockMvc.perform(post("/api/logistics/bookings/" + bookingId + "/examination-release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"RELEASED\",\"replacementSealNumber\":\"CBP778899\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeSealNumber").value("CBP778899"))
                .andExpect(jsonPath("$.sealRecords.length()").value(2))
                // The seal the customer applied survives the inspection.
                .andExpect(jsonPath("$.sealRecords[0].sealNumber").value("SEAL123456"))
                .andExpect(jsonPath("$.sealRecords[0].active").value(false))
                .andExpect(jsonPath("$.sealRecords[0].deactivationReason").value("CUSTOMS_INSPECTION"))
                .andExpect(jsonPath("$.sealRecords[0].replacedBySealId").exists())
                .andExpect(jsonPath("$.sealRecords[1].sealSource").value("CUSTOMS_ISSUED"));
    }

    @Test
    void earlyTerminalDeliveryComputesStorageFeeExposure() throws Exception {
        String bookingId = confirmedBooking();
        sealedContainer(bookingId);
        acceptedFiling(bookingId);
        mockMvc.perform(post("/api/logistics/bookings/" + bookingId + "/inbound-dispatch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dispatchPayload("Customer premises", "APM Terminals")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/logistics/bookings/" + bookingId + "/loaded-container-picked-up"))
                .andExpect(status().isOk());

        // The fixed clock is 2024-03-15; the terminal opens for this vessel on 03-18.
        mockMvc.perform(post("/api/logistics/bookings/" + bookingId + "/terminal-receipt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "gateReceiptNumber": "GR-99887",
                                  "terminalName": "APM Terminals",
                                  "earliestAcceptanceDate": "2024-03-18",
                                  "vesselCutOffDate": "2024-03-25",
                                  "storageFeeDailyRate": 150.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("AT_TERMINAL"))
                .andExpect(jsonPath("$.terminalAcceptance.storageFeeApplies").value(true))
                .andExpect(jsonPath("$.terminalAcceptance.daysEarly").value(3))
                .andExpect(jsonPath("$.terminalAcceptance.estimatedStorageFee").value(450.00));
    }

    @Test
    void actualCargoDivergenceFlagsTheFilingForAmendment() throws Exception {
        String bookingId = confirmedBooking();
        sealedContainer(bookingId);
        String filingId = acceptedFiling(bookingId);

        // Booked 500 kg; 620 kg actually went in.
        mockMvc.perform(post("/api/logistics/bookings/" + bookingId + "/actual-cargo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualWeightKg\":620.000,\"actualPieces\":10,\"actualCbm\":10.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actualCargoDetails.divergesMaterially").value(true))
                .andExpect(jsonPath("$.actualCargoDetails.weightVarianceKg").value(120.000));

        // The filing is still accepted; the amendment requirement is raised as an event.
        mockMvc.perform(get("/api/compliance/filings/" + filingId))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void anItnFiledBeforeTheContainerExistsStillOpensTheGate() throws Exception {
        String bookingId = confirmedBooking();

        // Compliance files as soon as the carrier confirms — before anyone books a
        // truck, so there is no container assignment for the gate event to land on.
        acceptedFiling(bookingId);
        sealedContainer(bookingId);

        mockMvc.perform(get("/api/logistics/bookings/" + bookingId + "/itn-gate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clear").value(true));

        mockMvc.perform(post("/api/logistics/bookings/" + bookingId + "/inbound-dispatch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dispatchPayload("Customer premises", "APM Terminals")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.itnReceived").value(true));
    }

    @Test
    void containerNumberIsRecordedLateAndOnlyOnce() throws Exception {
        String bookingId = confirmedBooking();
        mockMvc.perform(post("/api/logistics/bookings/" + bookingId + "/outbound-dispatch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dispatchPayload("Carrier yard", "Customer premises")))
                .andExpect(status().isCreated())
                // No container number at dispatch — it is not known yet.
                .andExpect(jsonPath("$.containerNumber").doesNotExist())
                .andExpect(jsonPath("$.stage").value("OUTBOUND_DISPATCHED"));

        mockMvc.perform(post("/api/logistics/bookings/" + bookingId + "/container-number")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"containerNumber\":\"MSCU1234567\",\"source\":\"CARRIER_YARD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.containerNumber").value("MSCU1234567"));

        mockMvc.perform(post("/api/logistics/bookings/" + bookingId + "/container-number")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"containerNumber\":\"MSCU7654321\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("already assigned")));
    }

    // ----------------------------------------------------------------- fixtures

    private String dispatchPayload(String pickup, String delivery) {
        return """
                {
                  "driverId": "%s",
                  "pickupAddress": "%s",
                  "deliveryAddress": "%s"
                }
                """.formatted(DRIVER, pickup, delivery);
    }

    private void sealedContainer(String bookingId) throws Exception {
        mockMvc.perform(post("/api/logistics/bookings/" + bookingId + "/outbound-dispatch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dispatchPayload("Carrier yard", "Customer premises")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/logistics/bookings/" + bookingId + "/container-number")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"containerNumber\":\"MSCU1234567\",\"source\":\"CARRIER_YARD\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/logistics/bookings/" + bookingId + "/delivered-to-customer"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/logistics/bookings/" + bookingId + "/loading-complete"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/logistics/bookings/" + bookingId + "/seal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sealNumber\":\"SEAL123456\"}"))
                .andExpect(status().isOk());
    }

    private String acceptedFiling(String bookingId) throws Exception {
        String filingId = JsonPath.parse(
                mockMvc.perform(post("/api/compliance/bookings/" + bookingId + "/filings"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString()).read("$.id");
        mockMvc.perform(post("/api/compliance/filings/" + filingId + "/compile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shipperName": "Acme", "shipperEin": "12-3456789",
                                  "consigneeName": "Sing Pte", "consigneeCountry": "SG",
                                  "scheduleBNumber": "8471.30.0100",
                                  "commodityDescription": "Machine parts",
                                  "quantityValue": 10, "quantityUnit": "PCS", "valueUsd": 50000.00,
                                  "carrierScac": "MAEU", "portOfExportCode": "INBOM",
                                  "countryOfDestination": "SG", "estimatedEtd": "2026-10-16",
                                  "licenseRequired": false
                                }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/compliance/filings/" + filingId + "/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
        return filingId;
    }

    private String requestedBooking() throws Exception {
        return JsonPath.parse(mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
                                  "shipperId": "aabbccdd-1122-3344-5566-778899aabbcc",
                                  "consigneeId": "bbccddee-2233-4455-6677-8899aabbccdd",
                                  "shippingMode": "OCEAN_FCL",
                                  "originPortCode": "INBOM", "destinationPortCode": "SGSIN",
                                  "incoterms": "FOB", "requestedEtd": "2026-10-15",
                                  "transportRequired": true,
                                  "pickupAddress": "Plot 14, MIDC Andheri",
                                  "cargoDetails": [{
                                    "description": "Machine parts", "hsCode": "8471.30.0100",
                                    "pieces": 10, "weightKg": 500.000, "valueUsd": 50000.00,
                                    "lengthCm": 100.00, "widthCm": 100.00, "heightCm": 100.00,
                                    "hazmat": false, "temperatureControlled": false, "oversized": false
                                  }]
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).read("$.id");
    }

    private String confirmedBooking() throws Exception {
        String bookingId = requestedBooking();
        mockMvc.perform(post("/api/bookings/" + bookingId + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bookingSourceType":"DIRECT_CARRIER",
                                 "carrierId":"1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
                                 "containerType":"FORTY_HC","numberOfContainers":1}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/bookings/" + bookingId + "/carrier-confirmation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"carrierBookingRef":"MAEU987654","vesselName":"MAERSK SEALAND",
                                 "voyageNumber":"024W","confirmedEtd":"2026-10-16",
                                 "confirmedEta":"2026-11-09"}
                                """))
                .andExpect(status().isOk());
        return bookingId;
    }
}
