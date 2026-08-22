package com.eazyfreight.quote;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record QuoteLineRequest(
        @NotNull(message = "lineType is required") QuoteLineType lineType,
        @NotBlank(message = "description is required") String description,
        @NotNull(message = "buyRate is required")
        @DecimalMin(value = "0.00", message = "buyRate cannot be negative") BigDecimal buyRate,
        @NotNull(message = "sellRate is required")
        @DecimalMin(value = "0.00", message = "sellRate cannot be negative") BigDecimal sellRate,
        @NotNull(message = "quantity is required")
        @DecimalMin(value = "0.0001", message = "quantity must be greater than 0") BigDecimal quantity,
        @NotNull(message = "unit is required") ChargeUnit unit
) {
}
