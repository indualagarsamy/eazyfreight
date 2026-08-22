package com.eazyfreight.booking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ReinstateBookingRequest(
        @NotBlank(message = "newVesselName is required") String newVesselName,
        @NotBlank(message = "newVoyageNumber is required") String newVoyageNumber,
        @NotNull(message = "newETD is required") LocalDate newEtd,
        @NotNull(message = "newETA is required") LocalDate newEta,
        String reason
) {
}
