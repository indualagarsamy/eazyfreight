package com.eazyfreight.alerts.domain;

import com.eazyfreight.alerts.service.AlertFactsAssembler;
import com.eazyfreight.booking.domain.Booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Everything the alert conditions need to know about one booking, read once.
 *
 * <p>The Alerts track owns no business data — it watches the other five. Left to itself
 * that becomes thirty-one conditions each reaching into whichever repository it fancies,
 * which is both slow and impossible to test without standing up the whole application.
 * So the reach happens exactly once, in {@link AlertFactsAssembler}, and produces this.
 *
 * <p>Everything is a plain value. No aggregate is handed across, because a condition
 * that can call a command method on a Booking is a condition that will eventually call
 * one. Alerts observe; they do not act on other contexts.
 *
 * @param daysToEtd whole days from today to the confirmed ETD; negative once it passes,
 *                  null when no carrier confirmation has landed yet
 */
public record AlertFacts(
        UUID bookingId,
        String bookingReference,
        LocalDate today,
        Instant now,

        // ---- booking
        boolean cancelled,
        boolean overbooked,
        boolean awaitingCarrierConfirmation,
        Instant submittedToCarrierAt,
        boolean carrierConfirmed,
        LocalDate confirmedEtd,
        LocalDate confirmedEta,
        Integer daysToEtd,
        boolean etdChangedPendingNotification,

        // ---- logistics
        boolean outboundDispatched,
        boolean deliveredToCustomer,
        LocalDate deliveredToCustomerOn,
        boolean loadingComplete,
        boolean sealRecorded,
        boolean itnReceived,
        String itnNumber,
        boolean inboundDispatched,
        Instant inboundScheduledDelivery,
        boolean atTerminal,
        LocalDate earliestAcceptanceDate,
        LocalDate vesselCutOffDate,
        BigDecimal storageFeeDailyRate,
        boolean storageFeeApplies,
        Integer storageDaysEarly,
        boolean underCbpExamination,
        boolean vesselDeparted,
        String containerNumber,

        // ---- compliance
        boolean filingInitiated,
        boolean filingSubmitted,
        boolean filingRejected,
        String rejectionCode,
        String rejectionDescription,
        boolean amendmentRequired,
        String amendmentReason,

        // ---- documentation
        boolean instructionsSent,
        Instant instructionsSentAt,
        boolean masterBolReceived,
        Instant masterBolReceivedAt,
        boolean houseBolGenerated,
        boolean houseBolDistributed,
        Instant houseBolGeneratedAt,

        // ---- finance
        boolean invoicePrepared,
        boolean invoiceIssued,
        String invoiceNumber,
        BigDecimal invoiceAmount,
        LocalDate paymentDueDate,
        boolean invoiceSettled,
        Integer daysPaymentOverdue,
        boolean customerOnCreditHold,
        boolean carrierPayableOpen,
        LocalDate carrierPayableDueDate,
        BigDecimal carrierPayableAmount,
        boolean carrierInvoiceMatched,
        BigDecimal carrierInvoiceVariance,
        boolean storageFeeInvoiced
) {

    /** Days until the vessel cut-off, or null when the terminal has not told us one. */
    public Integer daysToCutOff() {
        if (vesselCutOffDate == null) {
            return null;
        }
        return (int) java.time.temporal.ChronoUnit.DAYS.between(today, vesselCutOffDate);
    }

    /** Hours since the booking went to the carrier, or -1 if it has not. */
    public long hoursSinceSubmission() {
        return submittedToCarrierAt == null
                ? -1 : java.time.Duration.between(submittedToCarrierAt, now).toHours();
    }

    public long hoursSince(Instant moment) {
        return moment == null ? -1 : java.time.Duration.between(moment, now).toHours();
    }

    /** True when the ETD is known and no more than {@code days} away. */
    public boolean withinEtd(Integer days) {
        return daysToEtd != null && days != null && daysToEtd <= days;
    }

    /** The three things the Master BOL instructions cannot be compiled without. */
    public String missingPreconditions() {
        StringBuilder missing = new StringBuilder();
        if (containerNumber == null) {
            missing.append("container number");
        }
        if (!sealRecorded) {
            missing.append(missing.isEmpty() ? "" : ", ").append("seal number");
        }
        if (!itnReceived) {
            missing.append(missing.isEmpty() ? "" : ", ").append("ITN");
        }
        return missing.toString();
    }

    public boolean preconditionsMet() {
        return containerNumber != null && sealRecorded && itnReceived;
    }
}
