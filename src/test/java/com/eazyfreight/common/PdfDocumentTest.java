package com.eazyfreight.common;

import com.eazyfreight.support.FixedClockConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The two documents that leave the building.
 *
 * <p>Asserting on extracted text rather than on byte length is the point: a renderer
 * that throws produces no file and is obvious, but one that silently drops the seal
 * number produces a perfectly valid PDF that is wrong in a way nobody notices until a
 * container is standing at a terminal.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockConfiguration.class)
class PdfDocumentTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void theHouseBolRendersEverythingTheConsigneeNeedsToClaimTheCargo() throws Exception {
        String bookingId = readyForDocumentation();
        String houseBolId = issuedHouseBol(bookingId, "ORIGINAL_BOL");

        MvcResult result = mockMvc.perform(post(
                        "/api/documentation/house-bols/" + houseBolId + "/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("HBL-")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("-rev0.pdf")))
                .andReturn();

        byte[] pdf = result.getResponse().getContentAsByteArray();
        assertThat(pdf).startsWith('%', 'P', 'D', 'F');

        String text = textOf(pdf);
        assertThat(text)
                .contains("House Bill of Lading")
                .contains("MSCU1234567")     // container
                .contains("SEAL123456")      // seal
                .contains("MAERSK SEALAND")  // vessel
                .contains("024W")            // voyage
                .contains("INBOM")
                .contains("SGSIN")
                .contains("Machine parts");

        // Three negotiable originals were issued, so the page has to say what has to
        // happen to them.
        assertThat(text)
                .contains("Original Bol")
                .contains("surrendered at destination");
    }

    @Test
    void everyPageSaysWhatTheDocumentIsNot() throws Exception {
        String bookingId = readyForDocumentation();
        String houseBolId = issuedHouseBol(bookingId, "ORIGINAL_BOL");

        String text = textOf(pdfOf(post(
                "/api/documentation/house-bols/" + houseBolId + "/pdf")));

        // A House BOL is a document of title. This one is generated from invented data
        // by a workshop system, and must not be capable of passing for the instrument.
        assertThat(text)
                .contains("SPECIMEN - NOT NEGOTIABLE")
                .contains("not a document of title");
    }

    @Test
    void generatingRecordsTheReferenceAndTheDocumentCanBeFetchedAgain() throws Exception {
        String bookingId = readyForDocumentation();
        String houseBolId = issuedHouseBol(bookingId, "TELEX_RELEASE");

        mockMvc.perform(get("/api/documentation/house-bols/" + houseBolId))
                .andExpect(jsonPath("$.pdfReference").doesNotExist());

        byte[] generated = pdfOf(post("/api/documentation/house-bols/" + houseBolId + "/pdf"));

        mockMvc.perform(get("/api/documentation/house-bols/" + houseBolId))
                .andExpect(jsonPath("$.pdfReference",
                        org.hamcrest.Matchers.endsWith("-rev0.pdf")));

        // Re-downloading renders again from the same immutable revision, so the caller
        // is not holding a reference to something the server threw away.
        byte[] fetched = pdfOf(get("/api/documentation/house-bols/" + houseBolId + "/pdf"));
        assertThat(textOf(fetched)).isEqualTo(textOf(generated));
    }

    @Test
    void aTelexReleaseDoesNotTalkAboutOriginals() throws Exception {
        String bookingId = readyForDocumentation();
        String houseBolId = issuedHouseBol(bookingId, "TELEX_RELEASE");

        String text = textOf(pdfOf(post(
                "/api/documentation/house-bols/" + houseBolId + "/pdf")));

        assertThat(text).contains("surrendered their claim electronically");
        // No originals exist, so nothing should invite anyone to look for them.
        assertThat(text).doesNotContain("ORIGINALS ISSUED");
    }

    @Test
    void theInvoiceShowsTheCustomerWhatTheyOweAndNothingElse() throws Exception {
        String bookingId = readyForDocumentation();
        issuedHouseBol(bookingId, "TELEX_RELEASE");
        String invoiceId = issuedInvoice(bookingId);

        MvcResult result = mockMvc.perform(get("/api/finance/invoices/" + invoiceId + "/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("INV-")))
                .andReturn();

        String text = textOf(result.getResponse().getContentAsByteArray());
        assertThat(text)
                .contains("Freight Invoice")
                .contains("Ocean Freight")
                .contains("USD 2,400.00")   // the sell rate
                .contains("Total due");

        // The buy rate is what we pay the carrier. Putting it in front of the customer
        // hands them the negotiating position, and the invoice aggregate carries it on
        // every line, so this is one omission away from happening.
        assertThat(text).doesNotContain("1,850.00");
    }

    @Test
    void aPreparedInvoiceSaysItIsADraft() throws Exception {
        String bookingId = readyForDocumentation();
        String invoiceId = invoiceFor(bookingId);

        String text = textOf(pdfOf(get("/api/finance/invoices/" + invoiceId + "/pdf")));

        assertThat(text).contains("DRAFT - not yet issued to the customer.");
    }

    @Test
    void charactersTheStandardFontsCannotEncodeDoNotBreakTheRender() throws Exception {
        String bookingId = readyForDocumentation();
        String instructionsId = JsonPath.parse(mockMvc.perform(
                        post("/api/documentation/bookings/" + bookingId + "/instructions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"shipperName":"O\\u2019Brien \\u2014 Sh\\u012bpping \\u4e2d\\u6587",
                                         "consigneeName":"Sing Pte","freightTerms":"PREPAID"}
                                        """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).read("$.id");
        mockMvc.perform(post("/api/documentation/instructions/" + instructionsId + "/approve"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/documentation/instructions/" + instructionsId + "/send"))
                .andExpect(status().isOk());
        String masterId = masterBolReceived(instructionsId);
        mockMvc.perform(post("/api/documentation/master-bols/" + masterId + "/verify"))
                .andExpect(status().isOk());
        String houseBolId = JsonPath.parse(mockMvc.perform(
                        post("/api/documentation/master-bols/" + masterId + "/house-bol")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"releaseType\":\"TELEX_RELEASE\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).read("$.id");

        // A curly apostrophe pasted out of an email must not take the document with it.
        String text = textOf(pdfOf(post(
                "/api/documentation/house-bols/" + houseBolId + "/pdf")));
        assertThat(text).contains("O'Brien - Sh?pping");
    }

    // ----------------------------------------------------------------- fixtures

    private byte[] pdfOf(org.springframework.test.web.servlet.RequestBuilder request)
            throws Exception {
        return mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    private static String textOf(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String invoiceFor(String bookingId) throws Exception {
        return JsonPath.parse(mockMvc.perform(
                        get("/api/finance/bookings/" + bookingId + "/invoices"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).read("$[0].id");
    }

    private String issuedInvoice(String bookingId) throws Exception {
        String invoiceId = invoiceFor(bookingId);
        mockMvc.perform(post("/api/finance/invoices/" + invoiceId + "/actuals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lines":[
                                  {"description":"Ocean Freight INBOM-SGSIN","buyAmount":1850.00,
                                   "sellAmount":2400.00,"quantity":1,"unit":"TEU"},
                                  {"description":"Origin Terminal Handling","buyAmount":320.00,
                                   "sellAmount":420.00,"quantity":1,"unit":"TEU"}]}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/finance/invoices/" + invoiceId + "/issue"))
                .andExpect(status().isOk());
        return invoiceId;
    }

    private String masterBolReceived(String instructionsId) throws Exception {
        return JsonPath.parse(mockMvc.perform(
                        post("/api/documentation/instructions/" + instructionsId + "/master-bol")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"masterBolNumber\":\"MAEU-MBL-778120\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).read("$.id");
    }

    private String issuedHouseBol(String bookingId, String releaseType) throws Exception {
        String instructionsId = JsonPath.parse(mockMvc.perform(
                        post("/api/documentation/bookings/" + bookingId + "/instructions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"shipperName":"Acme Manufacturing",
                                         "shipperAddress":"Plot 14, MIDC Andheri, Mumbai",
                                         "consigneeName":"Singapore Trading Pte",
                                         "consigneeAddress":"12 Keppel Road, Singapore",
                                         "freightTerms":"PREPAID"}
                                        """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).read("$.id");
        mockMvc.perform(post("/api/documentation/instructions/" + instructionsId + "/approve"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/documentation/instructions/" + instructionsId + "/send"))
                .andExpect(status().isOk());
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
                .andExpect(status().isOk());
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
                                 "voyageNumber":"024W","confirmedEtd":"2026-10-16",
                                 "confirmedEta":"2026-11-09"}
                                """))
                .andExpect(status().isOk());
        return bookingId;
    }
}
