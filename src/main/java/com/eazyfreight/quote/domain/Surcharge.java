package com.eazyfreight.quote.domain;

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
import java.time.LocalDate;
import java.util.UUID;

/**
 * A lane surcharge. A null carrierId means the surcharge applies to every carrier
 * on the lane.
 */
@Entity
@Table(name = "surcharges")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Surcharge {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "rate_id")
    private Rate rate;

    @Column(name = "lane_id", nullable = false)
    private UUID laneId;

    @Column(name = "carrier_id")
    private UUID carrierId;

    @Enumerated(EnumType.STRING)
    @Column(name = "surcharge_type", nullable = false, length = 32)
    private SurchargeType surchargeType;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    public boolean isExpiredAsOf(LocalDate asOf) {
        return validUntil.isBefore(asOf);
    }
}
