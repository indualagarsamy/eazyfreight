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
 * Cargo as described by the customer at quote time. Weights and dimensions here
 * are estimates — the Logistics track later produces actuals, which is why the
 * booking context keeps its own copy rather than pointing at this one.
 */
@Entity
@Table(name = "quote_cargo_details")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PACKAGE)
public class QuoteCargoDetail {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "quote_id", nullable = false)
    private Quote quote;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "hs_code", nullable = false)
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

    @Column(name = "volumetric_weight_cbm", precision = 12, scale = 4)
    private BigDecimal volumetricWeightCbm;

    @Column(name = "volumetric_weight_kg", precision = 12, scale = 4)
    private BigDecimal volumetricWeightKg;

    @Column(name = "chargeable_weight", precision = 12, scale = 4)
    private BigDecimal chargeableWeight;

    @Enumerated(EnumType.STRING)
    @Column(name = "chargeable_unit", length = 8)
    private ChargeUnit chargeableUnit;

    @Column(name = "is_hazmat", nullable = false)
    private boolean hazmat;

    @Column(name = "is_temperature_controlled", nullable = false)
    private boolean temperatureControlled;

    @Column(name = "is_oversized", nullable = false)
    private boolean oversized;

    void assignTo(Quote quote) {
        this.quote = quote;
    }

    /**
     * Derives volumetric and chargeable figures for the quote's mode. Called by the
     * root when the cargo line is attached, so the computed values can never drift
     * from the dimensions they were derived from.
     */
    void computeChargeableWeight(ShippingMode mode) {
        this.volumetricWeightCbm = ChargeableWeight.volumetricCbm(lengthCm, widthCm, heightCm, pieces);
        this.volumetricWeightKg = ChargeableWeight.volumetricKg(lengthCm, widthCm, heightCm, pieces);

        switch (mode) {
            case OCEAN_LCL -> {
                this.chargeableWeight = ChargeableWeight.forOceanLcl(weightKg, volumetricWeightCbm);
                this.chargeableUnit = ChargeUnit.CBM;
            }
            case AIR -> {
                this.chargeableWeight = ChargeableWeight.forAir(weightKg, volumetricWeightKg);
                this.chargeableUnit = ChargeUnit.KG;
            }
            case OCEAN_FCL -> {
                // FCL is rated per TEU, not by chargeable weight.
                this.chargeableWeight = null;
                this.chargeableUnit = ChargeUnit.TEU;
            }
        }
    }
}
