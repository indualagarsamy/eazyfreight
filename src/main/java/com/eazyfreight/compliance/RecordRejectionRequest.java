package com.eazyfreight.compliance;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record RecordRejectionRequest(
        @NotBlank(message = "rejectionCode is required") String rejectionCode,
        @NotBlank(message = "rejectionDescription is required") String rejectionDescription,
        Instant rejectedAt
) {
}
