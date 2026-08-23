package com.eazyfreight.alerts.event;

import com.eazyfreight.alerts.domain.AlertCategory;
import com.eazyfreight.alerts.domain.AlertType;
import com.eazyfreight.alerts.domain.NotificationChannel;
import com.eazyfreight.alerts.domain.RecipientRole;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** What the Alerts context tells the rest of the system. */
public sealed interface AlertEvent {

    record AlertCreated(
            UUID alertId, UUID bookingId, String bookingReference, AlertType alertType,
            AlertCategory category, String title, String message, String recommendedAction,
            Instant deadlineAt, Set<RecipientRole> recipients, Instant occurredAt
    ) implements AlertEvent {
    }

    record AlertNotificationSent(
            UUID alertId, UUID bookingId, AlertType alertType,
            NotificationChannel channel, RecipientRole recipient, Instant occurredAt
    ) implements AlertEvent {
    }

    record AlertAcknowledged(
            UUID alertId, UUID bookingId, AlertType alertType, String actor, Instant occurredAt
    ) implements AlertEvent {
    }

    record AlertSnoozed(
            UUID alertId, UUID bookingId, AlertType alertType, String actor,
            Instant until, Instant occurredAt
    ) implements AlertEvent {
    }

    record AlertReactivated(
            UUID alertId, UUID bookingId, AlertType alertType, Instant occurredAt
    ) implements AlertEvent {
    }

    record AlertEscalated(
            UUID alertId, UUID bookingId, String bookingReference, AlertType alertType,
            AlertCategory newCategory, RecipientRole escalatedTo,
            long hoursUnacknowledged, Instant deadlineAt, Instant occurredAt
    ) implements AlertEvent {
    }

    /**
     * One event for both automatic and manual closure, distinguished by {@code automatic}.
     * They are the same fact — this alert is over, and here is why — and splitting them
     * would make every subscriber handle two shapes of the same thing.
     */
    record AlertResolved(
            UUID alertId, UUID bookingId, String bookingReference, AlertType alertType,
            String resolutionReason, UUID resolvingEventId, boolean automatic, Instant occurredAt
    ) implements AlertEvent {
    }

    /** Raised instead of a second alert when a condition that is already open fires again. */
    record AlertDuplicateSuppressed(
            UUID existingAlertId, UUID bookingId, AlertType alertType, Instant occurredAt
    ) implements AlertEvent {
    }

    /**
     * An alert passed its hard deadline without being resolved. Business rule 5 says the
     * booking itself carries the flag from then on, so anyone opening it sees the risk
     * without having to go looking at the alert list.
     */
    record BookingFlaggedAtRisk(
            UUID bookingId, String bookingReference, UUID alertId, AlertType alertType,
            String riskReason, Instant occurredAt
    ) implements AlertEvent {
    }
}
