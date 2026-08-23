package com.eazyfreight.logistics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A seal applied to the container. Append-only: when customs cuts a seal and fits
 * a new one, the original is deactivated with its reason and a pointer to the
 * successor, never overwritten.
 *
 * <p>In the monolith this was a single {@code SealNo} column. A customs inspection
 * silently destroyed the number that had been on the container up to that point,
 * and with it any way to answer which seal was intact when.
 */
@Entity
@Table(name = "seal_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SealRecord {

    /** Assigned in the factory so a replacement can be linked before either is persisted. */
    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "container_assignment_id", nullable = false)
    private ContainerAssignment assignment;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "seal_number", nullable = false, length = 64)
    private String sealNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "seal_source", nullable = false, length = 24)
    private SealSource sealSource;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "deactivation_reason", length = 32)
    private SealDeactivationReason deactivationReason;

    @Column(name = "replaced_by_seal_id")
    private UUID replacedBySealId;

    @Column(name = "recorded_by", nullable = false, length = 64)
    private String recordedBy;

    static SealRecord issued(
            ContainerAssignment assignment, UUID bookingId, String sealNumber,
            SealSource source, Instant issuedAt, String recordedBy) {
        SealRecord seal = new SealRecord();
        seal.id = UUID.randomUUID();
        seal.assignment = assignment;
        seal.bookingId = bookingId;
        seal.sealNumber = sealNumber;
        seal.sealSource = source;
        seal.active = true;
        seal.issuedAt = issuedAt;
        seal.recordedBy = recordedBy;
        return seal;
    }

    /** Stands the seal down ahead of its replacement being written. */
    void deactivate(SealDeactivationReason reason, Instant at) {
        this.active = false;
        this.deactivationReason = reason;
        this.deactivatedAt = at;
    }

    void replacedBy(UUID successorSealId) {
        this.replacedBySealId = successorSealId;
    }
}
