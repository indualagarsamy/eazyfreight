package com.eazyfreight.quote;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record QuoteResponse(
        UUID id,
        String quoteReference,
        UUID customerId,
        QuoteStatus status,
        ShippingMode shippingMode,
        String originPortCode,
        String destinationPortCode,
        String incoterms,
        UUID selectedCarrierId,
        ScreeningStatus screeningStatus,
        String screeningReferenceId,
        LocalDate requestedEtd,
        LocalDate validFrom,
        LocalDate validUntil,
        LocalDate rateValidUntil,
        Instant createdAt,
        Instant sentAt,
        Instant acceptedAt,
        Instant declinedAt,
        Instant expiredAt,
        String declineReason,
        String notes,
        BigDecimal totalBuyRate,
        BigDecimal totalSellRate,
        BigDecimal margin,
        String currency,
        boolean spotRate,
        String specialHandling,
        boolean insuranceRequired,
        List<QuoteLineResponse> quoteLines,
        List<CargoDetailResponse> cargoDetails
) {
    public static QuoteResponse fromEntity(Quote quote) {
        return new QuoteResponse(
                quote.getId(),
                quote.getQuoteReference(),
                quote.getCustomerId(),
                quote.getStatus(),
                quote.getShippingMode(),
                quote.getOriginPortCode(),
                quote.getDestinationPortCode(),
                quote.getIncoterms(),
                quote.getSelectedCarrierId(),
                quote.getScreeningStatus(),
                quote.getScreeningReferenceId(),
                quote.getRequestedEtd(),
                quote.getValidFrom(),
                quote.getValidUntil(),
                quote.getRateValidUntil(),
                quote.getCreatedAt(),
                quote.getSentAt(),
                quote.getAcceptedAt(),
                quote.getDeclinedAt(),
                quote.getExpiredAt(),
                quote.getDeclineReason(),
                quote.getNotes(),
                quote.getTotalBuyRate(),
                quote.getTotalSellRate(),
                quote.margin(),
                quote.getCurrency(),
                quote.isSpotRate(),
                quote.getSpecialHandling(),
                quote.isInsuranceRequired(),
                quote.getQuoteLines().stream().map(QuoteLineResponse::fromEntity).toList(),
                quote.getCargoDetails().stream().map(CargoDetailResponse::fromEntity).toList()
        );
    }
}
