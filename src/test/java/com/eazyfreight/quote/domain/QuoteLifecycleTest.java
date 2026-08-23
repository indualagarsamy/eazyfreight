package com.eazyfreight.quote.domain;

import com.eazyfreight.exception.DomainRuleViolationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the Quote state machine. No Spring context — the rules live on the
 * aggregate, so they can be tested without a database.
 */
class QuoteLifecycleTest {

    private static final LocalDate TODAY = LocalDate.of(2024, 3, 15);
    private static final Instant NOW = Instant.parse("2024-03-15T10:00:00Z");

    @Test
    void aQuoteCannotBeSentUntilScreeningClears() {
        Quote quote = draftQuote();
        priceIt(quote, TODAY.plusDays(30), TODAY.plusDays(30));

        assertThatThrownBy(() -> quote.send(TODAY, NOW))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("denied party screening");

        quote.recordScreeningCleared("SCR-1", NOW);
        quote.send(TODAY, NOW);

        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.SENT);
    }

    @Test
    void aFlaggedQuoteCanNeverBeSent() {
        Quote quote = draftQuote();
        priceIt(quote, TODAY.plusDays(30), TODAY.plusDays(30));
        quote.recordScreeningFlagged(EntityType.CONSIGNEE, "OFAC SDN List",
                new BigDecimal("0.97"), "SCR-2", NOW);

        assertThatThrownBy(() -> quote.send(TODAY, NOW))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("FLAGGED");
    }

    @Test
    void anExpiredQuoteCannotBeAccepted() {
        Quote quote = sentQuote(TODAY.plusDays(5), TODAY.plusDays(30));

        LocalDate afterExpiry = TODAY.plusDays(6);
        assertThatThrownBy(() -> quote.accept(UUID.randomUUID(), afterExpiry, NOW))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("must be re-rated");
    }

    @Test
    void anExpiredUnderlyingRateBlocksAcceptanceAndAsksForARerate() {
        // Quote still valid, but the carrier rate it was priced from has lapsed.
        Quote quote = sentQuote(TODAY.plusDays(30), TODAY.plusDays(2));

        LocalDate afterRateExpiry = TODAY.plusDays(3);
        assertThatThrownBy(() -> quote.accept(UUID.randomUUID(), afterRateExpiry, NOW))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("carrier rate expired");
    }

    @Test
    void acceptanceRecordsTheCarrierAndMargin() {
        Quote quote = sentQuote(TODAY.plusDays(30), TODAY.plusDays(30));
        UUID carrierId = UUID.randomUUID();

        quote.accept(carrierId, TODAY, NOW);

        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.ACCEPTED);
        assertThat(quote.getSelectedCarrierId()).isEqualTo(carrierId);
        assertThat(quote.getTotalBuyRate()).isEqualByComparingTo("850.00");
        assertThat(quote.getTotalSellRate()).isEqualByComparingTo("1100.00");
        assertThat(quote.margin()).isEqualByComparingTo("250.00");
    }

    @Test
    void cargoLinesCannotBeAddedAfterTheQuoteIsSent() {
        Quote quote = sentQuote(TODAY.plusDays(30), TODAY.plusDays(30));

        assertThatThrownBy(() -> quote.addCargoDetail(cargo()))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("DRAFT");
    }

    @Test
    void chargeableWeightIsDerivedWhenCargoIsAttached() {
        Quote quote = draftQuote();
        quote.addCargoDetail(cargo());

        QuoteCargoDetail detail = quote.getCargoDetails().get(0);
        // 100 x 100 x 100 cm x 2 pieces = 2 CBM; 500 kg -> 0.5 CBM equivalent.
        assertThat(detail.getVolumetricWeightCbm()).isEqualByComparingTo("2");
        assertThat(detail.getChargeableWeight()).isEqualByComparingTo("2");
        assertThat(detail.getChargeableUnit()).isEqualTo(ChargeUnit.CBM);
    }

    @Test
    void originAndDestinationMustDiffer() {
        assertThatThrownBy(() -> Quote.request("Q-2024-00001", UUID.randomUUID(), ShippingMode.OCEAN_LCL,
                "INBOM", "INBOM", "FOB", null, null, false, "USD", NOW))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("must differ");
    }

    // ----------------------------------------------------------------- fixtures

    private Quote draftQuote() {
        return Quote.request("Q-2024-00001", UUID.randomUUID(), ShippingMode.OCEAN_LCL,
                "INBOM", "SGSIN", "FOB", TODAY.plusDays(20), null, false, "USD", NOW);
    }

    private Quote sentQuote(LocalDate validUntil, LocalDate rateValidUntil) {
        Quote quote = draftQuote();
        quote.addCargoDetail(cargo());
        priceIt(quote, validUntil, rateValidUntil);
        quote.recordScreeningCleared("SCR-1", NOW);
        quote.send(TODAY, NOW);
        return quote;
    }

    private void priceIt(Quote quote, LocalDate validUntil, LocalDate rateValidUntil) {
        quote.build(
                List.of(QuoteLine.builder()
                        .lineType(QuoteLineType.BASE_FREIGHT)
                        .description("Ocean Freight")
                        .buyRate(new BigDecimal("850.00"))
                        .sellRate(new BigDecimal("1100.00"))
                        .currency("USD")
                        .quantity(BigDecimal.ONE)
                        .unit(ChargeUnit.CBM)
                        .build()),
                TODAY, validUntil, rateValidUntil, false, null, NOW);
    }

    private QuoteCargoDetail cargo() {
        return QuoteCargoDetail.builder()
                .description("Machine parts")
                .hsCode("8471.30.0100")
                .pieces(2)
                .weightKg(new BigDecimal("500"))
                .lengthCm(new BigDecimal("100"))
                .widthCm(new BigDecimal("100"))
                .heightCm(new BigDecimal("100"))
                .hazmat(false)
                .temperatureControlled(false)
                .oversized(false)
                .build();
    }
}
