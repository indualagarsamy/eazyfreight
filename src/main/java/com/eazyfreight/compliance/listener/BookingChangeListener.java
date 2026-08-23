package com.eazyfreight.compliance.listener;

import com.eazyfreight.booking.domain.Booking;
import com.eazyfreight.booking.event.BookingEvent;
import com.eazyfreight.compliance.service.ComplianceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reacts to booking changes that invalidate a filing already accepted by CBP.
 *
 * <p>This is the consumer for {@code ItnAmendmentRequired}, which the Booking
 * aggregate has been raising on reinstatement since it was written but which
 * nothing listened to. A rolled sailing changes the vessel, voyage and ETD — all
 * declared on the EEI — so CBP is owed an amendment.
 *
 * <p>A cancelled booking is worse: an accepted filing left uncancelled leaves an
 * export reported to the US government that will never happen.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BookingChangeListener {

    private final ComplianceService complianceService;

    @TransactionalEventListener
    public void on(BookingEvent.ItnAmendmentRequired event) {
        complianceService.flagAmendmentRequired(event.bookingId(),
                "Booking rolled to %s departing %s — vessel, voyage and ETD on the filing are stale"
                        .formatted(event.newVesselName(), event.newEtd()));
    }

    @TransactionalEventListener
    public void on(BookingEvent.BookingCancelled event) {
        complianceService.flagAmendmentRequired(event.bookingId(),
                "Booking cancelled (%s) — the accepted filing must be cancelled with CBP"
                        .formatted(event.reason()));
    }
}
