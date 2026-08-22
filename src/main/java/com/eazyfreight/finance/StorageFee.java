package com.eazyfreight.finance;

import com.eazyfreight.finance.FinanceEnums.StorageFeeCause;
import com.eazyfreight.finance.FinanceEnums.StorageFeeResponsibility;
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
 * A terminal storage charge, arising in Logistics and invoiced here.
 *
 * <p>What matters is <em>who caused it</em>: a customer who insisted on early
 * delivery pays it, our own late documentation means we absorb it, and an unclear
 * carrier acceptance window is disputed. The monolith tracks these in a spreadsheet
 * and discovers them when the terminal's invoice lands, by which point the cause is
 * a matter of memory.
 */
@Entity
@Table(name = "storage_fees")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StorageFee extends AbstractAggregateRoot<StorageFee> implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew = true;

    @Column(name = "booking_id", nullable = false, unique = true)
    private UUID bookingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "cause", nullable = false, length = 32)
    private StorageFeeCause cause;

    @Enumerated(EnumType.STRING)
    @Column(name = "responsibility", nullable = false, length = 24)
    private StorageFeeResponsibility responsibility;

    @Column(name = "daily_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal dailyRate;

    @Column(name = "days", nullable = false)
    private int days;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "period_from")
    private LocalDate periodFrom;

    @Column(name = "period_to")
    private LocalDate periodTo;

    /** Set when the fee is passed through and a storage invoice is raised. */
    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "notes")
    private String notes;

    public static StorageFee calculate(
            UUID bookingId, StorageFeeCause cause, BigDecimal dailyRate, int days,
            LocalDate periodFrom, LocalDate periodTo, String currency, Instant now
    ) {
        if (days < 1) {
            throw new DomainRuleViolationException("A storage fee needs at least one chargeable day");
        }
        StorageFee fee = new StorageFee();
        fee.id = UUID.randomUUID();
        fee.bookingId = bookingId;
        fee.cause = cause;
        // Who pays is a judgement call, made explicitly rather than assumed.
        fee.responsibility = StorageFeeResponsibility.UNDETERMINED;
        fee.dailyRate = dailyRate;
        fee.days = days;
        fee.amount = dailyRate.multiply(BigDecimal.valueOf(days));
        fee.currency = currency;
        fee.periodFrom = periodFrom;
        fee.periodTo = periodTo;
        fee.createdAt = now;

        fee.registerEvent(new FinanceEvent.StorageFeeCalculated(
                fee.id, bookingId, fee.amount, days, fee.responsibility, now));
        return fee;
    }

    public void assignResponsibility(StorageFeeResponsibility responsibility, String notes) {
        if (invoiceId != null) {
            throw new DomainRuleViolationException(
                    "Responsibility cannot change once the storage fee has been invoiced");
        }
        this.responsibility = responsibility;
        this.notes = notes;
    }

    /** Only a customer-caused fee is passed through. */
    public String invoiceBlockedReason() {
        if (invoiceId != null) return "Already invoiced";
        if (responsibility == StorageFeeResponsibility.UNDETERMINED) {
            return "Assign responsibility before invoicing";
        }
        if (responsibility != StorageFeeResponsibility.CUSTOMER) {
            return "This fee is " + (responsibility == StorageFeeResponsibility.EAZY_FREIGHT
                    ? "absorbed by Eazy Freight" : "disputed with the carrier")
                    + ", so it is not passed through";
        }
        return null;
    }

    public void linkInvoice(UUID invoiceId) {
        String blocked = invoiceBlockedReason();
        if (blocked != null) {
            throw new DomainRuleViolationException("Storage fee cannot be invoiced: " + blocked);
        }
        this.invoiceId = invoiceId;
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
}
