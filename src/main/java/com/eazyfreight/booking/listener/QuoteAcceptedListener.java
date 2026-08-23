package com.eazyfreight.booking.listener;

import com.eazyfreight.quote.domain.Quote;
import com.eazyfreight.quote.event.QuoteEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reacts to an accepted quote — the trigger for a booking request.
 *
 * <p>It does not create the booking automatically. A booking requires shipper and
 * consignee party identifiers that the Quote aggregate does not carry (the quote
 * captures those parties as free-text names and addresses for screening only), so
 * the missing pieces have to come from operations. What this listener does is make
 * the handoff visible instead of leaving it to a staff member noticing an email.
 *
 * <p>Runs after commit, so a rolled-back acceptance never produces a handoff.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class QuoteAcceptedListener {

    @TransactionalEventListener
    public void on(QuoteEvent.QuoteAccepted event) {
        log.info("Quote {} accepted for customer {} on carrier {} — awaiting booking request "
                        + "(shipperId and consigneeId required)",
                event.quoteReference(), event.customerId(), event.selectedCarrierId());
    }
}
