package com.eazyfreight.booking;

import java.math.BigDecimal;
import java.util.UUID;

public record BookingCargoDetailResponse(
        UUID id,
        String description,
        String hsCode,
        int pieces,
        BigDecimal weightKg,
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm,
        BigDecimal cbm,
        BigDecimal valueUsd,
        boolean hazmat,
        boolean temperatureControlled,
        boolean oversized,
        String marksAndNumbers
) {
    public static BookingCargoDetailResponse fromEntity(BookingCargoDetail detail) {
        return new BookingCargoDetailResponse(
                detail.getId(),
                detail.getDescription(),
                detail.getHsCode(),
                detail.getPieces(),
                detail.getWeightKg(),
                detail.getLengthCm(),
                detail.getWidthCm(),
                detail.getHeightCm(),
                detail.getCbm(),
                detail.getValueUsd(),
                detail.isHazmat(),
                detail.isTemperatureControlled(),
                detail.isOversized(),
                detail.getMarksAndNumbers()
        );
    }
}
