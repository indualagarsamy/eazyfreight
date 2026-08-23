package com.eazyfreight.quote.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Chargeable weight rules from the Quote Intake specification.
 *
 * <p>Ocean LCL is rated on W/M — the greater of actual weight expressed in CBM
 * equivalent (kg / 1000) or volumetric CBM. Air is rated on the greater of actual
 * kg or volumetric kg using the 6000 cm3/kg divisor.
 *
 * <p>In the monolith this arithmetic lived in an Excel cell. Here it is a pure
 * function with no dependency on persistence, so it can be tested directly.
 */
public final class ChargeableWeight {

    private static final BigDecimal OCEAN_CBM_DIVISOR = new BigDecimal("1000000");
    private static final BigDecimal AIR_DIVISOR = new BigDecimal("6000");
    private static final BigDecimal KG_PER_CBM_EQUIVALENT = new BigDecimal("1000");
    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);

    private ChargeableWeight() {
    }

    /** (L x W x H in cm) / 1,000,000 x pieces. */
    public static BigDecimal volumetricCbm(BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm, int pieces) {
        return lengthCm.multiply(widthCm, MC)
                .multiply(heightCm, MC)
                .multiply(BigDecimal.valueOf(pieces), MC)
                .divide(OCEAN_CBM_DIVISOR, MC);
    }

    /** (L x W x H in cm) / 6000 x pieces. */
    public static BigDecimal volumetricKg(BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm, int pieces) {
        return lengthCm.multiply(widthCm, MC)
                .multiply(heightCm, MC)
                .multiply(BigDecimal.valueOf(pieces), MC)
                .divide(AIR_DIVISOR, MC);
    }

    /** MAX(actual kg / 1000, volumetric CBM). */
    public static BigDecimal forOceanLcl(BigDecimal actualWeightKg, BigDecimal volumetricCbm) {
        return actualWeightKg.divide(KG_PER_CBM_EQUIVALENT, MC).max(volumetricCbm);
    }

    /** MAX(actual kg, volumetric kg). */
    public static BigDecimal forAir(BigDecimal actualWeightKg, BigDecimal volumetricKg) {
        return actualWeightKg.max(volumetricKg);
    }
}
