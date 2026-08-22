package com.eazyfreight.booking;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record BookingCargoDetailRequest(
        @NotBlank(message = "description is required") String description,
        @NotBlank(message = "hsCode is required")
        @Pattern(regexp = "\\d{4}\\.\\d{2}\\.\\d{4}", message = "hsCode must match format NNNN.NN.NNNN") String hsCode,
        @Min(value = 1, message = "pieces must be greater than 0") int pieces,
        @NotNull(message = "weightKg is required")
        @DecimalMin(value = "0.001", message = "weightKg must be greater than 0") BigDecimal weightKg,
        @NotNull(message = "lengthCm is required")
        @DecimalMin(value = "0.01", message = "lengthCm must be greater than 0") BigDecimal lengthCm,
        @NotNull(message = "widthCm is required")
        @DecimalMin(value = "0.01", message = "widthCm must be greater than 0") BigDecimal widthCm,
        @NotNull(message = "heightCm is required")
        @DecimalMin(value = "0.01", message = "heightCm must be greater than 0") BigDecimal heightCm,
        @NotNull(message = "valueUsd is required")
        @DecimalMin(value = "0.00", message = "valueUsd cannot be negative") BigDecimal valueUsd,
        boolean hazmat,
        boolean temperatureControlled,
        boolean oversized,
        String marksAndNumbers
) {
}
