package com.eazyfreight.documentation;

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

/**
 * The Documentation track is the convergence point, so these tests are mostly
 * about what it refuses to do until the other three tracks have delivered.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockConfiguration.class)
class DocumentationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unknownHouseBolReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/documentation/house-bols/3fa85f64-5717-4562-b3fc-2c963f66afa6"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void preconditionsReportExactlyWhatIsStillMissing() throws Exception {
        String bookingId = confirmedBooking();

        mockMvc.perform(get("/api/documentation/bookings/" + bookingId + "/preconditions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.met").value(false))
                .andExpect(jsonPath("$.missing").value(
                        org.hamcrest.Matchers.containsInAnyOrder(
                                "containerNumber", "sealNumber", "itnNumber")))
                // The carrier reference is already in hand from the booking.
                .andExpect(jsonPath("$.carrierBookingRef").value("MAEU987654"));

        mockMvc.perform(post("/api/documentation/bookings/" + bookingId + "/instructions")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("still waiting on")));
    }

    @Test
    void instructionsCarryTheContainerSealAndItnAsProofOfWhatWasSent() throws Exception {
        String bookingId = readyForDocumentation();

        mockMvc.perform(get("/api/documentation/bookings/" + bookingId + "/preconditions"))
                .andExpect(jsonPath("$.met").value(true))
                .andExpect(jsonPath("$.missing").isEmpty());

        String instructions = mockMvc.perform(
                        post("/api/documentation/bookings/" + bookingId + "/instructions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(instructionsPayload()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.containerNumber").value("MSCU1234567"))
                .andExpect(jsonPath("$.sealNumber").value("SEAL123456"))
                .andExpect(jsonPath("$.itnNumber").value(
                        org.hamcrest.Matchers.matchesPattern("X99999999\\d{6}")))
                .andReturn().getResponse().getContentAsString();

        String instructionsId = JsonPath.parse(instructions).read("$.id");

        // Sending before approval is refused.
        mockMvc.perform(post("/api/documentation/instructions/" + instructionsId + "/send"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("approved")));

        mockMvc.perform(post("/api/documentation/instructions/" + instructionsId + "/approve"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/documentation/instructions/" + instructionsId + "/send"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.sentAt").exists());
    }

    @Test
    void aHouseBolCannotBeIssuedUntilTheMasterBolIsVerified() throws Exception {
        String bookingId = readyForDocumentation();
        String instructionsId = sentInstructions(bookingId);

        String master = mockMvc.perform(
                        post("/api/documentation/instructions/" + instructionsId + "/master-bol")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"masterBolNumber\":\"MAEU-MBL-778899\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        String masterId = JsonPath.parse(master).read("$.id");

        mockMvc.perform(post("/api/documentation/master-bols/" + masterId + "/house-bol")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"releaseType\":\"TELEX_RELEASE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("must be verified")));

        mockMvc.perform(post("/api/documentation/master-bols/" + masterId + "/verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true));

        mockMvc.perform(post("/api/documentation/master-bols/" + masterId + "/house-bol")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"releaseType\":\"TELEX_RELEASE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.revisionNumber").value(0))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.containerNumber").value("MSCU1234567"))
                .andExpect(jsonPath("$.sealNumber").value("SEAL123456"));
    }

    @Test
    void aDiscrepancyMustNameFieldsAndBlocksVerification() throws Exception {
        String bookingId = readyForDocumentation();
        String instructionsId = sentInstructions(bookingId);
        String masterId = masterBolReceived(instructionsId);

        mockMvc.perform(post("/api/documentation/master-bols/" + masterId + "/discrepancy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"discrepancyFields\":[]}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/documentation/master-bols/" + masterId + "/discrepancy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"discrepancyFields\":[\"sealNumber\",\"grossWeight\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("DISCREPANCY_RAISED"))
                .andExpect(jsonPath("$.discrepancyFields").value(
                        org.hamcrest.Matchers.contains("sealNumber", "grossWeight")));

        mockMvc.perform(post("/api/documentation/master-bols/" + masterId + "/verify"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("Resolve the discrepancy")));

        mockMvc.perform(post("/api/documentation/master-bols/" + masterId + "/correction")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correctedMasterBolNumber\":\"MAEU-MBL-778900\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("CORRECTED"));

        mockMvc.perform(post("/api/documentation/master-bols/" + masterId + "/verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true));
    }

    @Test
    void amendingProducesANewRevisionAndVoidsTheOld() throws Exception {
        String bookingId = readyForDocumentation();
        String houseBolId = issuedHouseBol(bookingId, "TELEX_RELEASE");

        String number = JsonPath.parse(
                        mockMvc.perform(get("/api/documentation/house-bols/" + houseBolId))
                                .andReturn().getResponse().getContentAsString())
                .read("$.houseBolNumber");

        mockMvc.perform(post("/api/documentation/house-bols/" + houseBolId + "/amend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Consignee address corrected\","
                                + "\"consigneeAddress\":\"12 Marina Boulevard\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.houseBolNumber").value(number))
                .andExpect(jsonPath("$.revisionNumber").value(1))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.consigneeAddressSnapshot").value("12 Marina Boulevard"));

        // Rev 0 still exists, voided — the revision the consignee is holding.
        mockMvc.perform(get("/api/documentation/house-bols/" + houseBolId))
                .andExpect(jsonPath("$.revisionNumber").value(0))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.status").value("VOIDED"))
                .andExpect(jsonPath("$.supersededByHouseBolId").exists())
                .andExpect(jsonPath("$.consigneeAddressSnapshot").value("9 Raffles Place"));

        mockMvc.perform(get("/api/documentation/house-bols/by-number/" + number + "/revisions"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].revisionNumber").value(0))
                .andExpect(jsonPath("$[1].revisionNumber").value(1));
    }

    @Test
    void outstandingOriginalsBlockAmendmentOverHttp() throws Exception {
        String bookingId = readyForDocumentation();
        String houseBolId = issuedHouseBol(bookingId, "ORIGINAL_BOL");

        mockMvc.perform(post("/api/documentation/house-bols/" + houseBolId + "/originals/release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"releasedTo\":\"Acme Manufacturing\",\"courierReference\":\"DHL-8891\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originals.originalsIssued").value(3))
                .andExpect(jsonPath("$.originals.outstanding").value(3))
                .andExpect(jsonPath("$.amendmentBlockedReason").value(
                        containsString("3 of 3 negotiable originals")));

        mockMvc.perform(post("/api/documentation/house-bols/" + houseBolId + "/amend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Vessel changed\",\"vesselName\":\"MAERSK KOWLOON\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("still outstanding")));

        mockMvc.perform(post("/api/documentation/house-bols/" + houseBolId + "/originals/surrender")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"count\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originals.allSurrendered").value(true))
                .andExpect(jsonPath("$.amendmentBlockedReason").doesNotExist());

        mockMvc.perform(post("/api/documentation/house-bols/" + houseBolId + "/amend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Vessel changed\",\"vesselName\":\"MAERSK KOWLOON\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vesselName").value("MAERSK KOWLOON"));
    }

    @Test
    void distributionRecordsWhoReceivedWhichRevision() throws Exception {
        String bookingId = readyForDocumentation();
        String houseBolId = issuedHouseBol(bookingId, "SEA_WAYBILL");

        mockMvc.perform(post("/api/documentation/house-bols/" + houseBolId + "/distribute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipient\":\"SHIPPER\",\"recipientName\":\"Acme Manufacturing\","
                                + "\"channel\":\"EMAIL\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/documentation/house-bols/" + houseBolId + "/distribute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipient\":\"CONSIGNEE_AGENT\","
                                + "\"recipientName\":\"Singapore Trading Pte\",\"channel\":\"EMAIL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distributions.length()").value(2))
                .andExpect(jsonPath("$.distributions[0].recipient").value("SHIPPER"))
                .andExpect(jsonPath("$.distributions[0].revisionNumber").value(0));
    }

    @Test
    void onlyOneActiveHouseBolPerBooking() throws Exception {
        String bookingId = readyForDocumentation();
        String houseBolId = issuedHouseBol(bookingId, "TELEX_RELEASE");
        String masterId = JsonPath.parse(
                        mockMvc.perform(get("/api/documentation/house-bols/" + houseBolId))
                                .andReturn().getResponse().getContentAsString())
                .read("$.masterBolId");

        mockMvc.perform(post("/api/documentation/master-bols/" + masterId + "/house-bol")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"releaseType\":\"TELEX_RELEASE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("already exists")));
    }

    // ----------------------------------------------------------------- fixtures

    private String instructionsPayload() {
        return """
                {
                  "shipperName": "Acme Manufacturing",
                  "shipperAddress": "Plot 14, MIDC Andheri",
                  "consigneeName": "Singapore Trading Pte",
                  "consigneeAddress": "9 Raffles Place",
                  "freightTerms": "PREPAID",
                  "documentationCutOffDate": "2026-10-13"
                }
                """;
    }

    private String sentInstructions(String bookingId) throws Exception {
        String id = JsonPath.parse(mockMvc.perform(
                        post("/api/documentation/bookings/" + bookingId + "/instructions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(instructionsPayload()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).read("$.id");
        mockMvc.perform(post("/api/documentation/instructions/" + id + "/approve"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/documentation/instructions/" + id + "/send"))
                .andExpect(status().isOk());
        return id;
    }

    private String masterBolReceived(String instructionsId) throws Exception {
        return JsonPath.parse(mockMvc.perform(
                        post("/api/documentation/instructions/" + instructionsId + "/master-bol")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"masterBolNumber\":\"MAEU-MBL-778899\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).read("$.id");
    }

    private String issuedHouseBol(String bookingId, String releaseType) throws Exception {
        String instructionsId = sentInstructions(bookingId);
        String masterId = masterBolReceived(instructionsId);
        mockMvc.perform(post("/api/documentation/master-bols/" + masterId + "/verify"))
                .andExpect(status().isOk());
        return JsonPath.parse(mockMvc.perform(
                        post("/api/documentation/master-bols/" + masterId + "/house-bol")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"releaseType\":\"" + releaseType + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).read("$.id");
    }

    /** A booking with container, seal and ITN all in hand. */
    private String readyForDocumentation() throws Exception {
        String bookingId = confirmedBooking();
        mockMvc.perform(post("/api/logistics/bookings/" + bookingId + "/outbound-dispatch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driverId\":\"9f8e7d6c-5b4a-3c2d-1e0f-9a8b7c6d5e4f\","
                                + "\"pickupAddress\":\"Carrier yard\",\"deliveryAddress\":\"Plot 14\"}"))
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

        String filingId = JsonPath.parse(mockMvc.perform(
                        post("/api/compliance/bookings/" + bookingId + "/filings"))
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
        return bookingId;
    }

    private String confirmedBooking() throws Exception {
        String bookingId = JsonPath.parse(mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
                                  "shipperId": "aabbccdd-1122-3344-5566-778899aabbcc",
                                  "consigneeId": "bbccddee-2233-4455-6677-8899aabbccdd",
                                  "shippingMode": "OCEAN_FCL",
                                  "originPortCode": "INBOM", "destinationPortCode": "SGSIN",
                                  "incoterms": "FOB", "requestedEtd": "2026-10-15",
                                  "transportRequired": true, "pickupAddress": "Plot 14",
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
