package com.eazyfreight.quote;

/**
 * Quote lifecycle. Transitions are enforced by {@link Quote}; nothing outside the
 * aggregate root may set this field.
 */
public enum QuoteStatus {
    DRAFT,
    SENT,
    ACCEPTED,
    DECLINED,
    EXPIRED
}
