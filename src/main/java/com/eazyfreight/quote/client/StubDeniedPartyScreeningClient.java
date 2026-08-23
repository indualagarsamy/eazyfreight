package com.eazyfreight.quote.client;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Local stand-in for the real screening provider (Descartes, Visual Compliance, or
 * equivalent) so the quote flow is exercisable end to end without a vendor account.
 *
 * <p>Clears everything except names containing the marker below, which flag as an
 * OFAC SDN match — enough to drive the halted-quote path in tests and demos.
 * Replace with a real adapter before any production use.
 */
@Component
public class StubDeniedPartyScreeningClient implements DeniedPartyScreeningClient {

    private static final String FLAG_MARKER = "DENIED";

    private final AtomicLong sequence = new AtomicLong();

    @Override
    public ScreeningResult screen(ScreeningRequest request) {
        String reference = "SCR-STUB-%05d".formatted(sequence.incrementAndGet());

        if (request.name() != null && request.name().toUpperCase(Locale.ROOT).contains(FLAG_MARKER)) {
            return ScreeningResult.flagged("OFAC SDN List", new BigDecimal("0.97"), reference);
        }
        return ScreeningResult.cleared(reference);
    }
}
