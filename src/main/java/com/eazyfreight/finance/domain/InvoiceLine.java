package com.eazyfreight.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One charge on an invoice, carrying both what it cost us and what we charge.
 *
 * <p>Holding buy alongside sell is what makes profit-per-file answerable at the
 * transaction level. The monolith stores a lump sum in QuickBooks and reconstructs
 * margin at month-end by hand, across two systems that never agree.
 */
@Entity
@Table(name = "invoice_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvoiceLine {

    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "buy_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal buyAmount;

    @Column(name = "sell_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal sellAmount;

    @Column(name = "quantity", nullable = false, precision = 12, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit", length = 16)
    private String unit;

    static InvoiceLine of(
            Invoice invoice, int lineNumber, String description,
            BigDecimal buyAmount, BigDecimal sellAmount, BigDecimal quantity, String unit) {
        InvoiceLine line = new InvoiceLine();
        line.id = UUID.randomUUID();
        line.invoice = invoice;
        line.lineNumber = lineNumber;
        line.description = description;
        line.buyAmount = buyAmount;
        line.sellAmount = sellAmount;
        line.quantity = quantity;
        line.unit = unit;
        return line;
    }

    public BigDecimal extendedSell() {
        return sellAmount.multiply(quantity);
    }

    public BigDecimal extendedBuy() {
        return buyAmount.multiply(quantity);
    }

    public BigDecimal margin() {
        return extendedSell().subtract(extendedBuy());
    }
}
