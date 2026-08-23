package com.eazyfreight.logistics.listener;

import com.eazyfreight.compliance.event.ComplianceEvent;
import com.eazyfreight.logistics.service.LogisticsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Opens the inbound gate when CBP issues an ITN.
 *
 * <p>This is the join the specification describes and the monolith lacks entirely:
 * its logistics track has no awareness of ITN status, so drivers are dispatched to
 * the terminal regardless and the container gets turned away.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ItnGateListener {

    private final LogisticsService logisticsService;

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ComplianceEvent.ItnGateCheckPassed event) {
        logisticsService.recordItnReceived(event.bookingId(), event.itnNumber());
        log.info("ITN gate cleared for booking {} with {} — inbound dispatch authorised",
                event.bookingId(), event.itnNumber());
    }
}
