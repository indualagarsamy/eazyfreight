package com.eazyfreight.booking;

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
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void bookingRunsFromRequestThroughConfirmationToTruckDispatch() throws Exception {
        String createResponse = mockMvc.perform(post("/api/bookings")
                        .header("X-Actor", "ops.jane")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingPayload(true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("BOOKING_REQUESTED"))
                .andExpect(jsonPath("$.bookingReference").value(
                        org.hamcrest.Matchers.matchesPattern("EF-2024-\\d{5}")))
                .andExpect(jsonPath("$.statusHistory.length()").value(1))
                .andExpect(jsonPath("$.statusHistory[0].changedBy").value("ops.jane"))
                .andReturn().getResponse().getContentAsString();

        String bookingId = JsonPath.parse(createResponse).read("$.id");

        mockMvc.perform(post("/api/bookings/" + bookingId + "/submit")
                        .header("X-Actor", "ops.jane")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bookingSourceType": "DIRECT_CARRIER",
                                  "carrierId": "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED_TO_CARRIER"));

        mockMvc.perform(post("/api/bookings/" + bookingId + "/carrier-confirmation")
                        .header("X-Actor", "ops.jane")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "carrierBookingRef": "MAEU987654",
                                  "vesselName": "MAERSK SEALAND",
                                  "voyageNumber": "024W",
                                  "confirmedEtd": "2024-04-16",
                                  "confirmedEta": "2024-05-10"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED_BY_CARRIER"))
                .andExpect(jsonPath("$.carrierBooking.carrierBookingRef").value("MAEU987654"))
                // Requested 2024-04-15, confirmed 2024-04-16 — one business day, within tolerance.
                .andExpect(jsonPath("$.requiresCustomerEtdNotification").value(false));

        mockMvc.perform(post("/api/bookings/" + bookingId + "/send-confirmation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CUSTOMER_CONFIRMED"))
                .andExpect(jsonPath("$.confirmationSentAt").exists());

        mockMvc.perform(get("/api/bookings/" + bookingId + "/status-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].toStatus").value("BOOKING_REQUESTED"))
                .andExpect(jsonPath("$[1].toStatus").value("SUBMITTED_TO_CARRIER"))
                .andExpect(jsonPath("$[2].toStatus").value("CONFIRMED_BY_CARRIER"))
                .andExpect(jsonPath("$[2].fromStatus").value("SUBMITTED_TO_CARRIER"))
                .andExpect(jsonPath("$[3].toStatus").value("CUSTOMER_CONFIRMED"))
                .andExpect(jsonPath("$[3].sequenceNumber").value(4));
    }

    @Test
    void overbookingAndReinstatementPreserveTheBookingIdentity() throws Exception {
        String bookingId = confirmedBooking("2024-04-16");

        mockMvc.perform(post("/api/bookings/" + bookingId + "/vessel-overbooking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VESSEL_OVERBOOKED"));

        mockMvc.perform(post("/api/bookings/" + bookingId + "/reinstate")
                        .header("X-Actor", "ops.jane")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "newVesselName": "MAERSK KOWLOON",
                                  "newVoyageNumber": "026W",
                                  "newEtd": "2024-04-30",
                                  "newEta": "2024-05-24",
                                  "reason": "VesselOverbooked"
                                }
                                """))
                .andExpect(status().isOk())
                // Same row, same reference — this is not a new booking.
                .andExpect(jsonPath("$.id").value(bookingId))
                .andExpect(jsonPath("$.status").value("CONFIRMED_BY_CARRIER"))
                .andExpect(jsonPath("$.carrierBooking.vesselName").value("MAERSK KOWLOON"))
                .andExpect(jsonPath("$.carrierBooking.confirmedEtd").value("2024-04-30"))
                // The sailing it left is still on the record.
                .andExpect(jsonPath("$.reinstatements.length()").value(1))
                .andExpect(jsonPath("$.reinstatements[0].previousVessel").value("MAERSK SEALAND"))
                .andExpect(jsonPath("$.reinstatements[0].previousEtd").value("2024-04-16"))
                .andExpect(jsonPath("$.reinstatements[0].reinstatedBy").value("ops.jane"));
    }

    @Test
    void aMaterialEtdChangeBlocksTheCustomerConfirmationUntilAcknowledged() throws Exception {
        // Requested 2024-04-15, confirmed 2024-05-20 — well beyond two business days.
        String bookingId = confirmedBooking("2024-05-20");

        mockMvc.perform(get("/api/bookings/" + bookingId))
                .andExpect(jsonPath("$.requiresCustomerEtdNotification").value(true));

        mockMvc.perform(post("/api/bookings/" + bookingId + "/send-confirmation"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("notify the customer")));

        mockMvc.perform(post("/api/bookings/" + bookingId + "/acknowledge-etd-variance"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/bookings/" + bookingId + "/send-confirmation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CUSTOMER_CONFIRMED"));
    }

    @Test
    void cargoOverTheContainerPayloadLimitIsRejectedAtSubmission() throws Exception {
        String createResponse = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingPayload(false)
                                .replace("\"shippingMode\": \"OCEAN_LCL\"", "\"shippingMode\": \"OCEAN_FCL\"")
                                .replace("\"weightKg\": 500.000", "\"weightKg\": 30000.000")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String bookingId = JsonPath.parse(createResponse).read("$.id");

        mockMvc.perform(post("/api/bookings/" + bookingId + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bookingSourceType": "DIRECT_CARRIER",
                                  "carrierId": "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
                                  "containerType": "TWENTY_GP",
                                  "numberOfContainers": 1
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("20GP payload limit")));
    }

    @Test
    void cancellationRecordsWhoCausedIt() throws Exception {
        String bookingId = confirmedBooking("2024-04-16");

        mockMvc.perform(post("/api/bookings/" + bookingId + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "Customer cancelled the order",
                                  "initiatedBy": "CUSTOMER"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationInitiatedBy").value("CUSTOMER"))
                .andExpect(jsonPath("$.cancelledAt").exists());

        // A customer cancellation is not an overbooking, so it cannot be rolled forward.
        mockMvc.perform(post("/api/bookings/" + bookingId + "/reinstate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "newVesselName": "MAERSK KOWLOON",
                                  "newVoyageNumber": "026W",
                                  "newEtd": "2024-04-30",
                                  "newEta": "2024-05-24"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("overbooked by the carrier")));
    }

    @Test
    void unknownBookingReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/bookings/3fa85f64-5717-4562-b3fc-2c963f66afa6"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ----------------------------------------------------------------- fixtures

    private String confirmedBooking(String confirmedEtd) throws Exception {
        String createResponse = mockMvc.perform(post("/api/bookings")
                        .header("X-Actor", "ops.jane")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingPayload(false)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String bookingId = JsonPath.parse(createResponse).read("$.id");

        mockMvc.perform(post("/api/bookings/" + bookingId + "/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bookingSourceType": "DIRECT_CARRIER",
                                  "carrierId": "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/bookings/" + bookingId + "/carrier-confirmation")
                        .header("X-Actor", "ops.jane")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "carrierBookingRef": "MAEU987654",
                                  "vesselName": "MAERSK SEALAND",
                                  "voyageNumber": "024W",
                                  "confirmedEtd": "%s",
                                  "confirmedEta": "2024-05-24"
                                }
                                """.formatted(confirmedEtd)))
                .andExpect(status().isOk());

        return bookingId;
    }

    private String bookingPayload(boolean transportRequired) {
        return """
                {
                  "customerId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
                  "shipperId": "aabbccdd-1122-3344-5566-778899aabbcc",
                  "consigneeId": "bbccddee-2233-4455-6677-8899aabbccdd",
                  "shippingMode": "OCEAN_LCL",
                  "originPortCode": "INBOM",
                  "destinationPortCode": "SGSIN",
                  "incoterms": "FOB",
                  "requestedEtd": "2024-04-15",
                  "requestedEta": "2024-05-10",
                  "transportRequired": %s,
                  %s
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
                """.formatted(
                transportRequired,
                transportRequired ? "\"pickupAddress\": \"Plot 14, MIDC Andheri, Mumbai\"," : "");
    }
}
