package com.eazyfreight.finance;

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
class FinanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void confirmingABookingPreparesAnInvoiceAutomatically() throws Exception {
        String bookingId = confirmedBooking();

        // Nothing here calls Finance — the booking's own event does it.
        mockMvc.perform(get("/api/finance/bookings/" + bookingId + "/invoices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PREPARED"))
                .andExpect(jsonPath("$[0].invoiceType").value("FREIGHT"));
    }

    @Test
    void theInvoiceCannotBeIssuedUntilTheHouseBolExists() throws Exception {
        String bookingId = confirmedBooking();
        String invoiceId = invoiceFor(bookingId);

        mockMvc.perform(post("/api/finance/invoices/" + invoiceId + "/actuals")
                        .contentType(MediaType.APPLICATION_JSON).content(linesPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.margin").value(270.00))
                .andExpect(jsonPath("$.issueBlockedReason")
                        .value(containsString("House BOL has not been issued")));

        mockMvc.perform(post("/api/finance/invoices/" + invoiceId + "/issue"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("House BOL")));

        // Issuing the House BOL links it through an event, unblocking the invoice.
        issueHouseBol(bookingId);

        mockMvc.perform(get("/api/finance/invoices/" + invoiceId))
                .andExpect(jsonPath("$.houseBolId").exists())
                .andExpect(jsonPath("$.issueBlockedReason").doesNotExist());

        mockMvc.perform(post("/api/finance/invoices/" + invoiceId + "/issue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.paymentDueDate").exists());
    }

    @Test
    void recordingACustomerPaymentCreatesTheCarrierPayableItFunds() throws Exception {
        String bookingId = confirmedBooking();
        String invoiceId = issuedInvoice(bookingId);

        mockMvc.perform(get("/api/finance/bookings/" + bookingId + "/payables"))
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(post("/api/finance/invoices/" + invoiceId + "/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":1220.00,"paymentDate":"2024-03-18",
                                 "paymentMethod":"WIRE","reference":"W-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.outstandingAmount").value(0.00));

        // The payable exists because the payment does, and is due T+2 business days.
        mockMvc.perform(get("/api/finance/bookings/" + bookingId + "/payables"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].customerPaymentDate").value("2024-03-18"))
                .andExpect(jsonPath("$[0].dueDate").value("2024-03-20"))
                // We remit the buy side, not what we charged the customer.
                .andExpect(jsonPath("$[0].amount").value(950.00));
    }

    @Test
    void theCarrierIsNotPaidUntilTheirInvoiceIsMatchedAndApproved() throws Exception {
        String bookingId = confirmedBooking();
        String invoiceId = issuedInvoice(bookingId);
        mockMvc.perform(post("/api/finance/invoices/" + invoiceId + "/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":1220.00,"paymentDate":"2024-03-18","paymentMethod":"WIRE"}
                                """))
                .andExpect(status().isOk());

        String payableId = JsonPath.parse(
                mockMvc.perform(get("/api/finance/bookings/" + bookingId + "/payables"))
                        .andReturn().getResponse().getContentAsString()).read("$[0].id");

        mockMvc.perform(post("/api/finance/payables/" + payableId + "/approve"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("received and matched")));

        mockMvc.perform(post("/api/finance/payables/" + payableId + "/carrier-invoice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reference\":\"MAEU-INV-5567\",\"amount\":995.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_INVOICE_MATCH"))
                // The carrier billed 45 more than we set aside.
                .andExpect(jsonPath("$.invoiceVariance").value(45.00));

        mockMvc.perform(post("/api/finance/payables/" + payableId + "/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(post("/api/finance/payables/" + payableId + "/paid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paidOn\":\"2024-03-20\",\"reference\":\"WIRE-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.overdue").value(false));
    }

    @Test
    void aStorageFeeIsOnlyPassedThroughWhenTheCustomerCausedIt() throws Exception {
        String bookingId = confirmedBooking();

        String fee = mockMvc.perform(post("/api/finance/bookings/" + bookingId + "/storage-fee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cause":"EARLY_PORT_DELIVERY","dailyRate":150.00,"days":3,
                                 "periodFrom":"2024-03-15","periodTo":"2024-03-18"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(450.00))
                .andExpect(jsonPath("$.responsibility").value("UNDETERMINED"))
                .andExpect(jsonPath("$.invoiceBlockedReason")
                        .value(containsString("Assign responsibility")))
                .andReturn().getResponse().getContentAsString();

        String feeId = JsonPath.parse(fee).read("$.id");

        mockMvc.perform(post("/api/finance/storage-fees/" + feeId + "/responsibility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responsibility\":\"EAZY_FREIGHT\",\"notes\":\"Our docs were late\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceBlockedReason")
                        .value(containsString("absorbed by Eazy Freight")));

        mockMvc.perform(post("/api/finance/storage-fees/" + feeId + "/invoice"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/finance/storage-fees/" + feeId + "/responsibility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responsibility\":\"CUSTOMER\",\"notes\":\"Customer asked for early delivery\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/finance/storage-fees/" + feeId + "/invoice"))
                .andExpect(status().isCreated())
                // A separate document from the freight invoice.
                .andExpect(jsonPath("$.invoiceType").value("STORAGE_FEE"))
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.totalAmount").value(450.00));
    }

    @Test
    void cancellingABookingVoidsItsUnpaidInvoice() throws Exception {
        String bookingId = confirmedBooking();
        String invoiceId = invoiceFor(bookingId);

        mockMvc.perform(post("/api/bookings/" + bookingId + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Customer cancelled\",\"initiatedBy\":\"CUSTOMER\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/finance/invoices/" + invoiceId))
                .andExpect(jsonPath("$.status").value("VOIDED"))
                .andExpect(jsonPath("$.voidReason").value(containsString("Booking cancelled")));
    }

    @Test
    void aCustomerCanBePutOnCreditHoldAndTakenOff() throws Exception {
        String bookingId = confirmedBooking();
        String customerId = "7c9e6679-7425-40de-944b-e07fc1f90ae7";

        mockMvc.perform(post("/api/finance/bookings/" + bookingId + "/credit-hold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"" + customerId
                                + "\",\"reason\":\"Three invoices overdue\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(post("/api/finance/bookings/" + bookingId + "/credit-hold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"" + customerId + "\",\"reason\":\"Again\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("already on credit hold")));

        mockMvc.perform(post("/api/finance/credit-holds/" + customerId + "/lift"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    // ----------------------------------------------------------------- fixtures

    private String linesPayload() {
        return """
                {"lines":[
                  {"description":"Ocean Freight","buyAmount":850.00,"sellAmount":1100.00,
                   "quantity":1,"unit":"CBM"},
                  {"description":"THC Origin","buyAmount":100.00,"sellAmount":120.00,
                   "quantity":1,"unit":"FLAT"}]}
                """;
    }

    private String invoiceFor(String bookingId) throws Exception {
        return JsonPath.parse(
                mockMvc.perform(get("/api/finance/bookings/" + bookingId + "/invoices"))
                        .andReturn().getResponse().getContentAsString()).read("$[0].id");
    }

    private String issuedInvoice(String bookingId) throws Exception {
        String invoiceId = invoiceFor(bookingId);
        mockMvc.perform(post("/api/finance/invoices/" + invoiceId + "/actuals")
                        .contentType(MediaType.APPLICATION_JSON).content(linesPayload()))
                .andExpect(status().isOk());
        issueHouseBol(bookingId);
        mockMvc.perform(post("/api/finance/invoices/" + invoiceId + "/issue"))
                .andExpect(status().isOk());
        return invoiceId;
    }

    /** Drives Logistics, Compliance and Documentation far enough to get a House BOL. */
    private void issueHouseBol(String bookingId) throws Exception {
        for (String[] step : new String[][]{
                {"outbound-dispatch", "{\"driverId\":\"9f8e7d6c-5b4a-3c2d-1e0f-9a8b7c6d5e4f\","
                        + "\"pickupAddress\":\"Yard\",\"deliveryAddress\":\"Plot 14\"}"},
                {"container-number", "{\"containerNumber\":\"MSCU1234567\",\"source\":\"CARRIER_YARD\"}"},
                {"delivered-to-customer", "{}"},
                {"loading-complete", "{}"},
                {"seal", "{\"sealNumber\":\"SEAL123456\"}"}}) {
            mockMvc.perform(post("/api/logistics/bookings/" + bookingId + "/" + step[0])
                            .contentType(MediaType.APPLICATION_JSON).content(step[1]))
                    .andExpect(status().is2xxSuccessful());
        }

        String filingId = JsonPath.parse(mockMvc.perform(
                        post("/api/compliance/bookings/" + bookingId + "/filings"))
                .andReturn().getResponse().getContentAsString()).read("$.id");
        mockMvc.perform(post("/api/compliance/filings/" + filingId + "/compile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shipperName":"Acme","shipperEin":"12-3456789",
                                 "consigneeName":"Sing","consigneeCountry":"SG",
                                 "scheduleBNumber":"8471.30.0100","commodityDescription":"Machine parts",
                                 "quantityValue":10,"quantityUnit":"PCS","valueUsd":50000.00,
                                 "carrierScac":"MAEU","portOfExportCode":"INBOM",
                                 "countryOfDestination":"SG","estimatedEtd":"2026-10-16",
                                 "licenseRequired":false}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/compliance/filings/" + filingId + "/submit"))
                .andExpect(status().isOk());

        String instructionsId = JsonPath.parse(mockMvc.perform(
                        post("/api/documentation/bookings/" + bookingId + "/instructions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"shipperName":"Acme Manufacturing",
                                         "consigneeName":"Singapore Trading Pte",
                                         "freightTerms":"PREPAID"}
                                        """))
                .andReturn().getResponse().getContentAsString()).read("$.id");
        mockMvc.perform(post("/api/documentation/instructions/" + instructionsId + "/approve"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/documentation/instructions/" + instructionsId + "/send"))
                .andExpect(status().isOk());
        String masterId = JsonPath.parse(mockMvc.perform(
                        post("/api/documentation/instructions/" + instructionsId + "/master-bol")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"masterBolNumber\":\"MAEU-MBL-778899\"}"))
                .andReturn().getResponse().getContentAsString()).read("$.id");
        mockMvc.perform(post("/api/documentation/master-bols/" + masterId + "/verify"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/documentation/master-bols/" + masterId + "/house-bol")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"releaseType\":\"TELEX_RELEASE\"}"))
                .andExpect(status().isCreated());
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
