package com.eazyfreight.documentation;

import com.eazyfreight.logistics.LogisticsEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Notices when the Logistics track reports that container, seal and ITN are all in
 * hand — the moment the Documentation track can begin.
 *
 * <p>In the monolith this moment is invisible: an operator decides from memory that
 * it is probably time to send shipping instructions.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PreconditionsListener {

    private final DocumentationService documentationService;

    @TransactionalEventListener
    public void on(LogisticsEvent.DocumentationPreconditionsMet event) {
        DocumentationResponses.Preconditions preconditions =
                documentationService.checkPreconditions(event.bookingId());
        if (preconditions.met()) {
            log.info("Documentation preconditions met for booking {} — container {}, seal {}, ITN {}",
                    event.bookingId(), preconditions.containerNumber(),
                    preconditions.sealNumber(), preconditions.itnNumber());
        }
    }
}
