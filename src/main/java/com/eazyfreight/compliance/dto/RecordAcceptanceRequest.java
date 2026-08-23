package com.eazyfreight.compliance.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

/**
 * Manual entry of a CBP response, for the case where the filing was made in the ACE
 * portal and the ITN is being keyed in — which is exactly what happens today.
 */
public record RecordAcceptanceRequest(
        @NotBlank(message = "itnNumber is required") String itnNumber,
        String aesSubmissionReference,
        Instant acceptedAt
) {
}
