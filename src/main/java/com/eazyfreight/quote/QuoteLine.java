package com.eazyfreight.quote;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One priced component of a quotation. Buy and sell are held separately on every
 * line — this is what makes profit-per-file answerable without a reconciliation
 * exercise.
 */
@Entity
@Table(name = "quote_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PACKAGE)
public class QuoteLine {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "quote_id", nullable = false)
    private Quote quote;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_type", nullable = false, length = 32)
    private QuoteLineType lineType;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "buy_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal buyRate;

    @Column(name = "sell_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal sellRate;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "quantity", nullable = false, precision = 12, scale = 4)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit", nullable = false, length = 8)
    private ChargeUnit unit;

    void assignTo(Quote quote) {
        this.quote = quote;
    }

    /** Extended sell amount for this line. */
    public BigDecimal amount() {
        return sellRate.multiply(quantity);
    }

    /** Extended buy amount for this line. */
    public BigDecimal cost() {
        return buyRate.multiply(quantity);
    }
}
