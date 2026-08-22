package com.eazyfreight.compliance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * An ITN issued by CBP. Never deleted — an amendment supersedes it, leaving the
 * original on record with {@code active = false} and a pointer to its replacement.
 *
 * <p>Exactly one record per booking is active at a time. That invariant is enforced
 * by {@link EEIFiling}, which owns the collection.
 */
@Entity
@Table(name = "itn_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItnRecord {

    /**
     * Assigned in the factory, not by the database: supersession points one record
     * at another, and a database-generated id is still null when that link is made.
     */
    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "eei_filing_id", nullable = false)
    private EEIFiling filing;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "itn_number", nullable = false, length = 16)
    private String itnNumber;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "superseded_by_itn_id")
    private UUID supersededByItnId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "recorded_by", nullable = false, length = 64)
    private String recordedBy;

    static ItnRecord issued(
            EEIFiling filing,
            UUID bookingId,
            ItnNumber itnNumber,
            Instant issuedAt,
            Instant recordedAt,
            String recordedBy
    ) {
        ItnRecord record = new ItnRecord();
        record.id = UUID.randomUUID();
        record.filing = filing;
        record.bookingId = bookingId;
        record.itnNumber = itnNumber.value();
        record.issuedAt = issuedAt;
        record.active = true;
        record.recordedAt = recordedAt;
        record.recordedBy = recordedBy;
        return record;
    }

    /**
     * Clears the active flag ahead of its replacement being inserted. Separate from
     * {@link #supersedeBy} so the deactivation can be flushed first — see
     * ComplianceService#applyAcceptance.
     */
    void deactivate() {
        this.active = false;
    }

    void supersedeBy(UUID replacementItnId) {
        this.active = false;
        this.supersededByItnId = replacementItnId;
    }

    /** Voided when the filing itself is cancelled with CBP. */
    void void_() {
        this.active = false;
    }

    public ItnNumber toItnNumber() {
        return new ItnNumber(itnNumber);
    }
}
