package com.eazyfreight.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Cargo as booked. Held in this context rather than referenced from the quote,
 * because booked figures and quoted figures diverge and both need to survive.
 */
@Entity
@Table(name = "booking_cargo_details")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PACKAGE)
public class BookingCargoDetail {

    private static final BigDecimal CBM_DIVISOR = new BigDecimal("1000000");
    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "hs_code", nullable = false, length = 16)
    private String hsCode;

    @Column(name = "pieces", nullable = false)
    private int pieces;

    @Column(name = "weight_kg", nullable = false, precision = 12, scale = 3)
    private BigDecimal weightKg;

    @Column(name = "length_cm", nullable = false, precision = 12, scale = 2)
    private BigDecimal lengthCm;

    @Column(name = "width_cm", nullable = false, precision = 12, scale = 2)
    private BigDecimal widthCm;

    @Column(name = "height_cm", nullable = false, precision = 12, scale = 2)
    private BigDecimal heightCm;

    @Column(name = "cbm", precision = 12, scale = 4)
    private BigDecimal cbm;

    /**
     * Declared value of the goods in USD.
     *
     * <p>Required for export compliance: the EEI filing carries it, and the
     * obligation to file at all turns on value exceeding $2,500 per Schedule B
     * number. Nullable in the schema because bookings created before the column
     * existed have none; required on new bookings by request validation.
     */
    @Column(name = "value_usd", precision = 14, scale = 2)
    private BigDecimal valueUsd;

    @Column(name = "is_hazmat", nullable = false)
    private boolean hazmat;

    @Column(name = "is_temperature_controlled", nullable = false)
    private boolean temperatureControlled;

    @Column(name = "is_oversized", nullable = false)
    private boolean oversized;

    @Column(name = "marks_and_numbers")
    private String marksAndNumbers;

    void assignTo(Booking booking) {
        this.booking = booking;
        this.cbm = lengthCm.multiply(widthCm, MC)
                .multiply(heightCm, MC)
                .multiply(BigDecimal.valueOf(pieces), MC)
                .divide(CBM_DIVISOR, MC);
    }
}
