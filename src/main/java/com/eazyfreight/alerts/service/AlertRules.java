package com.eazyfreight.alerts.service;

import com.eazyfreight.alerts.domain.AlertFacts;
import com.eazyfreight.alerts.domain.AlertTrack;
import com.eazyfreight.alerts.domain.AlertType;
import com.eazyfreight.alerts.listener.AlertListeners;
import com.eazyfreight.booking.domain.Booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * When each alert condition holds, what it should say, and by when it must be answered.
 *
 * <p>One table, thirty conditions, each a pure function of an {@link AlertFacts}. Pure
 * is the point: a condition that cannot reach a repository cannot accidentally depend on
 * an ordering, and the whole table can be exercised against record literals in
 * milliseconds instead of against a running application.
 *
 * <p>Two of the thirty-one definitions are missing from here on purpose. C-006 (EEI
 * amendment required) and B-003 (reinstatement recalculation) are announcements that
 * leave no trace in anybody's state — by the time a scheduler looks, there is nothing
 * to see. Those are caught as they pass, in {@link AlertListeners}. Every other
 * condition is visible in the data, and polling it means an alert that was missed
 * because a listener threw still turns up on the next sweep.
 */
final class AlertRules {

    /** The specification's cut-off for a materially different carrier invoice. */
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private record Rule(
            Predicate<AlertFacts> condition,
            Function<AlertFacts, String> message,
            Function<AlertFacts, Instant> deadline) {
    }

    private static final Map<AlertType, Rule> RULES = new EnumMap<>(AlertType.class);

    private AlertRules() {
    }

    static {
        // ---------------------------------------------------------- Logistics

        rule(AlertType.L001_CONTAINER_NOT_DISPATCHED,
                f -> f.withinEtd(10) && !f.outboundDispatched(),
                f -> ("%s: ETD is %s (%d days away). The container has not been dispatched "
                        + "from the carrier yard. Arrange the outbound truck now.")
                        .formatted(f.bookingReference(), f.confirmedEtd(), f.daysToEtd()),
                AlertRules::etdDeadline);

        rule(AlertType.L002_CONTAINER_NOT_DELIVERED_TO_CUSTOMER,
                f -> f.withinEtd(8) && f.outboundDispatched() && !f.deliveredToCustomer(),
                f -> ("%s: ETD is %s (%d days away). The container was dispatched but "
                        + "delivery to the customer is not confirmed. Check with the driver.")
                        .formatted(f.bookingReference(), f.confirmedEtd(), f.daysToEtd()),
                AlertRules::etdDeadline);

        rule(AlertType.L003_CUSTOMER_LOADING_OVERDUE,
                f -> f.withinEtd(7) && f.deliveredToCustomer() && !f.loadingComplete(),
                f -> ("%s: ETD is %s (%d days away). The container reached the customer%s "
                        + "but loading has not been confirmed. Contact the customer.")
                        .formatted(f.bookingReference(), f.confirmedEtd(), f.daysToEtd(),
                                f.deliveredToCustomerOn() == null
                                        ? "" : " on " + f.deliveredToCustomerOn()),
                AlertRules::etdDeadline);

        rule(AlertType.L004_INBOUND_BLOCKED_NO_ITN,
                f -> f.loadingComplete() && !f.itnReceived(),
                f -> ("%s: loading is complete but the inbound dispatch is BLOCKED — no ITN "
                        + "from CBP. File the AES now.%s")
                        .formatted(f.bookingReference(), etdSuffix(f)),
                AlertRules::etdDeadline);

        rule(AlertType.L005_EARLY_PORT_DELIVERY_RISK,
                f -> !f.atTerminal() && f.inboundScheduledDelivery() != null
                        && f.earliestAcceptanceDate() != null
                        && asDate(f.inboundScheduledDelivery()).isBefore(f.earliestAcceptanceDate()),
                f -> ("%s: the inbound truck is scheduled to deliver on %s but the terminal "
                        + "will not accept before %s. Early delivery accrues storage at %s a "
                        + "day. Reschedule, or accept the fee.")
                        .formatted(f.bookingReference(), asDate(f.inboundScheduledDelivery()),
                                f.earliestAcceptanceDate(), rate(f)),
                f -> f.earliestAcceptanceDate() == null
                        ? null : f.earliestAcceptanceDate().atStartOfDay().toInstant(ZoneOffset.UTC));

        rule(AlertType.L006_CBP_HOLD_CUTOFF_AT_RISK,
                f -> f.underCbpExamination() && f.daysToCutOff() != null && f.daysToCutOff() <= 3,
                f -> ("%s: container %s is under CBP examination hold. The vessel cut-off is "
                        + "%s (%d days). If the examination is not finished the cargo misses "
                        + "the vessel. Ask the carrier for a loading delay.")
                        .formatted(f.bookingReference(), f.containerNumber(),
                                f.vesselCutOffDate(), f.daysToCutOff()),
                f -> f.vesselCutOffDate() == null
                        ? null : f.vesselCutOffDate().atStartOfDay().toInstant(ZoneOffset.UTC));

        rule(AlertType.L007_SEAL_NUMBER_NOT_RECORDED,
                f -> f.loadingComplete() && !f.sealRecorded(),
                f -> ("%s: the customer has confirmed loading is complete but no seal number "
                        + "has been recorded. It is required for the Master BOL instructions.")
                        .formatted(f.bookingReference()),
                AlertRules::etdDeadline);

        rule(AlertType.L008_CONTAINER_NOT_DELIVERED_TO_PORT,
                f -> f.withinEtd(3) && !f.atTerminal(),
                f -> ("%s: ETD is %s (%d days). The container has not reached the port "
                        + "terminal. Contact the driver — the loading cut-off is %s.")
                        .formatted(f.bookingReference(), f.confirmedEtd(), f.daysToEtd(),
                                f.vesselCutOffDate() == null ? "not yet known" : f.vesselCutOffDate()),
                AlertRules::etdDeadline);

        // --------------------------------------------------------- Compliance

        rule(AlertType.C001_EEI_NOT_INITIATED,
                f -> f.withinEtd(10) && !f.filingInitiated(),
                f -> ("%s: ETD is %s (%d days away). The AES/EEI filing has not been "
                        + "initiated. File early to leave room for a CBP rejection.")
                        .formatted(f.bookingReference(), f.confirmedEtd(), f.daysToEtd()),
                AlertRules::etdDeadline);

        rule(AlertType.C002_EEI_NOT_SUBMITTED,
                f -> f.withinEtd(7) && f.filingInitiated() && !f.filingSubmitted(),
                f -> ("%s: ETD is %s (%d days away). The EEI was started but never submitted "
                        + "to CBP. Submit now to leave time for ITN processing.")
                        .formatted(f.bookingReference(), f.confirmedEtd(), f.daysToEtd()),
                AlertRules::etdDeadline);

        rule(AlertType.C003_EEI_REJECTED,
                AlertFacts::filingRejected,
                f -> ("%s: CBP has REJECTED the EEI filing. Reason: %s — %s.%s Correct and "
                        + "resubmit.")
                        .formatted(f.bookingReference(), f.rejectionCode(),
                                f.rejectionDescription(), etdSuffix(f)),
                AlertRules::etdDeadline);

        rule(AlertType.C004_ITN_NOT_RECEIVED_APPROACHING_CUTOFF,
                f -> f.withinEtd(5) && !f.itnReceived(),
                f -> ("%s: ETD is %s (%d days away). No ITN from CBP. The Master BOL "
                        + "instructions cannot be sent without it.")
                        .formatted(f.bookingReference(), f.confirmedEtd(), f.daysToEtd()),
                AlertRules::etdDeadline);

        rule(AlertType.C005_ITN_MISSING_CUTOFF_BREACHED,
                f -> f.withinEtd(3) && !f.itnReceived(),
                f -> ("%s: the documentation cut-off has passed with no ITN. Master BOL "
                        + "instructions CANNOT be sent to the carrier and the cargo is at "
                        + "risk of missing the vessel (ETD %s).")
                        .formatted(f.bookingReference(), f.confirmedEtd()),
                AlertRules::etdDeadline);

        rule(AlertType.C007_LEGAL_DEADLINE_APPROACHING,
                f -> f.withinEtd(1) && !f.itnReceived(),
                f -> ("%s: LEGAL DEADLINE. AES requires the EEI at least 24 hours before "
                        + "lading. ETD is %s and no ITN is on file. The cargo cannot "
                        + "lawfully be loaded.")
                        .formatted(f.bookingReference(), f.confirmedEtd()),
                AlertRules::etdDeadline);

        // ------------------------------------------------------------ Finance

        rule(AlertType.F001_INVOICE_NOT_ISSUED,
                f -> f.houseBolGenerated() && !f.invoiceIssued()
                        && f.hoursSince(f.houseBolGeneratedAt()) >= 24,
                f -> ("%s: the House BOL was issued %d hours ago and the invoice is still "
                        + "prepared, not issued. The payment clock has not started.")
                        .formatted(f.bookingReference(), f.hoursSince(f.houseBolGeneratedAt())),
                f -> null);

        rule(AlertType.F002_PAYMENT_APPROACHING_DUE,
                f -> f.invoiceIssued() && !f.invoiceSettled()
                        && daysUntil(f, f.paymentDueDate()) != null
                        && daysUntil(f, f.paymentDueDate()) <= 5
                        && daysUntil(f, f.paymentDueDate()) >= 0,
                f -> ("%s: invoice %s for %s is due in %d days (%s) and no payment has been "
                        + "received. Send a reminder.")
                        .formatted(f.bookingReference(), f.invoiceNumber(),
                                f.invoiceAmount(), daysUntil(f, f.paymentDueDate()),
                                f.paymentDueDate()),
                f -> dayStart(f.paymentDueDate()));

        rule(AlertType.F003_PAYMENT_OVERDUE,
                // isOverdueAsOf already excludes a settled invoice, so a part-paid one
                // that is past due stays overdue — which it is.
                f -> f.daysPaymentOverdue() != null,
                f -> ("%s: invoice %s for %s is OVERDUE by %d days. The due date was %s.")
                        .formatted(f.bookingReference(), f.invoiceNumber(), f.invoiceAmount(),
                                f.daysPaymentOverdue(), f.paymentDueDate()),
                f -> dayStart(f.paymentDueDate()));

        rule(AlertType.F004_CREDIT_HOLD_THRESHOLD,
                f -> f.daysPaymentOverdue() != null && f.daysPaymentOverdue() >= 14
                        && !f.customerOnCreditHold(),
                f -> ("%s: invoice %s is %d days overdue. A CREDIT HOLD is recommended — new "
                        + "bookings for this customer should be reviewed before confirmation.")
                        .formatted(f.bookingReference(), f.invoiceNumber(), f.daysPaymentOverdue()),
                f -> dayStart(f.paymentDueDate()));

        rule(AlertType.F005_CARRIER_PAYMENT_DUE,
                f -> f.carrierPayableOpen() && f.carrierPayableDueDate() != null
                        && f.carrierPayableDueDate().isEqual(f.today()),
                f -> ("%s: the carrier payment of %s is due TODAY (%s). Match the carrier "
                        + "invoice and execute the payment.")
                        .formatted(f.bookingReference(), f.carrierPayableAmount(),
                                f.carrierPayableDueDate()),
                f -> dayStart(f.carrierPayableDueDate()));

        rule(AlertType.F006_CARRIER_PAYMENT_OVERDUE,
                f -> f.carrierPayableOpen() && f.carrierPayableDueDate() != null
                        && f.carrierPayableDueDate().isBefore(f.today()),
                f -> ("%s: the carrier payment of %s is OVERDUE. It was due %s (%d days ago). "
                        + "This is the customer's money we are holding.")
                        .formatted(f.bookingReference(), f.carrierPayableAmount(),
                                f.carrierPayableDueDate(),
                                -daysUntil(f, f.carrierPayableDueDate())),
                f -> dayStart(f.carrierPayableDueDate()));

        rule(AlertType.F007_CARRIER_INVOICE_DISCREPANCY,
                f -> f.carrierPayableOpen() && f.carrierInvoiceMatched()
                        && f.carrierInvoiceVariance() != null
                        && f.carrierInvoiceVariance().compareTo(ZERO) != 0,
                f -> ("%s: the carrier invoice differs from the payable by %s. Resolve it "
                        + "before paying. The payment is due %s.")
                        .formatted(f.bookingReference(), f.carrierInvoiceVariance(),
                                f.carrierPayableDueDate()),
                f -> dayStart(f.carrierPayableDueDate()));

        rule(AlertType.F008_STORAGE_FEE_ACCRUING,
                f -> f.storageFeeApplies() && !f.storageFeeInvoiced(),
                f -> ("%s: container %s reached the terminal %s days before the earliest "
                        + "acceptance date of %s. Storage is accruing at %s a day.")
                        .formatted(f.bookingReference(), f.containerNumber(),
                                f.storageDaysEarly(), f.earliestAcceptanceDate(), rate(f)),
                f -> null);

        // ------------------------------------------------------------ Booking

        rule(AlertType.B001_CARRIER_CONFIRMATION_NOT_RECEIVED,
                f -> f.awaitingCarrierConfirmation() && f.hoursSinceSubmission() >= 48,
                f -> ("%s: submitted to the carrier %d hours ago with no confirmation. Pull "
                        + "the status from INTTRA or contact the carrier.%s")
                        .formatted(f.bookingReference(), f.hoursSinceSubmission(), etdSuffix(f)),
                f -> null);

        rule(AlertType.B002_VESSEL_OVERBOOKING,
                AlertFacts::overbooked,
                f -> ("%s: the carrier has OVERBOOKED the vessel and rolled this booking off "
                        + "it. Contact the customer — reinstate to the next sailing, or "
                        + "cancel. The ETD was %s.")
                        .formatted(f.bookingReference(), f.confirmedEtd()),
                AlertRules::etdDeadline);

        rule(AlertType.B004_ETD_CHANGED_BY_CARRIER,
                AlertFacts::etdChangedPendingNotification,
                f -> ("%s: the carrier has confirmed an ETD of %s, which is not the date the "
                        + "customer was quoted. Notify the customer, recalculate the payment "
                        + "due date, and check whether the EEI needs amending.")
                        .formatted(f.bookingReference(), f.confirmedEtd()),
                AlertRules::etdDeadline);

        // ------------------------------------------------------ Documentation

        rule(AlertType.D001_CUTOFF_APPROACHING_PRECONDITIONS_UNMET,
                f -> f.withinEtd(4) && !f.preconditionsMet(),
                f -> ("%s: the documentation cut-off is close (ETD %s, %d days) and the "
                        + "Master BOL instructions cannot be sent. Missing: %s.")
                        .formatted(f.bookingReference(), f.confirmedEtd(), f.daysToEtd(),
                                f.missingPreconditions()),
                AlertRules::etdDeadline);

        rule(AlertType.D002_INSTRUCTIONS_NOT_SENT,
                f -> f.withinEtd(3) && f.preconditionsMet() && !f.instructionsSent(),
                f -> ("%s: all preconditions are met (container %s, seal recorded, ITN %s) "
                        + "but the Master BOL instructions have not gone to the carrier. "
                        + "Send them — the ETD is %s.")
                        .formatted(f.bookingReference(), f.containerNumber(),
                                f.itnNumber(), f.confirmedEtd()),
                AlertRules::etdDeadline);

        rule(AlertType.D003_MASTER_BOL_NOT_RECEIVED,
                f -> f.instructionsSent() && !f.masterBolReceived()
                        && f.hoursSince(f.instructionsSentAt()) >= 48,
                f -> ("%s: the Master BOL instructions went to the carrier %d hours ago and "
                        + "no Master BOL has come back. Follow up.%s")
                        .formatted(f.bookingReference(), f.hoursSince(f.instructionsSentAt()),
                                etdSuffix(f)),
                AlertRules::etdDeadline);

        rule(AlertType.D004_HOUSE_BOL_NOT_GENERATED,
                f -> f.masterBolReceived() && !f.houseBolGenerated()
                        && f.hoursSince(f.masterBolReceivedAt()) >= 24,
                f -> ("%s: the Master BOL came back from the carrier but no House BOL has "
                        + "been issued to the shipper and consignee.")
                        .formatted(f.bookingReference()),
                f -> null);
    }

    /** The alert types this table evaluates. C-006 and B-003 are deliberately absent. */
    static Iterable<AlertType> polledTypes() {
        return RULES.keySet();
    }

    /**
     * Whether the condition for {@code type} currently holds.
     *
     * <p>A cancelled booking never has a live condition — business rule 10 — and neither
     * does one whose vessel has already sailed, except for the finance conditions, which
     * outlive the voyage: the money is owed whether or not the cargo has arrived.
     */
    static boolean holds(AlertType type, AlertFacts facts) {
        Rule rule = RULES.get(type);
        if (rule == null || facts.cancelled()) {
            return false;
        }
        if (facts.vesselDeparted() && type.track() != AlertTrack.FINANCE) {
            return false;
        }
        return rule.condition().test(facts);
    }

    static String message(AlertType type, AlertFacts facts) {
        return RULES.get(type).message().apply(facts);
    }

    static Instant deadline(AlertType type, AlertFacts facts) {
        return RULES.get(type).deadline().apply(facts);
    }

    // ---------------------------------------------------------------- helpers

    private static void rule(AlertType type, Predicate<AlertFacts> condition,
                             Function<AlertFacts, String> message,
                             Function<AlertFacts, Instant> deadline) {
        RULES.put(type, new Rule(condition, message, deadline));
    }

    private static Instant etdDeadline(AlertFacts facts) {
        return dayStart(facts.confirmedEtd());
    }

    private static Instant dayStart(LocalDate date) {
        return date == null ? null : date.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private static LocalDate asDate(Instant instant) {
        return instant == null ? null : LocalDate.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Integer daysUntil(AlertFacts facts, LocalDate date) {
        return date == null ? null : (int) ChronoUnit.DAYS.between(facts.today(), date);
    }

    private static String rate(AlertFacts facts) {
        return Optional.ofNullable(facts.storageFeeDailyRate())
                .map(BigDecimal::toPlainString).orElse("an unknown rate");
    }

    /** " ETD: 2026-09-21 (5 days)." — appended where the ETD is context, not the trigger. */
    private static String etdSuffix(AlertFacts facts) {
        if (facts.confirmedEtd() == null) {
            return "";
        }
        return " ETD: %s (%d days).".formatted(facts.confirmedEtd(), facts.daysToEtd());
    }
}
