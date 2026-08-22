package com.eazyfreight.finance;

import com.eazyfreight.common.BusinessDays;
import com.eazyfreight.finance.FinanceEnums.InvoiceStatus;
import com.eazyfreight.finance.FinanceEnums.InvoiceType;
import com.eazyfreight.finance.FinanceEnums.PaymentMethod;
import com.eazyfreight.finance.FinanceEnums.PaymentTermsType;
import com.eazyfreight.exception.DomainRuleViolationException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.AbstractAggregateRoot;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Invoice aggregate root.
 *
 * <p>Two rules from the specification shape it. An invoice is <em>prepared</em> at
 * booking confirmation but not <em>issued</em> until the House BOL exists, because
 * the BOL carries the actual weight and container number the invoice is billed on.
 * And buy sits alongside sell on every line, so margin is a property of the record
 * rather than a month-end reconciliation across two disconnected systems.
 *
 * <p>Recording a customer payment is what creates a carrier payable — see
 * {@link CarrierPayable}. That direction is deliberate and enforced.
 */
@Entity
@Table(name = "invoices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invoice extends AbstractAggregateRoot<Invoice> implements Persistable<UUID> {

    private static final int NET_30_DAYS = 30;
    private static final int WEEKS_BEFORE_ARRIVAL_DAYS = 14;

    @Id
    private UUID id;

    @Transient
    private boolean isNew = true;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 32)
    private String invoiceNumber;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    /** Null until the Documentation track issues the House BOL. */
    @Column(name = "house_bol_id")
    private UUID houseBolId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private InvoiceStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_type", nullable = false, length = 16)
    private InvoiceType invoiceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_terms_type", nullable = false, length = 32)
    private PaymentTermsType paymentTermsType;

    @Column(name = "invoice_date")
    private LocalDate invoiceDate;

    @Column(name = "payment_due_date")
    private LocalDate paymentDueDate;

    /** Carrier-confirmed arrival, used when terms are two weeks before arrival. */
    @Column(name = "confirmed_eta")
    private LocalDate confirmedEta;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "total_buy_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalBuyAmount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "paid_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    /** True once the actual figures from loading have been applied. */
    @Column(name = "actuals_confirmed", nullable = false)
    private boolean actualsConfirmed;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "issued_by", length = 64)
    private String issuedBy;

    @Column(name = "pdf_sent_at")
    private Instant pdfSentAt;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "voided_by", length = 64)
    private String voidedBy;

    @Column(name = "void_reason")
    private String voidReason;

    /** Set when this record is a credit note against another invoice. */
    @Column(name = "credit_note_against_invoice_id")
    private UUID creditNoteAgainstInvoiceId;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("lineNumber")
    private List<InvoiceLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("paymentDate")
    private List<CustomerPayment> payments = new ArrayList<>();

    // ----------------------------------------------------------- 1. prepare

    /**
     * Opens a draft invoice at booking confirmation. Not a demand for payment — the
     * figures are still the booking's estimates.
     */
    public static Invoice prepare(
            String invoiceNumber, UUID bookingId, UUID customerId,
            PaymentTermsType paymentTermsType, LocalDate confirmedEta,
            String currency, Instant now
    ) {
        Invoice invoice = new Invoice();
        invoice.id = UUID.randomUUID();
        invoice.invoiceNumber = invoiceNumber;
        invoice.bookingId = bookingId;
        invoice.customerId = customerId;
        invoice.status = InvoiceStatus.PREPARED;
        invoice.invoiceType = InvoiceType.FREIGHT;
        invoice.paymentTermsType = paymentTermsType == null
                // No terms on file: default to the more conservative option.
                ? PaymentTermsType.TWO_WEEKS_BEFORE_ARRIVAL : paymentTermsType;
        invoice.confirmedEta = confirmedEta;
        invoice.currency = currency;
        invoice.createdAt = now;
        invoice.registerEvent(new FinanceEvent.InvoicePrepared(
                invoice.id, bookingId, invoiceNumber, BigDecimal.ZERO, currency, now));
        return invoice;
    }

    /** A storage fee invoice, kept separate from the freight invoice. */
    public static Invoice forStorageFee(
            String invoiceNumber, UUID bookingId, UUID customerId,
            String currency, Instant now) {
        Invoice invoice = prepare(invoiceNumber, bookingId, customerId,
                PaymentTermsType.NET_30, null, currency, now);
        invoice.invoiceType = InvoiceType.STORAGE_FEE;
        return invoice;
    }

    public void replaceLines(List<LineDraft> drafts) {
        requireNotIssued("Invoice lines cannot be changed once the invoice is issued");
        lines.clear();
        int number = 1;
        for (LineDraft draft : drafts) {
            lines.add(InvoiceLine.of(this, number++, draft.description(),
                    draft.buyAmount(), draft.sellAmount(), draft.quantity(), draft.unit()));
        }
        recalculateTotals();
    }

    /** 2. UpdateInvoiceWithActuals — the figures the House BOL will carry. */
    public void applyActuals(List<LineDraft> drafts, Instant now) {
        requireNotIssued("Actuals cannot be applied once the invoice is issued");
        replaceLines(drafts);
        this.actualsConfirmed = true;
    }

    /** Told by the Documentation track that the House BOL now exists. */
    public void linkHouseBol(UUID houseBolId) {
        this.houseBolId = houseBolId;
    }

    // ------------------------------------------------------------- 3. issue

    /** Why the invoice cannot be issued yet, or null when it can. */
    public String issueBlockedReason() {
        if (status == InvoiceStatus.VOIDED) return "The invoice is voided";
        if (status.isIssued()) return "The invoice has already been issued";
        if (houseBolId == null) {
            return "The House BOL has not been issued — it carries the actuals this invoice bills on";
        }
        if (!actualsConfirmed) return "Actual cargo figures have not been confirmed";
        if (lines.isEmpty()) return "The invoice has no lines";
        return null;
    }

    public void issue(LocalDate today, Instant now, String actor) {
        String blocked = issueBlockedReason();
        if (blocked != null) {
            throw new DomainRuleViolationException("Invoice cannot be issued: " + blocked);
        }

        this.status = InvoiceStatus.ISSUED;
        this.invoiceDate = today;
        this.paymentDueDate = computeDueDate(today);
        this.issuedAt = now;
        this.issuedBy = actor;

        registerEvent(new FinanceEvent.InvoiceIssuedToCustomer(
                id, bookingId, invoiceNumber, totalAmount, paymentDueDate, now));
    }

    public void recordPdfSent(Instant now) {
        this.pdfSentAt = now;
    }

    // ------------------------------------------------------------ 5. payment

    /**
     * Records money in. Raises {@code CustomerPaymentReceived}, which is what allows
     * a carrier payable to be created — the causal chain, not two calendars.
     */
    public CustomerPayment recordPayment(
            BigDecimal amount, LocalDate paymentDate, PaymentMethod method,
            String reference, Instant now, String actor) {
        if (!status.isIssued()) {
            throw new DomainRuleViolationException(
                    "Payment cannot be recorded against an invoice that has not been issued (status "
                            + status + ")");
        }
        if (amount.signum() <= 0) {
            throw new DomainRuleViolationException("Payment amount must be positive");
        }
        if (amount.compareTo(outstandingAmount()) > 0) {
            throw new DomainRuleViolationException(
                    "Payment of " + amount + " exceeds the outstanding balance of " + outstandingAmount());
        }

        CustomerPayment payment = CustomerPayment.of(
                this, amount, currency, paymentDate, method, reference, now, actor);
        payments.add(payment);
        this.paidAmount = paidAmount.add(amount);
        this.status = outstandingAmount().signum() == 0
                ? InvoiceStatus.PAID : InvoiceStatus.PARTIALLY_PAID;

        registerEvent(new FinanceEvent.CustomerPaymentReceived(
                id, bookingId, payment.getId(), amount, currency,
                paymentDate, outstandingAmount(), now));
        return payment;
    }

    // ------------------------------------------------- 9-10. void, credit note

    public void voidInvoice(String reason, Instant now, String actor) {
        if (status == InvoiceStatus.VOIDED) {
            throw new DomainRuleViolationException("Invoice " + invoiceNumber + " is already voided");
        }
        if (paidAmount.signum() > 0) {
            throw new DomainRuleViolationException(
                    "Invoice " + invoiceNumber + " has payments against it — issue a credit note instead");
        }
        this.status = InvoiceStatus.VOIDED;
        this.voidedAt = now;
        this.voidedBy = actor;
        this.voidReason = reason;
        registerEvent(new FinanceEvent.InvoiceVoided(id, bookingId, invoiceNumber, reason, now));
    }

    /** A credit note is a separate negative document, never an edit to the original. */
    public static Invoice creditNote(
            String invoiceNumber, Invoice against, BigDecimal amount,
            String reason, Instant now, String actor) {
        if (!against.getStatus().isIssued()) {
            throw new DomainRuleViolationException(
                    "A credit note can only be raised against an issued invoice");
        }
        Invoice note = new Invoice();
        note.id = UUID.randomUUID();
        note.invoiceNumber = invoiceNumber;
        note.bookingId = against.bookingId;
        note.customerId = against.customerId;
        note.houseBolId = against.houseBolId;
        note.status = InvoiceStatus.ISSUED;
        note.invoiceType = InvoiceType.CREDIT_NOTE;
        note.paymentTermsType = against.paymentTermsType;
        note.creditNoteAgainstInvoiceId = against.id;
        note.currency = against.currency;
        note.totalAmount = amount.negate();
        note.totalBuyAmount = BigDecimal.ZERO;
        note.actualsConfirmed = true;
        note.invoiceDate = LocalDate.ofInstant(now, java.time.ZoneOffset.UTC);
        note.issuedAt = now;
        note.issuedBy = actor;
        note.notes = reason;
        note.createdAt = now;
        note.registerEvent(new FinanceEvent.CreditNoteIssued(
                note.id, against.bookingId, against.id, amount, reason, now));
        return note;
    }

    // ----------------------------------------------------------------- queries

    public BigDecimal outstandingAmount() {
        return totalAmount.subtract(paidAmount);
    }

    /** Sell less buy. Answerable per file, which is the point. */
    public BigDecimal margin() {
        return totalAmount.subtract(totalBuyAmount);
    }

    public boolean isOverdueAsOf(LocalDate today) {
        return status.isIssued()
                && status != InvoiceStatus.PAID
                && paymentDueDate != null
                && paymentDueDate.isBefore(today);
    }

    public long daysOverdue(LocalDate today) {
        if (!isOverdueAsOf(today)) {
            return 0;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(paymentDueDate, today);
    }

    public List<InvoiceLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public List<CustomerPayment> getPayments() {
        return Collections.unmodifiableList(payments);
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    public java.util.Collection<Object> pendingEvents() {
        return domainEvents();
    }

    // ----------------------------------------------------------------- helpers

    /**
     * Two weeks before arrival protects us — the customer pays before the cargo can
     * be released. Net 30 is for established credit customers.
     */
    private LocalDate computeDueDate(LocalDate invoiceDate) {
        if (paymentTermsType == PaymentTermsType.NET_30 || confirmedEta == null) {
            return invoiceDate.plusDays(NET_30_DAYS);
        }
        return confirmedEta.minusDays(WEEKS_BEFORE_ARRIVAL_DAYS);
    }

    private void recalculateTotals() {
        this.totalAmount = lines.stream()
                .map(InvoiceLine::extendedSell).reduce(BigDecimal.ZERO, BigDecimal::add);
        this.totalBuyAmount = lines.stream()
                .map(InvoiceLine::extendedBuy).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void requireNotIssued(String message) {
        if (status.isIssued() || status == InvoiceStatus.VOIDED) {
            throw new DomainRuleViolationException(message + " (status was " + status + ")");
        }
    }

    /** Input shape for building invoice lines. */
    public record LineDraft(
            String description, BigDecimal buyAmount, BigDecimal sellAmount,
            BigDecimal quantity, String unit
    ) {
    }
}
