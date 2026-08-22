package com.eazyfreight.alerts;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Read models for the Alerts endpoints. */
public final class AlertResponses {

    private AlertResponses() {
    }

    public record HistoryEntry(
            UUID id, int sequenceNumber, AlertHistoryAction action,
            Instant occurredAt, String actor, String notes
    ) {
        static HistoryEntry from(AlertHistory entry) {
            return new HistoryEntry(entry.getId(), entry.getSequenceNumber(), entry.getAction(),
                    entry.getOccurredAt(), entry.getActor(), entry.getNotes());
        }
    }

    public record NotificationView(
            UUID id, NotificationChannel channel, RecipientRole recipientRole,
            Instant sentAt, Instant deliveredAt, DeliveryStatus deliveryStatus,
            String failureReason, boolean simulated
    ) {
        static NotificationView from(AlertNotification notification) {
            return new NotificationView(
                    notification.getId(), notification.getChannel(),
                    notification.getRecipientRole(), notification.getSentAt(),
                    notification.getDeliveredAt(), notification.getDeliveryStatus(),
                    notification.getFailureReason(), notification.isSimulated());
        }
    }

    public record AlertView(
            UUID id,
            UUID bookingId,
            String bookingReference,
            AlertType alertType,
            String alertCode,
            AlertTrack track,
            AlertCategory category,
            AlertStatus status,
            String title,
            String message,
            String recommendedAction,
            Set<RecipientRole> recipients,
            Instant createdAt,
            Instant deadlineAt,
            Instant lastEvaluatedAt,
            boolean overdue,
            Instant acknowledgedAt,
            String acknowledgedBy,
            Instant snoozedUntil,
            String snoozedBy,
            Instant escalatedAt,
            RecipientRole escalatedTo,
            Instant resolvedAt,
            String resolvedBy,
            String resolutionReason,
            int maxSnoozeHours,
            List<HistoryEntry> history,
            List<NotificationView> notifications
    ) {
        public static AlertView from(Alert alert, Instant now) {
            return new AlertView(
                    alert.getId(), alert.getBookingId(), alert.getBookingReference(),
                    alert.getAlertType(), alert.getAlertType().code(), alert.getTrack(),
                    alert.getCategory(), alert.getStatus(), alert.getTitle(),
                    alert.getMessage(), alert.getRecommendedAction(),
                    alert.getAlertType().recipients(),
                    alert.getCreatedAt(), alert.getDeadlineAt(), alert.getLastEvaluatedAt(),
                    alert.isOverdue(now),
                    alert.getAcknowledgedAt(), alert.getAcknowledgedBy(),
                    alert.getSnoozedUntil(), alert.getSnoozedBy(),
                    alert.getEscalatedAt(), alert.getEscalatedTo(),
                    alert.getResolvedAt(), alert.getResolvedBy(), alert.getResolutionReason(),
                    alert.getCategory().maxSnoozeHours(),
                    alert.getHistory().stream().map(HistoryEntry::from).toList(),
                    alert.getNotifications().stream().map(NotificationView::from).toList());
        }
    }

    /**
     * The "what needs attention right now" view — counts by severity and by track, plus
     * the two numbers that say whether the system is being used: how many alerts nobody
     * has looked at, and how many have run past their deadline.
     */
    public record Dashboard(
            int open,
            Map<AlertCategory, Integer> byCategory,
            Map<AlertTrack, Integer> byTrack,
            int unacknowledged,
            int escalated,
            int overdue,
            int bookingsAffected
    ) {
        public static Dashboard of(List<Alert> openAlerts, Instant now) {
            Map<AlertCategory, Integer> byCategory = new java.util.EnumMap<>(AlertCategory.class);
            for (AlertCategory category : AlertCategory.values()) {
                byCategory.put(category, 0);
            }
            Map<AlertTrack, Integer> byTrack = new java.util.EnumMap<>(AlertTrack.class);
            for (AlertTrack track : AlertTrack.values()) {
                byTrack.put(track, 0);
            }
            java.util.Set<UUID> bookings = new java.util.HashSet<>();
            int unacknowledged = 0;
            int escalated = 0;
            int overdue = 0;
            for (Alert alert : openAlerts) {
                byCategory.merge(alert.getCategory(), 1, Integer::sum);
                byTrack.merge(alert.getTrack(), 1, Integer::sum);
                bookings.add(alert.getBookingId());
                if (alert.getStatus() == AlertStatus.ACTIVE) {
                    unacknowledged++;
                }
                if (alert.getStatus() == AlertStatus.ESCALATED) {
                    escalated++;
                }
                if (alert.isOverdue(now)) {
                    overdue++;
                }
            }
            return new Dashboard(openAlerts.size(), byCategory, byTrack,
                    unacknowledged, escalated, overdue, bookings.size());
        }
    }

    public record ConfigurationView(
            AlertType alertType,
            String alertCode,
            AlertTrack track,
            String title,
            AlertCategory category,
            boolean enabled,
            Integer thresholdDays,
            int escalationHours,
            Set<NotificationChannel> channels,
            int snoozeMaxHours,
            int snoozeCeilingHours,
            String customMessage,
            Set<RecipientRole> recipients
    ) {
        public static ConfigurationView from(AlertConfiguration configuration) {
            AlertType type = configuration.getAlertType();
            return new ConfigurationView(
                    type, type.code(), type.track(), type.title(), type.baseCategory(),
                    configuration.isEnabled(), configuration.getThresholdDays(),
                    configuration.getEscalationHours(), configuration.channels(),
                    configuration.getSnoozeMaxHours(), type.baseCategory().maxSnoozeHours(),
                    configuration.getCustomMessage(), type.recipients());
        }
    }
}
