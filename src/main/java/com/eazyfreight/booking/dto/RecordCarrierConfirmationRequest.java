package com.eazyfreight.booking.dto;

import com.eazyfreight.booking.domain.ContainerType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;

public record RecordCarrierConfirmationRequest(
        @NotBlank(message = "carrierBookingRef is required") String carrierBookingRef,
        String coLoaderBookingRef,
        @NotBlank(message = "vesselName is required") String vesselName,
        @NotBlank(message = "voyageNumber is required") String voyageNumber,
        @NotNull(message = "confirmedETD is required") LocalDate confirmedEtd,
        @NotNull(message = "confirmedETA is required") LocalDate confirmedEta,
        ContainerType containerType,
        Instant confirmedAt
) {
}
