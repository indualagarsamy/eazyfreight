package com.eazyfreight.finance.domain;

import com.eazyfreight.finance.domain.FinanceEnums.PaymentMethod;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Money received from the customer.
 *
 * <p>This is the event the whole payables side hangs off: a carrier payable cannot
 * exist without one, because Eazy Freight pays the carrier out of the customer's
 * money rather than its own.
 */
@Entity
@Table(name = "customer_payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerPayment {

    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 16)
    private PaymentMethod paymentMethod;

    @Column(name = "reference", length = 64)
    private String reference;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "recorded_by", nullable = false, length = 64)
    private String recordedBy;

    static CustomerPayment of(
            Invoice invoice, BigDecimal amount, String currency, LocalDate paymentDate,
            PaymentMethod method, String reference, Instant now, String actor) {
        CustomerPayment payment = new CustomerPayment();
        payment.id = UUID.randomUUID();
        payment.invoice = invoice;
        payment.amount = amount;
        payment.currency = currency;
        payment.paymentDate = paymentDate;
        payment.paymentMethod = method;
        payment.reference = reference;
        payment.recordedAt = now;
        payment.recordedBy = actor;
        return payment;
    }
}
