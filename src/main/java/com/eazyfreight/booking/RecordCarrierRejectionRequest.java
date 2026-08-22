package com.eazyfreight.booking;

import jakarta.validation.constraints.NotBlank;

public record RecordCarrierRejectionRequest(
        @NotBlank(message = "reason is required") String reason
) {
}
