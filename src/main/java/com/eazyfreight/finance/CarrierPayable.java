package com.eazyfreight.finance;

import com.eazyfreight.common.BusinessDays;
import com.eazyfreight.finance.FinanceEnums.PayableStatus;
import com.eazyfreight.exception.DomainRuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
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
import java.util.UUID;

/**
 * What we owe the carrier, created from a customer payment.
 *
 * <p>This is the most important financial rule in the system, and it is a
 * <em>causal</em> dependency rather than two independent schedules. Eazy Freight
 * buys vessel space on the customer's behalf and does not pay the carrier out of
 * its own funds: it collects, then remits. So a payable cannot be constructed
 * without the payment that funds it — {@link #fundedBy} is the only way in — and
 * its due date is two business days after that payment, fixed at creation.
 *
 * <p>If the customer pays late, the carrier is late by the same amount. That is an
 * operational fact the system should make visible, not smooth over.
 */
@Entity
@Table(name = "carrier_payables")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CarrierPayable extends AbstractAggregateRoot<CarrierPayable>
        implements Persistable<UUID> {

    /** Operational processing time between collecting and remitting. */
    private static final int REMITTANCE_BUSINESS_DAYS = 2;

    @Id
    private UUID id;

    @Transient
    private boolean isNew = true;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    /** The payment that funds this payable. A payable cannot exist without one. */
    @Column(name = "customer_payment_id", nullable = false, unique = true)
    private UUID customerPaymentId;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "carrier_id")
    private UUID carrierId;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /** Customer payment date plus two business days. Immutable once set. */
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "customer_payment_date", nullable = false)
    private LocalDate customerPaymentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PayableStatus status;

    @Column(name = "carrier_invoice_reference", length = 64)
    private String carrierInvoiceReference;

    @Column(name = "carrier_invoice_amount", precision = 14, scale = 2)
    private BigDecimal carrierInvoiceAmount;

    @Column(name = "carrier_invoice_received_at")
    private Instant carrierInvoiceReceivedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_by", length = 64)
    private String approvedBy;

    @Column(name = "paid_on")
    private LocalDate paidOn;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "paid_by", length = 64)
    private String paidBy;

    @Column(name = "payment_reference", length = 64)
    private String paymentReference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * The only constructor. Takes the funding payment, so the causal chain cannot be
     * bypassed by creating a payable out of nowhere.
     */
    public static CarrierPayable fundedBy(
            UUID customerPaymentId, UUID invoiceId, UUID bookingId, UUID carrierId,
            BigDecimal amount, String currency, LocalDate customerPaymentDate, Instant now
    ) {
        if (customerPaymentId == null) {
            throw new DomainRuleViolationException(
                    "A carrier payable must be funded by a customer payment");
        }
        CarrierPayable payable = new CarrierPayable();
        payable.id = UUID.randomUUID();
        payable.customerPaymentId = customerPaymentId;
        payable.invoiceId = invoiceId;
        payable.bookingId = bookingId;
        payable.carrierId = carrierId;
        payable.amount = amount;
        payable.currency = currency;
        payable.customerPaymentDate = customerPaymentDate;
        payable.dueDate = BusinessDays.add(customerPaymentDate, REMITTANCE_BUSINESS_DAYS);
        payable.status = PayableStatus.PENDING;
        payable.createdAt = now;

        payable.registerEvent(new FinanceEvent.CarrierPaymentDue(
                payable.id, bookingId, amount, payable.dueDate, now));
        return payable;
    }

    /** 6. RecordCarrierInvoiceReceived */
    public void recordCarrierInvoice(String reference, BigDecimal invoiceAmount, Instant now) {
        requireStatus("A carrier invoice can only be recorded on a pending payable",
                PayableStatus.PENDING);
        this.carrierInvoiceReference = reference;
        this.carrierInvoiceAmount = invoiceAmount;
        this.carrierInvoiceReceivedAt = now;
        this.status = PayableStatus.AWAITING_INVOICE_MATCH;
    }

    /** Difference between what the carrier billed and what we expected to pay. */
    public BigDecimal invoiceVariance() {
        return carrierInvoiceAmount == null
                ? BigDecimal.ZERO : carrierInvoiceAmount.subtract(amount);
    }

    /** 7. ApproveCarrierPayment — matching happens before money moves. */
    public void approve(Instant now, String actor) {
        requireStatus("The carrier invoice must be received and matched before approval",
                PayableStatus.AWAITING_INVOICE_MATCH);
        this.status = PayableStatus.APPROVED;
        this.approvedAt = now;
        this.approvedBy = actor;
    }

    /** 8. RecordCarrierPaymentMade */
    public void recordPaid(LocalDate paidOn, String reference, Instant now, String actor) {
        requireStatus("Only an approved payable can be paid", PayableStatus.APPROVED);
        this.status = PayableStatus.PAID;
        this.paidOn = paidOn;
        this.paidAt = now;
        this.paidBy = actor;
        this.paymentReference = reference;

        registerEvent(new FinanceEvent.CarrierPaymentMade(
                id, bookingId, amount, paidOn, paidOn.isAfter(dueDate), now));
    }

    public boolean isOverdueAsOf(LocalDate today) {
        return status != PayableStatus.PAID && dueDate.isBefore(today);
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

    private void requireStatus(String message, PayableStatus expected) {
        if (status != expected) {
            throw new DomainRuleViolationException(message + " (status was " + status + ")");
        }
    }
}
