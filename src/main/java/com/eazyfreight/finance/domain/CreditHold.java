package com.eazyfreight.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A customer stopped from taking new bookings while an invoice is unpaid.
 *
 * <p>The monolith has no such mechanism, so a customer who has not paid for the
 * last three shipments can book a fourth.
 */
@Entity
@Table(name = "credit_holds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditHold {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "triggering_booking_id")
    private UUID triggeringBookingId;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "placed_at", nullable = false)
    private Instant placedAt;

    @Column(name = "placed_by", nullable = false, length = 64)
    private String placedBy;

    @Column(name = "lifted_at")
    private Instant liftedAt;

    @Column(name = "lifted_by", length = 64)
    private String liftedBy;

    public static CreditHold place(
            UUID customerId, UUID triggeringBookingId, String reason, Instant now, String actor) {
        CreditHold hold = new CreditHold();
        hold.id = UUID.randomUUID();
        hold.customerId = customerId;
        hold.triggeringBookingId = triggeringBookingId;
        hold.reason = reason;
        hold.active = true;
        hold.placedAt = now;
        hold.placedBy = actor;
        return hold;
    }

    public void lift(Instant now, String actor) {
        this.active = false;
        this.liftedAt = now;
        this.liftedBy = actor;
    }
}
