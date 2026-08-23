package com.eazyfreight.booking.dto;

import com.eazyfreight.booking.domain.BookingSourceType;
import com.eazyfreight.booking.domain.ContainerType;

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
