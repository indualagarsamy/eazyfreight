package com.eazyfreight.finance.event;

import com.eazyfreight.finance.domain.FinanceEnums.StorageFeeResponsibility;

import com.eazyfreight.finance.domain.FinanceEnums;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Domain events raised by the Finance context. */
public sealed interface FinanceEvent {

    UUID bookingId();

    Instant occurredAt();

    record InvoicePrepared(
            UUID invoiceId, UUID bookingId, String invoiceNumber,
            BigDecimal totalAmount, String currency, Instant occurredAt
    ) implements FinanceEvent {
    }

    record InvoiceIssuedToCustomer(
            UUID invoiceId, UUID bookingId, String invoiceNumber,
            BigDecimal totalAmount, LocalDate paymentDueDate, Instant occurredAt
    ) implements FinanceEvent {
    }

    /**
     * The trigger for the payables side. A carrier payable is created from this and
     * cannot exist without it.
     */
    record CustomerPaymentReceived(
            UUID invoiceId, UUID bookingId, UUID paymentId,
            BigDecimal amount, String currency, LocalDate paymentDate,
            BigDecimal outstandingAmount, Instant occurredAt
    ) implements FinanceEvent {
    }

    record CarrierPaymentDue(
            UUID payableId, UUID bookingId, BigDecimal amount,
            LocalDate dueDate, Instant occurredAt
    ) implements FinanceEvent {
    }

    record CarrierPaymentMade(
            UUID payableId, UUID bookingId, BigDecimal amount,
            LocalDate paidOn, boolean late, Instant occurredAt
    ) implements FinanceEvent {
    }

    record InvoiceVoided(
            UUID invoiceId, UUID bookingId, String invoiceNumber,
            String reason, Instant occurredAt
    ) implements FinanceEvent {
    }

    record CreditNoteIssued(
            UUID creditNoteId, UUID bookingId, UUID againstInvoiceId,
            BigDecimal amount, String reason, Instant occurredAt
    ) implements FinanceEvent {
    }

    record StorageFeeCalculated(
            UUID storageFeeId, UUID bookingId, BigDecimal amount,
            int days, FinanceEnums.StorageFeeResponsibility responsibility, Instant occurredAt
    ) implements FinanceEvent {
    }

    record CreditHoldPlaced(
            UUID bookingId, UUID customerId, String reason, Instant occurredAt
    ) implements FinanceEvent {
    }

    record CreditHoldLifted(
            UUID bookingId, UUID customerId, Instant occurredAt
    ) implements FinanceEvent {
    }
}
