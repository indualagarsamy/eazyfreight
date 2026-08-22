package com.eazyfreight.compliance;

import com.eazyfreight.documentation.DocumentationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * A BOL amendment that changes cargo figures obliges an EEI amendment, since what
 * was declared to CBP no longer matches the document of title.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BolCargoChangeListener {

    private final ComplianceService complianceService;

    @TransactionalEventListener
    public void on(DocumentationEvent.BOLCargoDetailsChanged event) {
        complianceService.flagAmendmentRequired(event.bookingId(),
                "House BOL %s was amended with different cargo figures — the filed EEI is stale"
                        .formatted(event.houseBolNumber()));
    }
}
