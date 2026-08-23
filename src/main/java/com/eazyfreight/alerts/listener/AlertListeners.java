package com.eazyfreight.alerts.listener;

import com.eazyfreight.alerts.domain.AlertType;
import com.eazyfreight.alerts.service.AlertService;
import com.eazyfreight.booking.event.BookingEvent;
import com.eazyfreight.compliance.event.ComplianceEvent;
import com.eazyfreight.documentation.event.DocumentationEvent;
import com.eazyfreight.finance.event.FinanceEvent;
import com.eazyfreight.logistics.domain.MovementType;
import com.eazyfreight.logistics.event.LogisticsEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.ZoneOffset;

/**
 * Where the Alerts track listens to the rest of the system.
 *
 * <p>Two different jobs are done here, and they are worth telling apart.
 *
 * <p><b>Immediate resolution.</b> The scheduled sweep would close these alerts anyway
 * on its next pass, but "anyway, within half an hour" is the wrong answer when someone
 * has just done the thing the alert was shouting about. An alert that stays red after
 * the ITN lands teaches people that red does not mean anything.
 *
 * <p><b>Raising conditions that leave no trace.</b> C-006 and B-003 are announcements,
 * not states — no repository holds "an amendment is owed" or "these thresholds want
 * reviewing". If the event is not caught as it passes, there is nothing left to poll,
 * so these two are raised here and nowhere else.
 *
 * <p>Every handler runs after its source transaction commits and in one of its own, so
 * a failure to raise or resolve an alert cannot roll back the operational work that
 * caused it. An alert is a message about the business; it is not the business.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AlertListeners {

    private final AlertService alerts;
    private final Clock clock;

    // ------------------------------------------------------------ compliance

    /**
     * The ITN clears four separate warnings at once — the gate, the cut-off, the
     * deadline.
     *
     * <p>D-001 is deliberately not among them. Its condition is that <em>any</em> of the
     * container number, the seal or the ITN is missing, so closing it on the strength of
     * one of the three would only have the next sweep raise it again. An alert that
     * closes and reopens every half hour is worse than one that stays open: it looks
     * like the system is confused, and people stop trusting the list. Conditions with
     * more than one input are resolved by the sweep, which can see all of them.
     */
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ComplianceEvent.ItnGateCheckPassed event) {
        int resolved = alerts.resolveFor(event.bookingId(), "ITNNumberReceived", null,
                AlertType.L004_INBOUND_BLOCKED_NO_ITN,
                AlertType.C004_ITN_NOT_RECEIVED_APPROACHING_CUTOFF,
                AlertType.C005_ITN_MISSING_CUTOFF_BREACHED,
                AlertType.C007_LEGAL_DEADLINE_APPROACHING);
        log.debug("ITN {} closed {} alerts on booking {}",
                event.itnNumber(), resolved, event.bookingId());
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ComplianceEvent.EEIFilingInitiated event) {
        alerts.resolveFor(event.bookingId(), "EEIFilingInitiated", null,
                AlertType.C001_EEI_NOT_INITIATED);
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ComplianceEvent.EEISubmittedToCBP event) {
        alerts.resolveFor(event.bookingId(), "EEISubmittedToCBP", null,
                AlertType.C002_EEI_NOT_SUBMITTED);
    }

    /**
     * C-006 has no state behind it: {@code flagAmendmentRequired} publishes and returns.
     * Caught here or lost.
     */
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ComplianceEvent.EEIAmendmentRequired event) {
        alerts.raiseFromEvent(
                event.bookingId(), AlertType.C006_EEI_AMENDMENT_REQUIRED,
                ("%s: an EEI amendment is required. Reason: %s. File it with CBP before "
                        + "the vessel departs.")
                        .formatted(event.filingReference(), event.reason()),
                event.etdAtRisk() == null
                        ? null : event.etdAtRisk().atStartOfDay().toInstant(ZoneOffset.UTC));
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ComplianceEvent.EEIAmendmentSubmitted event) {
        alerts.resolveFor(event.bookingId(), "EEIAmendmentSubmitted", null,
                AlertType.C006_EEI_AMENDMENT_REQUIRED);
    }

    // ------------------------------------------------------------- logistics

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(LogisticsEvent.TruckDeliveryOrderDispatched event) {
        // One event covers both legs; only the outbound one answers L-001.
        if (event.movementType() != MovementType.OUTBOUND) {
            return;
        }
        alerts.resolveFor(event.bookingId(), "OutboundTruckDispatched", null,
                AlertType.L001_CONTAINER_NOT_DISPATCHED);
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(LogisticsEvent.ContainerDeliveredToCustomer event) {
        alerts.resolveFor(event.bookingId(), "ContainerDeliveredToCustomer", null,
                AlertType.L002_CONTAINER_NOT_DELIVERED_TO_CUSTOMER);
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(LogisticsEvent.CustomerLoadingComplete event) {
        alerts.resolveFor(event.bookingId(), "CustomerLoadingComplete", null,
                AlertType.L003_CUSTOMER_LOADING_OVERDUE);
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(LogisticsEvent.SealNumberIssued event) {
        alerts.resolveFor(event.bookingId(), "SealNumberIssued", null,
                AlertType.L007_SEAL_NUMBER_NOT_RECORDED);
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(LogisticsEvent.TerminalAccepted event) {
        alerts.resolveFor(event.bookingId(), "TerminalGateReceiptIssued", null,
                AlertType.L008_CONTAINER_NOT_DELIVERED_TO_PORT,
                AlertType.L005_EARLY_PORT_DELIVERY_RISK);
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(LogisticsEvent.ContainerReleasedFromExamination event) {
        alerts.resolveFor(event.bookingId(), "CBPExaminationReleased", null,
                AlertType.L006_CBP_HOLD_CUTOFF_AT_RISK);
    }

    // --------------------------------------------------------- documentation

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(DocumentationEvent.MasterBOLInstructionsSentToCarrier event) {
        alerts.resolveFor(event.bookingId(), "MasterBOLInstructionsSentToCarrier", null,
                AlertType.D002_INSTRUCTIONS_NOT_SENT);
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(DocumentationEvent.MasterBOLReceived event) {
        alerts.resolveFor(event.bookingId(), "MasterBOLReceived", null,
                AlertType.D003_MASTER_BOL_NOT_RECEIVED);
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(DocumentationEvent.HouseBOLGenerated event) {
        alerts.resolveFor(event.bookingId(), "HouseBOLGenerated", null,
                AlertType.D004_HOUSE_BOL_NOT_GENERATED);
    }

    // --------------------------------------------------------------- finance

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(FinanceEvent.InvoiceIssuedToCustomer event) {
        alerts.resolveFor(event.bookingId(), "InvoiceIssuedToCustomer", null,
                AlertType.F001_INVOICE_NOT_ISSUED);
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(FinanceEvent.CustomerPaymentReceived event) {
        // A part payment is not an answer. Closing the overdue alert on the strength of
        // one would have the next sweep raise it again, and an alert that flickers is an
        // alert people learn to ignore.
        if (event.outstandingAmount().signum() > 0) {
            return;
        }
        alerts.resolveFor(event.bookingId(), "CustomerPaymentReceived", null,
                AlertType.F002_PAYMENT_APPROACHING_DUE,
                AlertType.F003_PAYMENT_OVERDUE,
                AlertType.F004_CREDIT_HOLD_THRESHOLD);
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(FinanceEvent.CarrierPaymentMade event) {
        alerts.resolveFor(event.bookingId(), "CarrierPaymentMade", null,
                AlertType.F005_CARRIER_PAYMENT_DUE,
                AlertType.F006_CARRIER_PAYMENT_OVERDUE,
                AlertType.F007_CARRIER_INVOICE_DISCREPANCY);
    }

    // --------------------------------------------------------------- booking

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(BookingEvent.CarrierBookingConfirmed event) {
        alerts.resolveFor(event.bookingId(), "CarrierBookingConfirmed", null,
                AlertType.B001_CARRIER_CONFIRMATION_NOT_RECEIVED);
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(BookingEvent.BookingRejectedByCarrier event) {
        alerts.resolveFor(event.bookingId(), "BookingRejectedByCarrier", null,
                AlertType.B001_CARRIER_CONFIRMATION_NOT_RECEIVED);
    }

    /**
     * Business rule 1. The booking is on a different vessel now, so every deadline
     * counted from the old ETD is wrong, and B-003 exists to make somebody look at what
     * else the change invalidated — the EEI in particular.
     */
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(BookingEvent.BookingReinstated event) {
        alerts.resolveFor(event.bookingId(), "BookingReinstated", null,
                AlertType.B002_VESSEL_OVERBOOKING);
        alerts.recalculateFor(event.bookingId(), event.newEtd());
        alerts.raiseFromEvent(
                event.bookingId(), AlertType.B003_REINSTATEMENT_RECALCULATION,
                ("%s: reinstated to %s / voyage %s. New ETD %s, new ETA %s. All alert "
                        + "thresholds have been recalculated — review whether the EEI "
                        + "needs amending and reissue the booking confirmation.")
                        .formatted(event.bookingReference(), event.newVesselName(),
                                event.newVoyageNumber(), event.newEtd(), event.newEta()),
                event.newEtd() == null
                        ? null : event.newEtd().atStartOfDay().toInstant(ZoneOffset.UTC));
    }

    /** Business rule 10. */
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(BookingEvent.BookingCancelled event) {
        int resolved = alerts.cancelAllFor(event.bookingId(), "BookingCancelled");
        log.debug("Booking {} cancelled — closed {} alerts", event.bookingId(), resolved);
    }
}
