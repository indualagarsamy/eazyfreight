package com.eazyfreight.compliance;

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
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit log for a filing. Never updated, never deleted, retained five
 * years per CBP regulation.
 *
 * <p>Ordered by an explicit sequence rather than by timestamp, because several
 * entries can share an instant and the order they happened in is not recoverable
 * from the clock.
 */
@Entity
@Table(name = "eei_filing_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EEIFilingHistory {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "eei_filing_id", nullable = false)
    private EEIFiling filing;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 16)
    private FilingStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 16)
    private FilingStatus toStatus;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor", nullable = false, length = 64)
    private String actor;

    @Column(name = "detail")
    private String detail;

    static EEIFilingHistory record(
            EEIFiling filing,
            int sequenceNumber,
            FilingStatus fromStatus,
            FilingStatus toStatus,
            Instant occurredAt,
            String actor,
            String detail
    ) {
        EEIFilingHistory entry = new EEIFilingHistory();
        entry.filing = filing;
        entry.sequenceNumber = sequenceNumber;
        entry.fromStatus = fromStatus;
        entry.toStatus = toStatus;
        entry.occurredAt = occurredAt;
        entry.actor = actor;
        entry.detail = detail;
        return entry;
    }
}
