package com.eazyfreight.quote;

import java.math.BigDecimal;
import java.util.UUID;

public record CargoDetailResponse(
        UUID id,
        String description,
        String hsCode,
        int pieces,
        BigDecimal weightKg,
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm,
        BigDecimal volumetricWeightCbm,
        BigDecimal volumetricWeightKg,
        BigDecimal chargeableWeight,
        ChargeUnit chargeableUnit,
        boolean hazmat,
        boolean temperatureControlled,
        boolean oversized
) {
    public static CargoDetailResponse fromEntity(QuoteCargoDetail detail) {
        return new CargoDetailResponse(
                detail.getId(),
                detail.getDescription(),
                detail.getHsCode(),
                detail.getPieces(),
                detail.getWeightKg(),
                detail.getLengthCm(),
                detail.getWidthCm(),
                detail.getHeightCm(),
                detail.getVolumetricWeightCbm(),
                detail.getVolumetricWeightKg(),
                detail.getChargeableWeight(),
                detail.getChargeableUnit(),
                detail.isHazmat(),
                detail.isTemperatureControlled(),
                detail.isOversized()
        );
    }
}
