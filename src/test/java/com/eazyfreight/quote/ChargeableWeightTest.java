package com.eazyfreight.quote;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The W/M and volumetric rules, tested directly. In the monolith this arithmetic
 * lived in a spreadsheet cell and could not be tested at all.
 */
class ChargeableWeightTest {

    @Test
    void volumetricCbmDividesByOneMillion() {
        // 100 x 100 x 100 cm x 2 pieces = 2,000,000 cm3 = 2 CBM
        BigDecimal cbm = ChargeableWeight.volumetricCbm(
                new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"), 2);

        assertThat(cbm).isEqualByComparingTo("2");
    }

    @Test
    void oceanLclTakesTheGreaterOfWeightEquivalentAndVolume() {
        // 5000 kg -> 5.0 CBM equivalent, against 2.0 CBM volumetric: weight wins.
        BigDecimal chargeable = ChargeableWeight.forOceanLcl(new BigDecimal("5000"), new BigDecimal("2"));
        assertThat(chargeable).isEqualByComparingTo("5");

        // 500 kg -> 0.5 CBM equivalent, against 2.0 CBM volumetric: volume wins.
        BigDecimal volumeWins = ChargeableWeight.forOceanLcl(new BigDecimal("500"), new BigDecimal("2"));
        assertThat(volumeWins).isEqualByComparingTo("2");
    }

    @Test
    void airUsesTheSixThousandDivisor() {
        // 60 x 40 x 50 cm x 1 piece = 120,000 cm3 / 6000 = 20 kg volumetric
        BigDecimal volumetric = ChargeableWeight.volumetricKg(
                new BigDecimal("60"), new BigDecimal("40"), new BigDecimal("50"), 1);
        assertThat(volumetric).isEqualByComparingTo("20");

        // Actual 15 kg vs volumetric 20 kg: the greater is charged.
        assertThat(ChargeableWeight.forAir(new BigDecimal("15"), volumetric)).isEqualByComparingTo("20");
        assertThat(ChargeableWeight.forAir(new BigDecimal("25"), volumetric)).isEqualByComparingTo("25");
    }
}
