package com.eazyfreight.compliance;

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
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockConfiguration.class)
class ComplianceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unknownFilingReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/compliance/filings/3fa85f64-5717-4562-b3fc-2c963f66afa6"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void theApiDeclaresThatFilingsAreSimulated() throws Exception {
        mockMvc.perform(get("/api/compliance/filing-system"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulated").value(true))
                .andExpect(jsonPath("$.notice").value(containsString("No Electronic Export Information")));
    }

    @Test
    void filingRunsFromInitiationThroughSubmissionToItn() throws Exception {
        String bookingId = confirmedBooking();

        String filing = mockMvc.perform(post("/api/compliance/bookings/" + bookingId + "/filings")
                        .header("X-Actor", "compliance.sam"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.filingType").value("ORIGINAL"))
                .andExpect(jsonPath("$.filingReference").value(matchesPattern("EEI-2024-\\d{5}")))
                // Prefilled from the booking, but the HS code is not a Schedule B number.
                .andExpect(jsonPath("$.scheduleBTranslated").value(false))
                .andReturn().getResponse().getContentAsString();

        String filingId = JsonPath.parse(filing).read("$.id");

        // CBP-required fields are still blank, so submission is refused.
        mockMvc.perform(post("/api/compliance/filings/" + filingId + "/submit"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("CBP requires")));

        mockMvc.perform(post("/api/compliance/filings/" + filingId + "/compile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(compilePayload("Machine parts", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missingRequiredFields").isEmpty())
                .andExpect(jsonPath("$.filingRequired").value(true));

        mockMvc.perform(post("/api/compliance/filings/" + filingId + "/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.simulated").value(true))
                .andExpect(jsonPath("$.activeItnNumber").value(matchesPattern("X99999999\\d{6}")))
                .andExpect(jsonPath("$.itnRecords[0].active").value(true));

        // The booking now knows an ITN is on file, via the event listener.
        mockMvc.perform(get("/api/bookings/" + bookingId))
                .andExpect(jsonPath("$.itnFiled").value(true));
    }

    @Test
    void aRejectedFilingIsCorrectedIntoANewOriginal() throws Exception {
        String bookingId = confirmedBooking();
        String filingId = draftFiling(bookingId);

        // The simulator rejects any commodity description containing REJECT.
        mockMvc.perform(post("/api/compliance/filings/" + filingId + "/compile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(compilePayload("REJECT this description", false)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/compliance/filings/" + filingId + "/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReasonCode").value("127"))
                .andExpect(jsonPath("$.activeItnNumber").doesNotExist());

        mockMvc.perform(post("/api/compliance/filings/" + filingId + "/correct"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filingType").value("ORIGINAL"))
                .andExpect(jsonPath("$.parentFilingId").value(filingId))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void anAmendmentSupersedesThePreviousItn() throws Exception {
        String bookingId = confirmedBooking();
        String originalId = acceptedFiling(bookingId);

        String firstItn = JsonPath.parse(
                        mockMvc.perform(get("/api/compliance/filings/" + originalId))
                                .andReturn().getResponse().getContentAsString())
                .read("$.activeItnNumber");

        String amendment = mockMvc.perform(post("/api/compliance/filings/" + originalId + "/amend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Vessel changed after reinstatement\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filingType").value("AMENDMENT"))
                .andExpect(jsonPath("$.parentFilingId").value(originalId))
                .andReturn().getResponse().getContentAsString();

        String amendmentId = JsonPath.parse(amendment).read("$.id");

        mockMvc.perform(post("/api/compliance/filings/" + amendmentId + "/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        String newItn = JsonPath.parse(
                        mockMvc.perform(get("/api/compliance/filings/" + amendmentId))
                                .andReturn().getResponse().getContentAsString())
                .read("$.activeItnNumber");

        org.assertj.core.api.Assertions.assertThat(newItn).isNotEqualTo(firstItn);

        // The original ITN is retained, no longer active, pointing at its replacement.
        mockMvc.perform(get("/api/compliance/filings/" + originalId))
                .andExpect(jsonPath("$.itnRecords[0].itnNumber").value(firstItn))
                .andExpect(jsonPath("$.itnRecords[0].active").value(false))
                .andExpect(jsonPath("$.itnRecords[0].supersededByItnId").exists())
                .andExpect(jsonPath("$.activeItnNumber").doesNotExist());
    }

    @Test
    void aLicensableCommodityBlocksSubmissionUntilTheLicenceIsRecorded() throws Exception {
        String bookingId = confirmedBooking();
        String filingId = draftFiling(bookingId);

        mockMvc.perform(post("/api/compliance/filings/" + filingId + "/compile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(compilePayload("Encryption hardware", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.licenseRequired").value(true));

        mockMvc.perform(post("/api/compliance/filings/" + filingId + "/submit"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("export licence")));

        mockMvc.perform(post("/api/compliance/filings/" + filingId + "/export-license")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "licenseNumber": "BIS-12345",
                                  "issuingAuthority": "BIS",
                                  "licenseType": "Individual",
                                  "commodityEccn": "5A002",
                                  "validFrom": "2026-01-01",
                                  "validUntil": "2027-01-01",
                                  "valueAuthorized": 100000.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exportLicense.licenseNumber").value("BIS-12345"));

        mockMvc.perform(post("/api/compliance/filings/" + filingId + "/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void cancellingAFilingVoidsTheItn() throws Exception {
        String bookingId = confirmedBooking();
        String filingId = acceptedFiling(bookingId);

        mockMvc.perform(post("/api/compliance/filings/" + filingId + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Shipment cancelled by customer\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.activeItnNumber").doesNotExist())
                .andExpect(jsonPath("$.itnRecords[0].active").value(false));
    }

    @Test
    void anAesOutageLeavesTheFilingSubmittedForRetryRatherThanGuessing() throws Exception {
        String bookingId = confirmedBooking();
        String filingId = draftFiling(bookingId);

        mockMvc.perform(post("/api/compliance/filings/" + filingId + "/compile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(compilePayload("UNAVAILABLE simulated outage", false)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/compliance/filings/" + filingId + "/submit"))
                .andExpect(status().isOk())
                // Not accepted, not rejected — no answer came back, so none is invented.
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.activeItnNumber").doesNotExist());
    }

    @Test
    void aMalformedItnIsRejectedOnManualEntry() throws Exception {
        String bookingId = confirmedBooking();
        String filingId = draftFiling(bookingId);
        mockMvc.perform(post("/api/compliance/filings/" + filingId + "/compile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(compilePayload("UNAVAILABLE outage", false)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/compliance/filings/" + filingId + "/submit"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/compliance/filings/" + filingId + "/acceptance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itnNumber\":\"NOT-AN-ITN\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("X followed by 14 digits")));
    }

    @Test
    void aBookingCannotHaveTwoOpenFilings() throws Exception {
        String bookingId = confirmedBooking();
        draftFiling(bookingId);

        mockMvc.perform(post("/api/compliance/bookings/" + bookingId + "/filings"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("already has an open EEI filing")));
    }

    // ----------------------------------------------------------------- fixtures

    private String draftFiling(String bookingId) throws Exception {
        return JsonPath.parse(
                mockMvc.perform(post("/api/compliance/bookings/" + bookingId + "/filings"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString()).read("$.id");
    }

    private String acceptedFiling(String bookingId) throws Exception {
        String filingId = draftFiling(bookingId);
        mockMvc.perform(post("/api/compliance/filings/" + filingId + "/compile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(compilePayload("Machine parts", false)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/compliance/filings/" + filingId + "/submit"))
                .andExpect(status().isOk());
        return filingId;
    }

    private String compilePayload(String description, boolean licenseRequired) {
        return """
                {
                  "shipperName": "Acme Manufacturing",
                  "shipperEin": "12-3456789",
                  "shipperAddress": "Plot 14, MIDC Andheri",
                  "consigneeName": "Singapore Trading Pte",
                  "consigneeAddress": "9 Raffles Place",
                  "consigneeCountry": "SG",
                  "scheduleBNumber": "8471.30.0100",
                  "commodityDescription": "%s",
                  "quantityValue": 10,
                  "quantityUnit": "PCS",
                  "valueUsd": 50000.00,
                  "carrierScac": "MAEU",
                  "vesselName": "MAERSK SEALAND",
                  "voyageNumber": "024W",
                  "portOfExportCode": "INBOM",
                  "countryOfDestination": "SG",
                  "estimatedEtd": "2026-10-15",
                  "licenseRequired": %s
                }
                """.formatted(description, licenseRequired);
    }

    /** A booking confirmed by a carrier, which is what a filing is raised against. */
    private String confirmedBooking() throws Exception {
        String created = mockMvc.perform(post("/api/bookings")
                        .header("X-Actor", "ops.jane")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
                                  "shipperId": "aabbccdd-1122-3344-5566-778899aabbcc",
                                  "consigneeId": "bbccddee-2233-4455-6677-8899aabbccdd",
                                  "shippingMode": "OCEAN_LCL",
                                  "originPortCode": "INBOM",
                                  "destinationPortCode": "SGSIN",
                                  "incoterms": "FOB",
                                  "requestedEtd": "2026-10-15",
                                  "transportRequired": false,
                                  "cargoDetails": [
                                    {
                                      "description": "Machine parts",
                                      "hsCode": "8471.30.0100",
                                      "pieces": 10,
                                      "weightKg": 500.000,
                                      "valueUsd": 50000.00,
                                      "lengthCm": 100.00,
                                      "widthCm": 100.00,
                                      "heightCm": 100.00,
                                      "hazmat": false,
                                      "temperatureControlled": false,
                                      "oversized": false
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String bookingId = JsonPath.parse(created).read("$.id");
        mockMvc.perform(post("/api/bookings/" + bookingId + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bookingSourceType":"DIRECT_CARRIER",
                                 "carrierId":"1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d"}
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
