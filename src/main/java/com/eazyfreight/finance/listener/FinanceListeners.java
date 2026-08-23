package com.eazyfreight.finance.listener;

import com.eazyfreight.booking.event.BookingEvent;
import com.eazyfreight.documentation.event.DocumentationEvent;
import com.eazyfreight.finance.service.FinanceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Where Finance joins the rest of the system.
 *
 * <p>Two of these consume events that other contexts have been raising with nobody
 * listening: {@code BookingConfirmedForInvoicing} since the booking work, and
 * {@code HouseBOLGenerated} since Documentation. The second is the one that matters
 * — the invoice is prepared at confirmation but cannot be issued until the House
 * BOL exists, because the BOL carries the actual figures it bills on.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FinanceListeners {

    private final FinanceService financeService;

    /** A confirmed booking gets a prepared invoice — not yet a demand for payment. */
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(BookingEvent.BookingConfirmedForInvoicing event) {
        try {
            financeService.prepareInvoice(event.bookingId(), null);
            log.info("Invoice prepared for booking {}", event.bookingReference());
        } catch (RuntimeException ex) {
            log.warn("Could not prepare an invoice for {}: {}",
                    event.bookingReference(), ex.getMessage());
        }
    }

    /** The House BOL now exists, which unblocks issuing the invoice. */
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(DocumentationEvent.HouseBOLGenerated event) {
        financeService.linkHouseBol(event.bookingId(), event.houseBolId());
        log.info("House BOL {} linked to the invoice for booking {} — it can now be issued",
                event.houseBolNumber(), event.bookingId());
    }

    /** A cancelled booking should not leave an open invoice behind. */
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(BookingEvent.BookingCancelled event) {
        financeService.voidForCancelledBooking(event.bookingId(), event.reason());
    }
}
