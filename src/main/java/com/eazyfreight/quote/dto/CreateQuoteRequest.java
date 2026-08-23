package com.eazyfreight.quote.dto;

import com.eazyfreight.quote.domain.ShippingMode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateQuoteRequest(
        @NotNull(message = "customerId is required") UUID customerId,
        @NotNull(message = "shippingMode is required") ShippingMode shippingMode,
        @NotBlank(message = "originPortCode is required") String originPortCode,
        @NotBlank(message = "destinationPortCode is required") String destinationPortCode,
        @NotBlank(message = "incoterms is required") String incoterms,
        @NotEmpty(message = "cargoDetails is required") @Valid List<CargoDetailRequest> cargoDetails,
        LocalDate requestedEtd,
        String specialHandling,
        boolean insuranceRequired,
        @NotBlank(message = "currency is required")
        @Pattern(regexp = "[A-Z]{3}", message = "currency must be an ISO 4217 code") String currency,
        @NotBlank(message = "shipperName is required") String shipperName,
        String shipperAddress,
        String shipperCountry,
        @NotBlank(message = "consigneeName is required") String consigneeName,
        String consigneeAddress,
        String consigneeCountry
) {
}
