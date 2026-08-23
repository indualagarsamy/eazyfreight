package com.eazyfreight.quote.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Rate aggregate root — a carrier buy rate for a lane, with its surcharges.
 *
 * <p>Validity is a first-class property rather than a column nobody reads. Expired
 * rates are still returned by lookups, flagged via {@link #isExpiredAsOf}, so that
 * operations can see an expired rate exists instead of silently pricing from it.
 */
@Entity
@Table(name = "rates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Rate {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "lane_id", nullable = false)
    private UUID laneId;

    @Column(name = "carrier_id", nullable = false)
    private UUID carrierId;

    @Column(name = "buy_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal buyRate;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "rate_type", nullable = false, length = 16)
    private RateType rateType;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit", nullable = false, length = 8)
    private ChargeUnit unit;

    @Column(name = "transit_days")
    private Integer transitDays;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @OneToMany(mappedBy = "rate", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Surcharge> surcharges = new ArrayList<>();

    @Builder
    private Rate(UUID laneId, UUID carrierId, BigDecimal buyRate, String currency, RateType rateType,
                 ChargeUnit unit, Integer transitDays, LocalDate validFrom, LocalDate validUntil) {
        this.laneId = laneId;
        this.carrierId = carrierId;
        this.buyRate = buyRate;
        this.currency = currency;
        this.rateType = rateType;
        this.unit = unit;
        this.transitDays = transitDays;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
    }

    public void addSurcharge(Surcharge surcharge) {
        surcharges.add(surcharge);
    }

    public List<Surcharge> getSurcharges() {
        return Collections.unmodifiableList(surcharges);
    }

    public boolean isExpiredAsOf(LocalDate asOf) {
        return validUntil.isBefore(asOf);
    }

    /** Sell rate for a given markup, e.g. 0.25 for a 25% margin over buy. */
    public BigDecimal sellRateWithMarkup(BigDecimal markupFraction) {
        return buyRate.add(buyRate.multiply(markupFraction));
    }
}
