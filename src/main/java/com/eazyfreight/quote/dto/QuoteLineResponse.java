package com.eazyfreight.quote.dto;

import com.eazyfreight.quote.domain.ChargeUnit;
import com.eazyfreight.quote.domain.QuoteLine;
import com.eazyfreight.quote.domain.QuoteLineType;

import java.math.BigDecimal;
import java.util.UUID;

public record QuoteLineResponse(
        UUID id,
        QuoteLineType lineType,
        String description,
        BigDecimal buyRate,
        BigDecimal sellRate,
        String currency,
        BigDecimal quantity,
        ChargeUnit unit,
        BigDecimal amount
) {
    public static QuoteLineResponse fromEntity(QuoteLine line) {
        return new QuoteLineResponse(
                line.getId(),
                line.getLineType(),
                line.getDescription(),
                line.getBuyRate(),
                line.getSellRate(),
                line.getCurrency(),
                line.getQuantity(),
                line.getUnit(),
                line.amount()
        );
    }
}
