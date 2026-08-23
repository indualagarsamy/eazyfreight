package com.eazyfreight.quote.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AcceptQuoteRequest(
        @NotNull(message = "selectedCarrierId is required") UUID selectedCarrierId
) {
}
