package com.eazyfreight.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateBookingRequest(
        UUID quoteId,
        @NotNull(message = "customerId is required") UUID customerId,
        @NotNull(message = "shipperId is required") UUID shipperId,
        @NotNull(message = "consigneeId is required") UUID consigneeId,
        UUID notifyPartyId,
        UUID alsoNotifyId,
        @NotNull(message = "shippingMode is required") ShippingMode shippingMode,
        @NotBlank(message = "originPortCode is required") String originPortCode,
        @NotBlank(message = "destinationPortCode is required") String destinationPortCode,
        @NotBlank(message = "incoterms is required") String incoterms,
        @NotNull(message = "requestedETD is required") LocalDate requestedEtd,
        LocalDate requestedEta,
        boolean transportRequired,
        String pickupAddress,
        Instant pickupDateTime,
        String specialInstructions,
        String marksAndNumbers,
        @NotEmpty(message = "cargoDetails is required") @Valid List<BookingCargoDetailRequest> cargoDetails
) {
}
