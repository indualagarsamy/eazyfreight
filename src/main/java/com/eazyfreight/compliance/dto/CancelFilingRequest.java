package com.eazyfreight.compliance.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelFilingRequest(
        @NotBlank(message = "reason is required") String reason
) {
}
