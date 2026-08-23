package com.eazyfreight.booking.listener;

import com.eazyfreight.booking.repository.BookingRepository;
import com.eazyfreight.compliance.event.ComplianceEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;

/**
 * Records on the booking that an ITN is on file with CBP.
 *
 * <p>This replaces the manual "mark ITN filed" endpoint that stood in for the
 * Compliance context before it existed. The flag now follows a real filing being
 * accepted, which is what makes reinstatement raise an amendment requirement.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ItnFiledListener {

    private final BookingRepository bookingRepository;
    private final Clock clock;

    @TransactionalEventListener
    // Runs after the compliance transaction commits, so it needs its own.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ComplianceEvent.ItnNumberReceived event) {
        bookingRepository.findById(event.bookingId()).ifPresent(booking -> {
            booking.markItnFiled(clock.instant(), "compliance");
            bookingRepository.save(booking);
            log.info("Booking {} now has ITN {} on file{}",
                    booking.getBookingReference(), event.itnNumber(),
                    event.simulated() ? " (simulated)" : "");
        });
    }
}
