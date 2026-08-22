package com.eazyfreight.common;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Issues business references in the {@code PREFIX-YYYY-NNNNN} form used across the
 * platform.
 *
 * <p>Runs in its own transaction so the counter advances even if the calling
 * transaction later rolls back. That trades a possible gap in the sequence for a
 * guarantee of no duplicates, which is the right way round for a reference that
 * appears on a bill of lading.
 */
@Component
@RequiredArgsConstructor
public class ReferenceGenerator {

    public static final String QUOTE_PREFIX = "Q";
    public static final String BOOKING_PREFIX = "EF";
    public static final String TRUCK_DELIVERY_ORDER_PREFIX = "TDO";
    public static final String EEI_FILING_PREFIX = "EEI";
    public static final String DISPATCH_PREFIX = "TDO";
    public static final String INSTRUCTIONS_PREFIX = "SI";
    public static final String HOUSE_BOL_PREFIX = "HBL";
    public static final String INVOICE_PREFIX = "INV";
    public static final String CREDIT_NOTE_PREFIX = "CN";

    private final ReferenceSequenceRepository repository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String next(String prefix) {
        int year = LocalDate.now(clock).getYear();
        ReferenceSequence sequence = repository.findByPrefixAndSequenceYear(prefix, year)
                .orElseGet(() -> repository.save(new ReferenceSequence(prefix, year)));
        long value = sequence.next();
        repository.save(sequence);
        return "%s-%d-%05d".formatted(prefix, year, value);
    }
}
