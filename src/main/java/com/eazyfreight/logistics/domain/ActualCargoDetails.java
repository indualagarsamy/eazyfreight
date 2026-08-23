package com.eazyfreight.logistics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Cargo as actually loaded, alongside what was booked.
 *
 * <p>Both sets are kept because the difference is what matters: actuals propagate
 * to the House BOL and the invoice, and if the EEI has already been filed on the
 * estimates, a divergence obliges an amendment to CBP.
 */
@Entity
@Table(name = "actual_cargo_details")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActualCargoDetails {

    /** Divergence beyond this fraction is treated as material. */
    private static final BigDecimal MATERIAL_VARIANCE = new BigDecimal("0.05");

    @Id
    private UUID id;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "actual_weight_kg", nullable = false, precision = 12, scale = 3)
    private BigDecimal actualWeightKg;

    @Column(name = "actual_pieces", nullable = false)
    private int actualPieces;

    @Column(name = "actual_cbm", precision = 12, scale = 4)
    private BigDecimal actualCbm;

    @Column(name = "booked_weight_kg", nullable = false, precision = 12, scale = 3)
    private BigDecimal bookedWeightKg;

    @Column(name = "booked_pieces", nullable = false)
    private int bookedPieces;

    @Column(name = "booked_cbm", precision = 12, scale = 4)
    private BigDecimal bookedCbm;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "recorded_by", nullable = false, length = 64)
    private String recordedBy;

    static ActualCargoDetails record(
            UUID bookingId,
            BigDecimal actualWeightKg, int actualPieces, BigDecimal actualCbm,
            BigDecimal bookedWeightKg, int bookedPieces, BigDecimal bookedCbm,
            Instant at, String actor) {
        ActualCargoDetails details = new ActualCargoDetails();
        details.id = UUID.randomUUID();
        details.bookingId = bookingId;
        details.actualWeightKg = actualWeightKg;
        details.actualPieces = actualPieces;
        details.actualCbm = actualCbm;
        details.bookedWeightKg = bookedWeightKg;
        details.bookedPieces = bookedPieces;
        details.bookedCbm = bookedCbm;
        details.recordedAt = at;
        details.recordedBy = actor;
        return details;
    }

    /** True when actuals diverge enough from the booking to oblige an EEI amendment. */
    public boolean divergesMaterially() {
        if (actualPieces != bookedPieces) {
            return true;
        }
        if (bookedWeightKg == null || bookedWeightKg.signum() == 0) {
            return false;
        }
        BigDecimal variance = actualWeightKg.subtract(bookedWeightKg).abs()
                .divide(bookedWeightKg, 6, java.math.RoundingMode.HALF_UP);
        return variance.compareTo(MATERIAL_VARIANCE) > 0;
    }

    public BigDecimal weightVarianceKg() {
        return actualWeightKg.subtract(bookedWeightKg);
    }
}
