package com.eazyfreight.quote.repository;

import com.eazyfreight.quote.domain.Quote;
import com.eazyfreight.quote.domain.QuoteStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuoteRepository extends JpaRepository<Quote, UUID> {

    Optional<Quote> findByQuoteReference(String quoteReference);

    List<Quote> findByCustomerId(UUID customerId);

    List<Quote> findByCustomerIdAndStatus(UUID customerId, QuoteStatus status);

    List<Quote> findByStatus(QuoteStatus status);

    List<Quote> findByStatusIn(List<QuoteStatus> statuses);

    /** Open quotes lapsing on or before the given date — drives the follow-up alert. */
    List<Quote> findByStatusAndValidUntilLessThanEqual(QuoteStatus status, LocalDate validUntil);

    /** Duplicate detection: an open quote already exists for this customer and lane. */
    List<Quote> findByCustomerIdAndOriginPortCodeAndDestinationPortCodeAndStatusIn(
            UUID customerId, String originPortCode, String destinationPortCode, List<QuoteStatus> statuses);
}
