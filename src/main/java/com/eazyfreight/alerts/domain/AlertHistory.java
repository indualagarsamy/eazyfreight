package com.eazyfreight.alerts.domain;

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
 * One thing that happened to an alert. Append-only — business rule 7 requires the
 * whole trail to be retained for audit, so nothing here is ever updated.
 *
 * <p>Ordered by an explicit sequence number rather than the timestamp. Several entries
 * can land in the same transaction on the same clock tick — created, then notified —
 * and a timestamp sort would return them in whatever order the database felt like.
 */
@Entity
@Table(name = "alert_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlertHistory {

    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "alert_id", nullable = false)
    private Alert alert;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 32)
    private AlertHistoryAction action;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor", nullable = false, length = 64)
    private String actor;

    @Column(name = "notes", length = 512)
    private String notes;

    static AlertHistory of(Alert alert, int sequenceNumber, AlertHistoryAction action,
                           Instant occurredAt, String actor, String notes) {
        AlertHistory entry = new AlertHistory();
        entry.id = UUID.randomUUID();
        entry.alert = alert;
        entry.sequenceNumber = sequenceNumber;
        entry.action = action;
        entry.occurredAt = occurredAt;
        entry.actor = actor;
        entry.notes = notes;
        return entry;
    }
}
