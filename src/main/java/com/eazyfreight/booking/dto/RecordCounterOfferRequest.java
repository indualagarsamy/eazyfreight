package com.eazyfreight.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record RecordCounterOfferRequest(
        @NotBlank(message = "proposedVessel is required") String proposedVessel,
        @NotBlank(message = "proposedVoyage is required") String proposedVoyage,
        @NotNull(message = "proposedETD is required") LocalDate proposedEtd,
        @NotNull(message = "proposedETA is required") LocalDate proposedEta
) {
}
