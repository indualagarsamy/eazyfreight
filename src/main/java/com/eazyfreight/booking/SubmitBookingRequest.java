package com.eazyfreight.booking;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SubmitBookingRequest(
        @NotNull(message = "bookingSourceType is required") BookingSourceType bookingSourceType,
        @NotNull(message = "carrierId is required") UUID carrierId,
        ContainerType containerType,
        @Min(value = 1, message = "numberOfContainers must be at least 1") Integer numberOfContainers
) {
}
