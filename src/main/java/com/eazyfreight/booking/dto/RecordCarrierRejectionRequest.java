package com.eazyfreight.booking.dto;

import jakarta.validation.constraints.NotBlank;

public record RecordCarrierRejectionRequest(
        @NotBlank(message = "reason is required") String reason
) {
}
