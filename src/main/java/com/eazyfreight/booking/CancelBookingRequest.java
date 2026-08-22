package com.eazyfreight.booking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CancelBookingRequest(
        @NotBlank(message = "reason is required") String reason,
        @NotNull(message = "initiatedBy is required") CancellationInitiator initiatedBy
) {
}
