package com.eazyfreight.compliance;

import jakarta.validation.constraints.NotBlank;

public record CancelFilingRequest(
        @NotBlank(message = "reason is required") String reason
) {
}
