package com.eazyfreight.quote;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * @param expired true when this rate has lapsed as of the lookup date. Expired
 *                rates are returned rather than hidden, so operations can see that
 *                the lane is stale instead of quietly pricing from it.
 */
public record RateResponse(
        UUID id,
        UUID laneId,
        UUID carrierId,
        BigDecimal buyRate,
        String currency,
        RateType rateType,
        ChargeUnit unit,
        Integer transitDays,
        LocalDate validFrom,
        LocalDate validUntil,
        boolean expired,
        List<SurchargeResponse> surcharges
) {
    public static RateResponse fromEntity(Rate rate, LocalDate asOf) {
        return new RateResponse(
                rate.getId(),
                rate.getLaneId(),
                rate.getCarrierId(),
                rate.getBuyRate(),
                rate.getCurrency(),
                rate.getRateType(),
                rate.getUnit(),
                rate.getTransitDays(),
                rate.getValidFrom(),
                rate.getValidUntil(),
                rate.isExpiredAsOf(asOf),
                rate.getSurcharges().stream().map(s -> SurchargeResponse.fromEntity(s, asOf)).toList()
        );
    }

    public record SurchargeResponse(
            UUID id,
            SurchargeType surchargeType,
            BigDecimal amount,
            String currency,
            LocalDate validFrom,
            LocalDate validUntil,
            boolean expired
    ) {
        public static SurchargeResponse fromEntity(Surcharge surcharge, LocalDate asOf) {
            return new SurchargeResponse(
                    surcharge.getId(),
                    surcharge.getSurchargeType(),
                    surcharge.getAmount(),
                    surcharge.getCurrency(),
                    surcharge.getValidFrom(),
                    surcharge.getValidUntil(),
                    surcharge.isExpiredAsOf(asOf)
            );
        }
    }
}
