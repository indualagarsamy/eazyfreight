package com.eazyfreight.alerts.repository;

import com.eazyfreight.alerts.domain.Alert;
import com.eazyfreight.alerts.domain.AlertType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID> {

    /**
     * The duplicate-suppression lookup. Keyed on anything that is not resolved, so an
     * acknowledged or snoozed alert still blocks a second one for the same condition —
     * business rule 6.
     */
    @Query("""
            select a from Alert a
            where a.bookingId = :bookingId and a.alertType = :alertType
              and a.status <> com.eazyfreight.alerts.domain.AlertStatus.RESOLVED
            """)
    Optional<Alert> findOpen(UUID bookingId, AlertType alertType);

    List<Alert> findByBookingIdOrderByCreatedAtDesc(UUID bookingId);

    @Query("""
            select a from Alert a
            where a.status <> com.eazyfreight.alerts.domain.AlertStatus.RESOLVED
            order by a.category, a.createdAt
            """)
    List<Alert> findOpenAlerts();

    @Query("""
            select a from Alert a
            where a.bookingId = :bookingId
              and a.status <> com.eazyfreight.alerts.domain.AlertStatus.RESOLVED
            """)
    List<Alert> findOpenByBooking(UUID bookingId);

    @Query("""
            select a from Alert a
            where a.status = com.eazyfreight.alerts.domain.AlertStatus.SNOOZED
              and a.snoozedUntil <= :now
            """)
    List<Alert> findSnoozeExpired(Instant now);

    @Query("""
            select a from Alert a
            where a.status = com.eazyfreight.alerts.domain.AlertStatus.ACTIVE
            """)
    List<Alert> findActive();

    @Query("""
            select a from Alert a
            where a.status <> com.eazyfreight.alerts.domain.AlertStatus.RESOLVED
              and a.deadlineAt is not null and a.deadlineAt < :now
            order by a.deadlineAt
            """)
    List<Alert> findOverdue(Instant now);
}
