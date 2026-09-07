package com.eazyfreight.alerts.controller;

import com.eazyfreight.alerts.domain.Alert;
import com.eazyfreight.alerts.domain.AlertConfiguration;
import com.eazyfreight.alerts.domain.AlertHistory;
import com.eazyfreight.alerts.domain.AlertNotification;
import com.eazyfreight.alerts.domain.AlertStatus;
import com.eazyfreight.alerts.model.AlertCategory;
import com.eazyfreight.alerts.model.AlertTrack;
import com.eazyfreight.alerts.model.AlertType;
import com.eazyfreight.alerts.model.AlertView;
import com.eazyfreight.alerts.model.ConfigurationView;
import com.eazyfreight.alerts.model.Dashboard;
import com.eazyfreight.alerts.model.HistoryEntry;
import com.eazyfreight.alerts.model.NotificationChannel;
import com.eazyfreight.alerts.model.NotificationView;
import com.eazyfreight.alerts.model.RecipientRole;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Converts between Alerts domain objects and the generated OpenAPI models. */
final class AlertApiMapper {

    private AlertApiMapper() {
    }

    static AlertView toView(Alert alert, Instant now) {
        AlertView view = new AlertView();
        view.setId(alert.getId());
        view.setBookingId(alert.getBookingId());
        view.setBookingReference(alert.getBookingReference());
        view.setAlertType(mapEnum(alert.getAlertType(), AlertType.class));
        view.setAlertCode(alert.getAlertType().code());
        view.setTrack(mapEnum(alert.getTrack(), AlertTrack.class));
        view.setCategory(mapEnum(alert.getCategory(), AlertCategory.class));
        view.setStatus(mapEnum(alert.getStatus(), com.eazyfreight.alerts.model.AlertStatus.class));
        view.setTitle(alert.getTitle());
        view.setMessage(alert.getMessage());
        view.setRecommendedAction(alert.getRecommendedAction());
        view.setRecipients(mapEnumSet(alert.getAlertType().recipients(), RecipientRole.class));
        view.setCreatedAt(toOffsetDateTime(alert.getCreatedAt()));
        view.setDeadlineAt(toOffsetDateTime(alert.getDeadlineAt()));
        view.setLastEvaluatedAt(toOffsetDateTime(alert.getLastEvaluatedAt()));
        view.setOverdue(alert.isOverdue(now));
        view.setAcknowledgedAt(toOffsetDateTime(alert.getAcknowledgedAt()));
        view.setAcknowledgedBy(alert.getAcknowledgedBy());
        view.setSnoozedUntil(toOffsetDateTime(alert.getSnoozedUntil()));
        view.setSnoozedBy(alert.getSnoozedBy());
        view.setEscalatedAt(toOffsetDateTime(alert.getEscalatedAt()));
        view.setEscalatedTo(mapEnum(alert.getEscalatedTo(), RecipientRole.class));
        view.setResolvedAt(toOffsetDateTime(alert.getResolvedAt()));
        view.setResolvedBy(alert.getResolvedBy());
        view.setResolutionReason(alert.getResolutionReason());
        view.setMaxSnoozeHours(alert.getCategory().maxSnoozeHours());
        view.setHistory(alert.getHistory().stream().map(AlertApiMapper::toHistoryEntry).toList());
        view.setNotifications(alert.getNotifications().stream().map(AlertApiMapper::toNotificationView).toList());
        return view;
    }

    private static HistoryEntry toHistoryEntry(AlertHistory entry) {
        HistoryEntry view = new HistoryEntry();
        view.setId(entry.getId());
        view.setSequenceNumber(entry.getSequenceNumber());
        view.setAction(mapEnum(entry.getAction(), com.eazyfreight.alerts.model.AlertHistoryAction.class));
        view.setOccurredAt(toOffsetDateTime(entry.getOccurredAt()));
        view.setActor(entry.getActor());
        view.setNotes(entry.getNotes());
        return view;
    }

    private static NotificationView toNotificationView(AlertNotification notification) {
        NotificationView view = new NotificationView();
        view.setId(notification.getId());
        view.setChannel(mapEnum(notification.getChannel(), NotificationChannel.class));
        view.setRecipientRole(mapEnum(notification.getRecipientRole(), RecipientRole.class));
        view.setSentAt(toOffsetDateTime(notification.getSentAt()));
        view.setDeliveredAt(toOffsetDateTime(notification.getDeliveredAt()));
        view.setDeliveryStatus(mapEnum(notification.getDeliveryStatus(), com.eazyfreight.alerts.model.DeliveryStatus.class));
        view.setFailureReason(notification.getFailureReason());
        view.setSimulated(notification.isSimulated());
        return view;
    }

    static Dashboard toDashboard(List<Alert> openAlerts, Instant now) {
        Map<com.eazyfreight.alerts.domain.AlertCategory, Integer> byCategory =
                new EnumMap<>(com.eazyfreight.alerts.domain.AlertCategory.class);
        for (com.eazyfreight.alerts.domain.AlertCategory category
                : com.eazyfreight.alerts.domain.AlertCategory.values()) {
            byCategory.put(category, 0);
        }
        Map<com.eazyfreight.alerts.domain.AlertTrack, Integer> byTrack =
                new EnumMap<>(com.eazyfreight.alerts.domain.AlertTrack.class);
        for (com.eazyfreight.alerts.domain.AlertTrack track
                : com.eazyfreight.alerts.domain.AlertTrack.values()) {
            byTrack.put(track, 0);
        }
        java.util.Set<java.util.UUID> bookings = new java.util.HashSet<>();
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

        Dashboard dashboard = new Dashboard();
        dashboard.setOpen(openAlerts.size());
        Map<String, Integer> byCategoryOut = new java.util.LinkedHashMap<>();
        byCategory.forEach((category, count) -> byCategoryOut.put(category.name(), count));
        dashboard.setByCategory(byCategoryOut);
        Map<String, Integer> byTrackOut = new java.util.LinkedHashMap<>();
        byTrack.forEach((track, count) -> byTrackOut.put(track.name(), count));
        dashboard.setByTrack(byTrackOut);
        dashboard.setUnacknowledged(unacknowledged);
        dashboard.setEscalated(escalated);
        dashboard.setOverdue(overdue);
        dashboard.setBookingsAffected(bookings.size());
        return dashboard;
    }

    static ConfigurationView toConfigurationView(AlertConfiguration configuration) {
        com.eazyfreight.alerts.domain.AlertType type = configuration.getAlertType();
        ConfigurationView view = new ConfigurationView();
        view.setAlertType(mapEnum(type, AlertType.class));
        view.setAlertCode(type.code());
        view.setTrack(mapEnum(type.track(), AlertTrack.class));
        view.setTitle(type.title());
        view.setCategory(mapEnum(type.baseCategory(), AlertCategory.class));
        view.setEnabled(configuration.isEnabled());
        view.setThresholdDays(configuration.getThresholdDays());
        view.setEscalationHours(configuration.getEscalationHours());
        view.setChannels(mapEnumSet(configuration.channels(), NotificationChannel.class));
        view.setSnoozeMaxHours(configuration.getSnoozeMaxHours());
        view.setSnoozeCeilingHours(type.baseCategory().maxSnoozeHours());
        view.setCustomMessage(configuration.getCustomMessage());
        view.setRecipients(mapEnumSet(type.recipients(), RecipientRole.class));
        return view;
    }

    static com.eazyfreight.alerts.domain.AlertCategory toDomain(AlertCategory category) {
        return mapEnum(category, com.eazyfreight.alerts.domain.AlertCategory.class);
    }

    static com.eazyfreight.alerts.domain.AlertTrack toDomain(AlertTrack track) {
        return mapEnum(track, com.eazyfreight.alerts.domain.AlertTrack.class);
    }

    static com.eazyfreight.alerts.domain.AlertType toDomain(AlertType alertType) {
        return mapEnum(alertType, com.eazyfreight.alerts.domain.AlertType.class);
    }

    static Set<com.eazyfreight.alerts.domain.NotificationChannel> toDomainChannels(Set<NotificationChannel> channels) {
        return mapEnumSet(channels, com.eazyfreight.alerts.domain.NotificationChannel.class);
    }

    static Instant toInstant(OffsetDateTime dateTime) {
        return dateTime == null ? null : dateTime.toInstant();
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static <S extends Enum<S>, T extends Enum<T>> T mapEnum(S source, Class<T> targetType) {
        return source == null ? null : Enum.valueOf(targetType, source.name());
    }

    private static <S extends Enum<S>, T extends Enum<T>> Set<T> mapEnumSet(Set<S> source, Class<T> targetType) {
        if (source == null) {
            return new LinkedHashSet<>();
        }
        Set<T> result = new LinkedHashSet<>();
        for (S value : source) {
            result.add(mapEnum(value, targetType));
        }
        return result;
    }
}
