package com.eazyfreight.quote.controller;

import com.eazyfreight.support.FixedClockConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockConfiguration.class)
class QuoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void quoteRunsFromInquiryThroughScreeningPricingAndAcceptance() throws Exception {
        String createResponse = mockMvc.perform(post("/api/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quotePayload("Acme Manufacturing", "Singapore Trading Pte")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.screeningStatus").value("CLEARED"))
                .andExpect(jsonPath("$.quoteReference").value(
                        org.hamcrest.Matchers.matchesPattern("Q-2024-\\d{5}")))
                // Chargeable weight is derived, not typed in: 2 CBM volumetric beats
                // 0.5 CBM weight equivalent.
                .andExpect(jsonPath("$.cargoDetails[0].chargeableWeight").value(2))
                .andExpect(jsonPath("$.cargoDetails[0].chargeableUnit").value("CBM"))
                .andReturn().getResponse().getContentAsString();

        String quoteId = JsonPath.parse(createResponse).read("$.id");

        // Sending before the quote is priced is refused.
        mockMvc.perform(post("/api/quotes/" + quoteId + "/send"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("at least one quote line")));

        mockMvc.perform(post("/api/quotes/" + quoteId + "/build")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lines": [
                                    {
                                      "lineType": "BASE_FREIGHT",
                                      "description": "Ocean Freight",
                                      "buyRate": 850.00,
                                      "sellRate": 1100.00,
                                      "quantity": 1,
                                      "unit": "CBM"
                                    },
                                    {
                                      "lineType": "THC_ORIGIN",
                                      "description": "THC - Origin",
                                      "buyRate": 100.00,
                                      "sellRate": 120.00,
                                      "quantity": 1,
                                      "unit": "FLAT"
                                    }
                                  ],
                                  "validityDays": 30,
                                  "rateValidUntil": "2024-06-30",
                                  "spotRate": false,
                                  "notes": "Subject to space and equipment availability."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBuyRate").value(950.00))
                .andExpect(jsonPath("$.totalSellRate").value(1220.00))
                // Margin is a property of the record, not a month-end reconciliation.
                .andExpect(jsonPath("$.margin").value(270.00));

        mockMvc.perform(post("/api/quotes/" + quoteId + "/send"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.validUntil").value("2024-04-14"));

        mockMvc.perform(post("/api/quotes/" + quoteId + "/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selectedCarrierId\": \"1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.selectedCarrierId").value("1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d"));

        mockMvc.perform(get("/api/quotes/" + quoteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quoteLines.length()").value(2))
                .andExpect(jsonPath("$.acceptedAt").exists());
    }

    @Test
    void aFlaggedConsigneeHaltsTheQuoteAndBlocksSending() throws Exception {
        String createResponse = mockMvc.perform(post("/api/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        // The stub screening client flags any name containing "DENIED".
                        .content(quotePayload("Acme Manufacturing", "DENIED Entity Ltd")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.screeningStatus").value("FLAGGED"))
                .andReturn().getResponse().getContentAsString();

        String quoteId = JsonPath.parse(createResponse).read("$.id");

        mockMvc.perform(post("/api/quotes/" + quoteId + "/send"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("screening")));
    }

    @Test
    void anEtdInsideTheFiveBusinessDayFloorIsRejected() throws Exception {
        mockMvc.perform(post("/api/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quotePayload("Acme Manufacturing", "Singapore Trading Pte")
                                .replace("\"requestedEtd\": \"2024-04-15\"", "\"requestedEtd\": \"2024-03-18\"")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("5 business days")));
    }

    @Test
    void aMalformedHsCodeIsRejectedWithFieldLevelDetail() throws Exception {
        mockMvc.perform(post("/api/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quotePayload("Acme Manufacturing", "Singapore Trading Pte")
                                .replace("8471.30.0100", "847130")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors['cargoDetails[0].hsCode']").value(
                        "hsCode must match format NNNN.NN.NNNN"));
    }

    @Test
    void unknownQuoteReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/quotes/3fa85f64-5717-4562-b3fc-2c963f66afa6"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    private String quotePayload(String shipperName, String consigneeName) {
        return """
                {
                  "customerId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
                  "shippingMode": "OCEAN_LCL",
                  "originPortCode": "INBOM",
                  "destinationPortCode": "SGSIN",
                  "incoterms": "FOB",
                  "requestedEtd": "2024-04-15",
                  "insuranceRequired": false,
                  "currency": "USD",
                  "shipperName": "%s",
                  "shipperAddress": "Plot 14, MIDC Andheri",
                  "shipperCountry": "IN",
                  "consigneeName": "%s",
                  "consigneeAddress": "9 Raffles Place",
                  "consigneeCountry": "SG",
                  "cargoDetails": [
                    {
                      "description": "Machine parts",
                      "hsCode": "8471.30.0100",
                      "pieces": 2,
                      "weightKg": 500.000,
                      "lengthCm": 100.00,
                      "widthCm": 100.00,
                      "heightCm": 100.00,
                      "hazmat": false,
                      "temperatureControlled": false,
                      "oversized": false
                    }
                  ]
                }
                """.formatted(shipperName, consigneeName);
    }
}
