package com.eazyfreight.booking.domain;

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
 * Append-only record of every status change: what it moved from and to, when, who
 * did it, why, and whether the change came from a person, INTTRA, or the system.
 *
 * <p>There are no mutators. Entries are written once and never updated or deleted —
 * this is the audit trail the monolith could not produce when a customer asked why
 * their vessel changed.
 */
@Entity
@Table(name = "booking_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookingStatusHistory {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    /**
     * Position in this booking's history, starting at 1. Ordering cannot rely on
     * {@code changedAt} alone — several transitions can share a timestamp.
     */
    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 32)
    private BookingStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 32)
    private BookingStatus toStatus;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "changed_by", nullable = false, length = 64)
    private String changedBy;

    @Column(name = "reason")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private StatusChangeSource source;

    static BookingStatusHistory record(
            Booking booking,
            int sequenceNumber,
            BookingStatus fromStatus,
            BookingStatus toStatus,
            Instant changedAt,
            String changedBy,
            String reason,
            StatusChangeSource source
    ) {
        BookingStatusHistory entry = new BookingStatusHistory();
        entry.booking = booking;
        entry.sequenceNumber = sequenceNumber;
        entry.fromStatus = fromStatus;
        entry.toStatus = toStatus;
        entry.changedAt = changedAt;
        entry.changedBy = changedBy;
        entry.reason = reason;
        entry.source = source;
        return entry;
    }
}
