package com.eazyfreight.compliance.dto;

import jakarta.validation.constraints.NotBlank;

public record AmendFilingRequest(
        @NotBlank(message = "reason is required") String reason
) {
}
