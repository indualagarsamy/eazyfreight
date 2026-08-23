package com.eazyfreight.quote.event;

import com.eazyfreight.quote.domain.EntityType;
import com.eazyfreight.quote.domain.Quote;
import com.eazyfreight.quote.domain.ShippingMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Domain events raised by the Quote aggregate, per the Quote Intake specification.
 *
 * <p>Events are registered on the root and published by Spring Data when the
 * aggregate is saved, so an event can never escape without its state change having
 * been committed.
 */
public sealed interface QuoteEvent {

    UUID quoteId();

    Instant occurredAt();

    record QuoteRequestReceived(
            UUID quoteId,
            String quoteReference,
            UUID customerId,
            ShippingMode mode,
            String originPortCode,
            String destinationPortCode,
            Instant occurredAt
    ) implements QuoteEvent {
    }

    record DeniedPartyScreeningCleared(
            UUID quoteId,
            String screeningReferenceId,
            Instant occurredAt
    ) implements QuoteEvent {
    }

    record DeniedPartyScreeningFlagged(
            UUID quoteId,
            EntityType entityType,
            String matchedList,
            BigDecimal matchScore,
            String screeningReferenceId,
            Instant occurredAt
    ) implements QuoteEvent {
    }

    record QuotationBuilt(
            UUID quoteId,
            String quoteReference,
            BigDecimal totalBuyRate,
            BigDecimal totalSellRate,
            String currency,
            Instant occurredAt
    ) implements QuoteEvent {
    }

    record QuoteSentToCustomer(
            UUID quoteId,
            String quoteReference,
            UUID customerId,
            LocalDate validUntil,
            Instant occurredAt
    ) implements QuoteEvent {
    }

    /** Consumed by the booking context — an accepted quote is the trigger for a booking request. */
    record QuoteAccepted(
            UUID quoteId,
            String quoteReference,
            UUID customerId,
            UUID selectedCarrierId,
            ShippingMode mode,
            BigDecimal totalBuyRate,
            BigDecimal totalSellRate,
            String currency,
            Instant occurredAt
    ) implements QuoteEvent {
    }

    record QuoteDeclined(
            UUID quoteId,
            String quoteReference,
            UUID customerId,
            String reason,
            Instant occurredAt
    ) implements QuoteEvent {
    }

    record QuoteExpired(
            UUID quoteId,
            String quoteReference,
            UUID customerId,
            String reason,
            Instant occurredAt
    ) implements QuoteEvent {
    }

    /** The underlying carrier rate expired while the quote was open — it cannot be accepted as priced. */
    record QuoteRerateRequired(
            UUID quoteId,
            String quoteReference,
            LocalDate rateValidUntil,
            Instant occurredAt
    ) implements QuoteEvent {
    }
}
