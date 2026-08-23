package com.eazyfreight.finance.dto;

import com.eazyfreight.finance.domain.FinanceEnums.InvoiceStatus;
import com.eazyfreight.finance.domain.FinanceEnums.InvoiceType;
import com.eazyfreight.finance.domain.FinanceEnums.PayableStatus;
import com.eazyfreight.finance.domain.FinanceEnums.PaymentMethod;
import com.eazyfreight.finance.domain.FinanceEnums.PaymentTermsType;
import com.eazyfreight.finance.domain.FinanceEnums.StorageFeeCause;
import com.eazyfreight.finance.domain.FinanceEnums.StorageFeeResponsibility;

import com.eazyfreight.finance.domain.CarrierPayable;
import com.eazyfreight.finance.domain.CreditHold;
import com.eazyfreight.finance.domain.CustomerPayment;
import com.eazyfreight.finance.domain.Invoice;
import com.eazyfreight.finance.domain.InvoiceLine;
import com.eazyfreight.finance.domain.StorageFee;


import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Read models for the Finance context. */
public final class FinanceResponses {

    private FinanceResponses() {
    }

    public record Line(
            UUID id, int lineNumber, String description,
            BigDecimal buyAmount, BigDecimal sellAmount, BigDecimal quantity, String unit,
            BigDecimal extendedBuy, BigDecimal extendedSell, BigDecimal margin
    ) {
        static Line from(InvoiceLine l) {
            return new Line(l.getId(), l.getLineNumber(), l.getDescription(),
                    l.getBuyAmount(), l.getSellAmount(), l.getQuantity(), l.getUnit(),
                    l.extendedBuy(), l.extendedSell(), l.margin());
        }
    }

    public record Payment(
            UUID id, BigDecimal amount, String currency, LocalDate paymentDate,
            PaymentMethod paymentMethod, String reference, Instant recordedAt, String recordedBy
    ) {
        static Payment from(CustomerPayment p) {
            return new Payment(p.getId(), p.getAmount(), p.getCurrency(), p.getPaymentDate(),
                    p.getPaymentMethod(), p.getReference(), p.getRecordedAt(), p.getRecordedBy());
        }
    }

    /**
     * @param issueBlockedReason why the invoice cannot be issued, or null. The House
     *                           BOL requirement lives here.
     * @param margin             sell less buy, answerable per file
     */
    public record InvoiceView(
            UUID id, String invoiceNumber, UUID bookingId, UUID houseBolId, UUID customerId,
            InvoiceStatus status, InvoiceType invoiceType, PaymentTermsType paymentTermsType,
            LocalDate invoiceDate, LocalDate paymentDueDate, LocalDate confirmedEta,
            BigDecimal totalAmount, BigDecimal totalBuyAmount, BigDecimal margin,
            String currency, BigDecimal paidAmount, BigDecimal outstandingAmount,
            boolean actualsConfirmed, String issueBlockedReason,
            boolean overdue, long daysOverdue,
            Instant issuedAt, String issuedBy, Instant pdfSentAt,
            Instant voidedAt, String voidedBy, String voidReason,
            UUID creditNoteAgainstInvoiceId, String notes, Instant createdAt,
            List<Line> lines, List<Payment> payments
    ) {
        public static InvoiceView from(Invoice i, LocalDate today) {
            return new InvoiceView(i.getId(), i.getInvoiceNumber(), i.getBookingId(),
                    i.getHouseBolId(), i.getCustomerId(), i.getStatus(), i.getInvoiceType(),
                    i.getPaymentTermsType(), i.getInvoiceDate(), i.getPaymentDueDate(),
                    i.getConfirmedEta(), i.getTotalAmount(), i.getTotalBuyAmount(), i.margin(),
                    i.getCurrency(), i.getPaidAmount(), i.outstandingAmount(),
                    i.isActualsConfirmed(), i.issueBlockedReason(),
                    i.isOverdueAsOf(today), i.daysOverdue(today),
                    i.getIssuedAt(), i.getIssuedBy(), i.getPdfSentAt(),
                    i.getVoidedAt(), i.getVoidedBy(), i.getVoidReason(),
                    i.getCreditNoteAgainstInvoiceId(), i.getNotes(), i.getCreatedAt(),
                    i.getLines().stream().map(Line::from).toList(),
                    i.getPayments().stream().map(Payment::from).toList());
        }
    }

    /**
     * @param customerPaymentDate the payment that funds this; the due date is two
     *                            business days after it and cannot move
     */
    public record PayableView(
            UUID id, UUID bookingId, UUID invoiceId, UUID customerPaymentId, UUID carrierId,
            BigDecimal amount, String currency, LocalDate customerPaymentDate, LocalDate dueDate,
            PayableStatus status, String carrierInvoiceReference, BigDecimal carrierInvoiceAmount,
            BigDecimal invoiceVariance, Instant carrierInvoiceReceivedAt,
            Instant approvedAt, String approvedBy, LocalDate paidOn, String paymentReference,
            boolean overdue, Instant createdAt
    ) {
        public static PayableView from(CarrierPayable p, LocalDate today) {
            return new PayableView(p.getId(), p.getBookingId(), p.getInvoiceId(),
                    p.getCustomerPaymentId(), p.getCarrierId(), p.getAmount(), p.getCurrency(),
                    p.getCustomerPaymentDate(), p.getDueDate(), p.getStatus(),
                    p.getCarrierInvoiceReference(), p.getCarrierInvoiceAmount(),
                    p.invoiceVariance(), p.getCarrierInvoiceReceivedAt(),
                    p.getApprovedAt(), p.getApprovedBy(), p.getPaidOn(), p.getPaymentReference(),
                    p.isOverdueAsOf(today), p.getCreatedAt());
        }
    }

    public record StorageFeeView(
            UUID id, UUID bookingId, StorageFeeCause cause,
            StorageFeeResponsibility responsibility, BigDecimal dailyRate, int days,
            BigDecimal amount, String currency, LocalDate periodFrom, LocalDate periodTo,
            UUID invoiceId, String invoiceBlockedReason, String notes, Instant createdAt
    ) {
        public static StorageFeeView from(StorageFee f) {
            return new StorageFeeView(f.getId(), f.getBookingId(), f.getCause(),
                    f.getResponsibility(), f.getDailyRate(), f.getDays(), f.getAmount(),
                    f.getCurrency(), f.getPeriodFrom(), f.getPeriodTo(), f.getInvoiceId(),
                    f.invoiceBlockedReason(), f.getNotes(), f.getCreatedAt());
        }
    }

    public record CreditHoldView(
            UUID id, UUID customerId, UUID triggeringBookingId, String reason,
            boolean active, Instant placedAt, String placedBy, Instant liftedAt, String liftedBy
    ) {
        public static CreditHoldView from(CreditHold h) {
            return new CreditHoldView(h.getId(), h.getCustomerId(), h.getTriggeringBookingId(),
                    h.getReason(), h.isActive(), h.getPlacedAt(), h.getPlacedBy(),
                    h.getLiftedAt(), h.getLiftedBy());
        }
    }
}
