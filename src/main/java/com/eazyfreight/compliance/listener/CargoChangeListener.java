package com.eazyfreight.compliance.listener;

import com.eazyfreight.compliance.service.ComplianceService;
import com.eazyfreight.logistics.event.LogisticsEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reacts to actual cargo figures diverging from what was filed.
 *
 * <p>Business rule 5 of the compliance specification: if weight, pieces or value
 * change after the ITN was issued, CBP is owed an amendment before departure. In
 * the monolith this depended on someone remembering.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CargoChangeListener {

    private final ComplianceService complianceService;

    @TransactionalEventListener
    public void on(LogisticsEvent.AesAmendmentRequired event) {
        complianceService.flagAmendmentRequired(event.bookingId(),
                "Actual cargo differs from the filed figures by %s kg — CBP is owed an amendment"
                        .formatted(event.weightVarianceKg()));
    }
}
