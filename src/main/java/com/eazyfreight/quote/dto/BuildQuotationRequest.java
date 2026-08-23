package com.eazyfreight.quote.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * @param rateValidUntil earliest expiry among the carrier rates these lines were
 *                       priced from; acceptance is refused once it passes
 */
public record BuildQuotationRequest(
        @NotEmpty(message = "lines is required") @Valid List<QuoteLineRequest> lines,
        @NotNull(message = "validityDays is required")
        @Min(value = 1, message = "validityDays must be at least 1") Integer validityDays,
        LocalDate rateValidUntil,
        boolean spotRate,
        String notes
) {
}
