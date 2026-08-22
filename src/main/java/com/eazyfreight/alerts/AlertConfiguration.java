package com.eazyfreight.alerts;

import com.eazyfreight.exception.DomainRuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The parts of an alert definition a deployment is allowed to change.
 *
 * <p>The condition, the recipients and the escalation ladder are the firm's operating
 * rules and live in {@link AlertType}. What varies between one operation and the next
 * is how much warning they want and down which channels — a forwarder who books
 * two weeks out wants L-001 at ten days; one running same-week cargo would drown in it.
 *
 * <p>A missing row is not an error. The specification says to fall back to the system
 * default and log it, which is what {@link AlertService} does: an operation that has
 * never touched its configuration still gets every alert.
 */
@Entity
@Table(name = "alert_configurations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlertConfiguration {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, unique = true, length = 64)
    private AlertType alertType;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    /** Null means "use the type's own threshold", not "no threshold". */
    @Column(name = "threshold_days")
    private Integer thresholdDays;

    @Column(name = "escalation_hours", nullable = false)
    private int escalationHours;

    /** Comma-separated channel names — a small fixed set not worth a join table. */
    @Column(name = "notification_channels", nullable = false, length = 64)
    private String notificationChannels;

    @Column(name = "snooze_max_hours", nullable = false)
    private int snoozeMaxHours;

    @Column(name = "custom_message", length = 1024)
    private String customMessage;

    public static AlertConfiguration defaultsFor(AlertType alertType) {
        AlertConfiguration configuration = new AlertConfiguration();
        configuration.id = UUID.randomUUID();
        configuration.alertType = alertType;
        configuration.enabled = true;
        configuration.thresholdDays = alertType.thresholdDays();
        configuration.escalationHours = alertType.baseCategory().escalationHours();
        configuration.notificationChannels = defaultChannels(alertType);
        configuration.snoozeMaxHours = alertType.baseCategory().maxSnoozeHours();
        return configuration;
    }

    public void update(boolean enabled, Integer thresholdDays, int escalationHours,
                       Set<NotificationChannel> channels, int snoozeMaxHours,
                       String customMessage) {
        if (thresholdDays != null && thresholdDays <= 0) {
            throw new DomainRuleViolationException("thresholdDays must be greater than zero");
        }
        if (escalationHours <= 0) {
            throw new DomainRuleViolationException("escalationHours must be greater than zero");
        }
        if (channels == null || channels.isEmpty()) {
            throw new DomainRuleViolationException("At least one notification channel is required");
        }
        if (snoozeMaxHours > alertType.baseCategory().maxSnoozeHours()) {
            throw new DomainRuleViolationException(
                    "A %s alert cannot allow a snooze longer than %d hours"
                            .formatted(alertType.baseCategory().name().toLowerCase(),
                                    alertType.baseCategory().maxSnoozeHours()));
        }
        this.enabled = enabled;
        this.thresholdDays = thresholdDays;
        this.escalationHours = escalationHours;
        this.notificationChannels = channels.stream().map(Enum::name)
                .reduce((a, b) -> a + "," + b).orElseThrow();
        this.snoozeMaxHours = snoozeMaxHours;
        this.customMessage = customMessage;
    }

    public Set<NotificationChannel> channels() {
        Set<NotificationChannel> channels = new LinkedHashSet<>();
        Arrays.stream(notificationChannels.split(","))
                .map(String::trim).filter(value -> !value.isEmpty())
                .map(NotificationChannel::valueOf)
                .forEach(channels::add);
        return channels;
    }

    /**
     * Critical alerts add SMS on top of the default pair. Everything else is email and
     * in-app, because an SMS for a Medium alert teaches people to ignore SMS.
     */
    private static String defaultChannels(AlertType alertType) {
        return alertType.baseCategory() == AlertCategory.CRITICAL
                ? "IN_APP,EMAIL,SMS" : "IN_APP,EMAIL";
    }
}
