package com.eazyfreight.quote.exception;

import java.util.UUID;

public class QuoteNotFoundException extends RuntimeException {

    public QuoteNotFoundException(UUID id) {
        super("Quote not found with id: " + id);
    }

    public QuoteNotFoundException(String quoteReference) {
        super("Quote not found with reference: " + quoteReference);
    }
}
