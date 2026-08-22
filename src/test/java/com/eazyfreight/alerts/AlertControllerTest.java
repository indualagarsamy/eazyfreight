package com.eazyfreight.alerts;

import com.eazyfreight.support.FixedClockConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Alerts track against the rest of the system.
 *
 * <p>The clock is pinned to 2024-03-15, so a booking whose carrier-confirmed ETD is
 * 2024-03-22 sits exactly seven days out and every threshold in the specification can be
 * hit by choosing the date rather than by waiting.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockConfiguration.class)
class AlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aQuietBookingSevenDaysFromSailingIsAlreadyBehind() throws Exception {
        String bookingId = confirmedBooking("2024-03-22");

        mockMvc.perform(post("/api/alerts/bookings/" + bookingId + "/evaluate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes", greaterThan(0)));

        // Nothing has been dispatched and nothing has been filed, and the ETD is inside
        // both ten-day thresholds.
        mockMvc.perform(get("/api/alerts/bookings/" + bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].alertCode", hasItem("L-001")))
                .andExpect(jsonPath("$[*].alertCode", hasItem("C-001")))
                // Not raised: C-002 is for a filing that was started and left unsent,
                // and this one was never started at all. C-001 already says that.
                .andExpect(jsonPath("$[*].alertCode", not(hasItem("C-002"))))
                // Not yet: the ITN thresholds start at five days.
                .andExpect(jsonPath("$[*].alertCode", not(hasItem("C-004"))))
                .andExpect(jsonPath("$[*].alertCode", not(hasItem("C-005"))));
    }

    @Test
    void everyAlertSaysWhatIsWrongWhoActsAndByWhen() throws Exception {
        String bookingId = confirmedBooking("2024-03-22");
        evaluate(bookingId);

        String alerts = mockMvc.perform(get("/api/alerts/bookings/" + bookingId))
                .andReturn().getResponse().getContentAsString();
        List<String> messages = JsonPath.parse(alerts).read("$[?(@.alertCode == 'L-001')].message");
        List<String> actions =
                JsonPath.parse(alerts).read("$[?(@.alertCode == 'L-001')].recommendedAction");
        List<Object> deadlines =
                JsonPath.parse(alerts).read("$[?(@.alertCode == 'L-001')].deadlineAt");

        assertThatContains(messages.getFirst(), "ETD is 2024-03-22");
        assertThatContains(messages.getFirst(), "7 days away");
        assertThatContains(actions.getFirst(), "outbound truck");
        assertThatNotNull(deadlines.getFirst());
    }

    @Test
    void theSameConditionIsNeverRaisedTwice() throws Exception {
        String bookingId = confirmedBooking("2024-03-22");

        evaluate(bookingId);
        int afterFirst = countAlerts(bookingId);
        evaluate(bookingId);
        evaluate(bookingId);

        // A sweep every thirty minutes on a container that has not moved must not
        // produce a new row every thirty minutes.
        assertThatEquals(countAlerts(bookingId), afterFirst);
    }

    @Test
    void theItnClosesEveryAlertThatWasWaitingOnIt() throws Exception {
        String bookingId = confirmedBooking("2024-03-18");
        evaluate(bookingId);

        mockMvc.perform(get("/api/alerts/bookings/" + bookingId))
                .andExpect(jsonPath("$[?(@.status != 'RESOLVED')].alertCode", hasItem("C-004")))
                .andExpect(jsonPath("$[?(@.status != 'RESOLVED')].alertCode", hasItem("C-005")))
                .andExpect(jsonPath("$[?(@.status != 'RESOLVED')].alertCode", hasItem("D-001")));

        acceptedFiling(bookingId);

        // Nothing here touched the alerts. Compliance published ItnGateCheckPassed and
        // the Alerts track closed what that answered.
        mockMvc.perform(get("/api/alerts/bookings/" + bookingId))
                .andExpect(jsonPath("$[?(@.status != 'RESOLVED')].alertCode", not(hasItem("C-004"))))
                .andExpect(jsonPath("$[?(@.status != 'RESOLVED')].alertCode", not(hasItem("C-005"))))
                .andExpect(jsonPath("$[?(@.alertCode == 'C-004')].resolutionReason",
                        hasItem("ITNNumberReceived")));
    }

    @Test
    void cancellingABookingClosesEverythingOutstandingOnIt() throws Exception {
        String bookingId = confirmedBooking("2024-03-22");
        evaluate(bookingId);
        assertThatTrue(countAlerts(bookingId) > 0);

        mockMvc.perform(post("/api/bookings/" + bookingId + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Customer withdrew the shipment",
                                 "initiatedBy":"CUSTOMER"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/alerts/bookings/" + bookingId))
                .andExpect(jsonPath("$[?(@.status != 'RESOLVED')]", empty()))
                .andExpect(jsonPath("$[0].resolutionReason").value("BookingCancelled"));

        // And a later sweep does not raise them again.
        evaluate(bookingId);
        mockMvc.perform(get("/api/alerts/bookings/" + bookingId))
                .andExpect(jsonPath("$[?(@.status != 'RESOLVED')]", empty()));
    }

    @Test
    void anAlertCanBeAcknowledgedButNotSnoozedPastItsCategoryCap() throws Exception {
        String bookingId = confirmedBooking("2024-03-17");
        evaluate(bookingId);
        String criticalId = firstAlertOfCode(bookingId, "C-005");

        // 2024-03-15T10:00Z + 6 hours is beyond the four-hour Critical ceiling.
        mockMvc.perform(post("/api/alerts/" + criticalId + "/snooze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"until\":\"2024-03-15T16:00:00Z\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("more than 4 hours")));

        mockMvc.perform(post("/api/alerts/" + criticalId + "/snooze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"until\":\"2024-03-15T13:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SNOOZED"));

        mockMvc.perform(post("/api/alerts/" + criticalId + "/acknowledge")
                        .header("X-Actor", "compliance.ann"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.acknowledgedBy").value("compliance.ann"))
                .andExpect(jsonPath("$.snoozedUntil").doesNotExist());
    }

    @Test
    void closingAnAlertByHandRequiresAReason() throws Exception {
        String bookingId = confirmedBooking("2024-03-22");
        evaluate(bookingId);
        String alertId = firstAlertOfCode(bookingId, "L-001");

        mockMvc.perform(post("/api/alerts/" + alertId + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/alerts/" + alertId + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Truck booked by phone with the vendor\"}")
                        .header("X-Actor", "ops.jane"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolutionReason").value("Truck booked by phone with the vendor"));
    }

    @Test
    void notificationsAreRecordedAndMarkedSimulated() throws Exception {
        String bookingId = confirmedBooking("2024-03-22");
        evaluate(bookingId);
        String alertId = firstAlertOfCode(bookingId, "L-001");

        mockMvc.perform(get("/api/alerts/" + alertId))
                .andExpect(jsonPath("$.notifications", not(empty())))
                // In-app is real: the alert is in the list. Email is not sent anywhere.
                .andExpect(jsonPath("$.notifications[?(@.channel == 'IN_APP')].simulated",
                        hasItem(false)))
                .andExpect(jsonPath("$.notifications[?(@.channel == 'EMAIL')].simulated",
                        hasItem(true)))
                .andExpect(jsonPath("$.notifications[?(@.channel == 'EMAIL')].deliveryStatus",
                        hasItem("DELIVERED")));
    }

    @Test
    void theDashboardCountsBySeverityAndTrack() throws Exception {
        confirmedBooking("2024-03-22");
        mockMvc.perform(post("/api/alerts/evaluate")).andExpect(status().isOk());

        mockMvc.perform(get("/api/alerts/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open", greaterThan(0)))
                // Both L-001 and C-001 open as Medium but the type upgrades them to
                // High once the ETD is inside seven days, which this one is.
                .andExpect(jsonPath("$.byCategory.HIGH", greaterThan(0)))
                .andExpect(jsonPath("$.byTrack.LOGISTICS", greaterThan(0)))
                .andExpect(jsonPath("$.byTrack.COMPLIANCE", greaterThan(0)))
                .andExpect(jsonPath("$.bookingsAffected", greaterThan(0)));
    }

    @Test
    void configurationCanTurnAnAlertOffWithoutTouchingTheOthers() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/alerts/configurations/L001_CONTAINER_NOT_DISPATCHED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled": false, "thresholdDays": 10, "escalationHours": 24,
                                 "channels": ["IN_APP"], "snoozeMaxHours": 24}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        String bookingId = confirmedBooking("2024-03-22");
        evaluate(bookingId);

        mockMvc.perform(get("/api/alerts/bookings/" + bookingId))
                .andExpect(jsonPath("$[*].alertCode", not(hasItem("L-001"))))
                .andExpect(jsonPath("$[*].alertCode", hasItem("C-001")));

        // Put it back so the shared context does not leak into the other tests.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/alerts/configurations/L001_CONTAINER_NOT_DISPATCHED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled": true, "thresholdDays": 10, "escalationHours": 24,
                                 "channels": ["IN_APP","EMAIL"], "snoozeMaxHours": 24}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void aSnoozeLongerThanTheCategoryAllowsIsRefusedInConfigurationToo() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/alerts/configurations/C005_ITN_MISSING_CUTOFF_BREACHED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled": true, "escalationHours": 4,
                                 "channels": ["IN_APP","EMAIL","SMS"], "snoozeMaxHours": 12}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("longer than 4 hours")));
    }

    @Test
    void aResolvedConditionThatReturnsRaisesAFreshAlert() throws Exception {
        String bookingId = confirmedBooking("2024-03-22");
        evaluate(bookingId);
        String alertId = firstAlertOfCode(bookingId, "L-001");

        mockMvc.perform(post("/api/alerts/" + alertId + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Closed in error\"}"))
                .andExpect(status().isOk());

        // The container still has not been dispatched, so the next sweep raises it again.
        evaluate(bookingId);
        mockMvc.perform(get("/api/alerts/bookings/" + bookingId))
                .andExpect(jsonPath("$[?(@.alertCode == 'L-001')]", hasSize(2)))
                .andExpect(jsonPath("$[?(@.alertCode == 'L-001' && @.status != 'RESOLVED')]",
                        hasSize(1)));
    }

    @Test
    void theItnIsReadFromComplianceNotFromAContainerThatMayNotExistYet() throws Exception {
        String bookingId = confirmedBooking("2024-03-18");

        // Compliance files before anyone books a truck, so there is no container
        // assignment for the ITN to be mirrored onto. Reading the mirror would report
        // "no ITN" on a booking that plainly has one.
        acceptedFiling(bookingId);
        evaluate(bookingId);

        mockMvc.perform(get("/api/alerts/bookings/" + bookingId))
                .andExpect(jsonPath("$[?(@.status != 'RESOLVED')].alertCode", not(hasItem("C-004"))))
                .andExpect(jsonPath("$[?(@.status != 'RESOLVED')].alertCode", not(hasItem("C-005"))));
    }

    @Test
    void anEventThatClearsOneOfThreePreconditionsDoesNotCloseTheAlert() throws Exception {
        String bookingId = confirmedBooking("2024-03-18");
        evaluate(bookingId);
        mockMvc.perform(get("/api/alerts/bookings/" + bookingId))
                .andExpect(jsonPath("$[?(@.status != 'RESOLVED')].alertCode", hasItem("D-001")));

        // D-001 wants a container number, a seal and an ITN. The ITN arriving answers
        // one of the three.
        acceptedFiling(bookingId);
        evaluate(bookingId);
        evaluate(bookingId);

        // Still open, and still exactly one of it: an alert that closes on an event and
        // is re-raised by the next sweep flickers, and a flickering list is one nobody
        // reads.
        mockMvc.perform(get("/api/alerts/bookings/" + bookingId))
                .andExpect(jsonPath("$[?(@.alertCode == 'D-001')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.alertCode == 'D-001')].status", hasItem("ACTIVE")));
    }

    // ----------------------------------------------------------------- fixtures

    private void evaluate(String bookingId) throws Exception {
        mockMvc.perform(post("/api/alerts/bookings/" + bookingId + "/evaluate"))
                .andExpect(status().isOk());
    }

    private int countAlerts(String bookingId) throws Exception {
        String body = mockMvc.perform(get("/api/alerts/bookings/" + bookingId))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.parse(body).read("$.length()");
    }

    private String firstAlertOfCode(String bookingId, String code) throws Exception {
        String body = mockMvc.perform(get("/api/alerts/bookings/" + bookingId))
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.parse(body)
                .read("$[?(@.alertCode == '%s' && @.status != 'RESOLVED')].id".formatted(code));
        if (ids.isEmpty()) {
            throw new AssertionError("No open " + code + " alert on booking " + bookingId
                    + ". Alerts were: " + body);
        }
        return ids.getFirst();
    }

    /**
     * A booking the carrier has confirmed onto a sailing {@code confirmedEtd}.
     *
     * <p>The requested ETD is always 2024-03-25 because the booking rules refuse
     * anything less than five business days out, and these tests need vessels leaving
     * sooner than that. The carrier moving the date is ordinary — it is what
     * {@code requiresCustomerEtdNotification} exists for.
     */
    private String confirmedBooking(String confirmedEtd) throws Exception {
        String bookingId = JsonPath.parse(mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
                                  "shipperId": "aabbccdd-1122-3344-5566-778899aabbcc",
                                  "consigneeId": "bbccddee-2233-4455-6677-8899aabbcc00",
                                  "shippingMode": "OCEAN_FCL",
                                  "originPortCode": "USLAX", "destinationPortCode": "SGSIN",
                                  "incoterms": "FOB", "requestedEtd": "2024-03-25",
                                  "transportRequired": true,
                                  "pickupAddress": "1200 Foundry Rd, Vernon CA",
                                  "cargoDetails": [{
                                    "description": "Machine parts", "hsCode": "8471.30.0100",
                                    "pieces": 10, "weightKg": 500.000, "valueUsd": 50000.00,
                                    "lengthCm": 100.00, "widthCm": 100.00, "heightCm": 100.00
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
                                 "voyageNumber":"024W","confirmedEtd":"%s",
                                 "confirmedEta":"2024-04-12"}
                                """.formatted(confirmedEtd)))
                .andExpect(status().isOk());
        return bookingId;
    }

    private void acceptedFiling(String bookingId) throws Exception {
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
                                  "carrierScac": "MAEU", "portOfExportCode": "USLAX",
                                  "countryOfDestination": "SG", "estimatedEtd": "2024-03-18",
                                  "licenseRequired": false
                                }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/compliance/filings/" + filingId + "/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    private static void assertThatContains(String actual, String expected) {
        if (actual == null || !actual.contains(expected)) {
            throw new AssertionError("Expected \"" + actual + "\" to contain \"" + expected + "\"");
        }
    }

    private static void assertThatNotNull(Object value) {
        if (value == null) {
            throw new AssertionError("Expected a value, got null");
        }
    }

    private static void assertThatEquals(int actual, int expected) {
        if (actual != expected) {
            throw new AssertionError("Expected " + expected + " but was " + actual);
        }
    }

    private static void assertThatTrue(boolean value) {
        if (!value) {
            throw new AssertionError("Expected true");
        }
    }
}
