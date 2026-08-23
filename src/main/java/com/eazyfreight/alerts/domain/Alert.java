package com.eazyfreight.alerts.domain;

import com.eazyfreight.alerts.event.AlertEvent;

import com.eazyfreight.exception.DomainRuleViolationException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.AbstractAggregateRoot;
import org.springframework.data.domain.Persistable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Something that needs attention on a booking, and the record of what was done about it.
 *
 * <p>Three rules from the specification live here rather than in the service, because
 * they are the reasons an alert is a thing at all rather than a row in a report:
 *
 * <ul>
 *   <li><b>Snoozing is capped by category.</b> A Critical alert cannot be pushed out
 *       more than four hours. Without the cap, "snooze" is a delete button with better
 *       manners — which is exactly how alert systems die.</li>
 *   <li><b>Resolution needs a reason.</b> Every close, automatic or manual, records why.
 *       An alert that vanished without one leaves nobody able to say whether the
 *       underlying problem was fixed or merely dismissed.</li>
 *   <li><b>History is append-only.</b> Acknowledgements, snoozes, escalations and the
 *       resolution are all kept. This is the audit trail the monolith does not have, so
 *       nobody there can reconstruct who knew what and when.</li>
 * </ul>
 *
 * <p>The category is stored, not derived, even though {@link AlertType#categoryAt}
 * could recompute it. An alert that fired as Medium and was escalated to Critical
 * should read as Critical afterwards regardless of what the ETD later becomes — the
 * escalation happened, and rewriting it out of the record would be a lie.
 */
@Entity
@Table(name = "alerts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Alert extends AbstractAggregateRoot<Alert> implements Persistable<UUID> {

    private static final int MESSAGE_LIMIT = 1024;

    @Id
    private UUID id;

    @Transient
    private boolean isNew = true;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    /** Denormalised so an alert reads correctly without joining the booking. */
    @Column(name = "booking_reference", nullable = false, length = 32)
    private String bookingReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 64)
    private AlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(name = "track", nullable = false, length = 16)
    private AlertTrack track;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 16)
    private AlertCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AlertStatus status;

    @Column(name = "title", nullable = false, length = 128)
    private String title;

    @Column(name = "message", nullable = false, length = MESSAGE_LIMIT)
    private String message;

    @Column(name = "recommended_action", nullable = false, length = 512)
    private String recommendedAction;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** The hard deadline for action, where the condition has one. */
    @Column(name = "deadline_at")
    private Instant deadlineAt;

    /**
     * When the scheduler last confirmed the condition still holds. Used to show that a
     * quiet alert is quiet because nothing changed, not because evaluation stopped.
     */
    @Column(name = "last_evaluated_at", nullable = false)
    private Instant lastEvaluatedAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "acknowledged_by", length = 64)
    private String acknowledgedBy;

    @Column(name = "snoozed_until")
    private Instant snoozedUntil;

    @Column(name = "snoozed_by", length = 64)
    private String snoozedBy;

    @Column(name = "escalated_at")
    private Instant escalatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "escalated_to", length = 32)
    private RecipientRole escalatedTo;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by", length = 64)
    private String resolvedBy;

    @Column(name = "resolution_reason", length = 128)
    private String resolutionReason;

    @OneToMany(mappedBy = "alert", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("sequenceNumber")
    private List<AlertHistory> history = new ArrayList<>();

    @OneToMany(mappedBy = "alert", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("sentAt")
    private List<AlertNotification> notifications = new ArrayList<>();

    // ---------------------------------------------------------------- raising

    /**
     * Raises an alert. The id is assigned here so {@code AlertCreated} carries the real
     * one rather than a null the subscriber cannot use.
     */
    public static Alert raise(UUID bookingId, String bookingReference, AlertType alertType,
                              AlertCategory category, String message, Instant deadlineAt,
                              Instant now) {
        Alert alert = new Alert();
        alert.id = UUID.randomUUID();
        alert.bookingId = bookingId;
        alert.bookingReference = bookingReference;
        alert.alertType = alertType;
        alert.track = alertType.track();
        alert.category = category;
        alert.status = AlertStatus.ACTIVE;
        alert.title = alertType.title();
        alert.message = truncate(message);
        alert.recommendedAction = alertType.recommendedAction();
        alert.createdAt = now;
        alert.deadlineAt = deadlineAt;
        alert.lastEvaluatedAt = now;
        alert.record(AlertHistoryAction.CREATED, now, "SYSTEM", alertType.code());
        alert.registerEvent(new AlertEvent.AlertCreated(
                alert.id, bookingId, bookingReference, alertType, category,
                alertType.title(), alert.message, alertType.recommendedAction(),
                deadlineAt, alertType.recipients(), now));
        return alert;
    }

    // ------------------------------------------------------------- responding

    public void acknowledge(String actor, Instant now) {
        requireActionable("acknowledged");
        this.status = AlertStatus.ACKNOWLEDGED;
        this.acknowledgedAt = now;
        this.acknowledgedBy = actor;
        this.snoozedUntil = null;
        record(AlertHistoryAction.ACKNOWLEDGED, now, actor, null);
        registerEvent(new AlertEvent.AlertAcknowledged(id, bookingId, alertType, actor, now));
    }

    /**
     * Silences the alert until {@code until}.
     *
     * <p>The cap is the point. A Critical alert may be snoozed for four hours and no
     * longer, because the deadline it is warning about does not move when someone finds
     * it inconvenient.
     */
    public void snooze(String actor, Instant until, Instant now) {
        requireActionable("snoozed");
        if (until == null || !until.isAfter(now)) {
            throw new DomainRuleViolationException("A snooze must end in the future");
        }
        int cap = category.maxSnoozeHours();
        if (Duration.between(now, until).compareTo(Duration.ofHours(cap)) > 0) {
            throw new DomainRuleViolationException(
                    "A %s alert cannot be snoozed for more than %d hours"
                            .formatted(category.name().toLowerCase(), cap));
        }
        this.status = AlertStatus.SNOOZED;
        this.snoozedUntil = until;
        this.snoozedBy = actor;
        record(AlertHistoryAction.SNOOZED, now, actor, "Until " + until);
        registerEvent(new AlertEvent.AlertSnoozed(id, bookingId, alertType, actor, until, now));
    }

    /**
     * Brings a snoozed alert back. Business rule 3: the snooze expires when it expires,
     * regardless of business hours — the condition did not wait for office hours either.
     */
    public void reactivate(Instant now) {
        if (status != AlertStatus.SNOOZED) {
            return;
        }
        this.status = AlertStatus.ACTIVE;
        this.snoozedUntil = null;
        record(AlertHistoryAction.REACTIVATED, now, "SYSTEM", "Snooze expired");
        registerEvent(new AlertEvent.AlertReactivated(id, bookingId, alertType, now));
    }

    /** Whether the snooze has run out and the alert should come back. */
    public boolean snoozeExpired(Instant now) {
        return status == AlertStatus.SNOOZED && snoozedUntil != null && !snoozedUntil.isAfter(now);
    }

    /**
     * Sends the alert over someone's head, and raises its category if the type says the
     * escalated form is more serious.
     */
    public void escalate(RecipientRole to, AlertCategory escalatedCategory, Instant now) {
        requireActionable("escalated");
        this.status = AlertStatus.ESCALATED;
        this.escalatedAt = now;
        this.escalatedTo = to;
        AlertCategory previous = this.category;
        if (escalatedCategory != null && escalatedCategory.ordinal() < this.category.ordinal()) {
            this.category = escalatedCategory;
        }
        record(AlertHistoryAction.ESCALATED, now, "SYSTEM",
                "To %s%s".formatted(to.label(),
                        previous == category ? "" : ", now " + category.name().toLowerCase()));
        registerEvent(new AlertEvent.AlertEscalated(
                id, bookingId, bookingReference, alertType, category, to,
                Duration.between(createdAt, now).toHours(), deadlineAt, now));
    }

    /**
     * Whether the alert has gone unanswered long enough to escalate. An acknowledged
     * alert never escalates on the clock — somebody has taken it, and hounding them is
     * how a useful alert becomes noise.
     */
    public boolean escalationDue(Instant now) {
        if (status != AlertStatus.ACTIVE) {
            return false;
        }
        return Duration.between(createdAt, now).toHours() >= category.escalationHours();
    }

    // ------------------------------------------------------------- resolution

    /**
     * Closes the alert because the condition it warned about is gone.
     *
     * <p>{@code resolvingEventId} points at the domain event that cleared it, so the
     * trail runs from "the ITN arrived" to "these four alerts closed" without anyone
     * inferring the link.
     */
    public void resolve(String actor, String reason, UUID resolvingEventId, Instant now) {
        if (status == AlertStatus.RESOLVED) {
            return;
        }
        if (reason == null || reason.isBlank()) {
            throw new DomainRuleViolationException("A resolution reason is required");
        }
        this.status = AlertStatus.RESOLVED;
        this.resolvedAt = now;
        this.resolvedBy = actor;
        this.resolutionReason = reason;
        this.snoozedUntil = null;
        record(AlertHistoryAction.RESOLVED, now, actor, reason);
        registerEvent(new AlertEvent.AlertResolved(
                id, bookingId, bookingReference, alertType, reason,
                resolvingEventId, "SYSTEM".equals(actor), now));
    }

    // ------------------------------------------------------------ maintenance

    /**
     * Re-points a time-based alert at a new sailing.
     *
     * <p>Business rule 1. When a booking is reinstated, every ETD-relative deadline
     * moves with it. An alert still counting down to a vessel that sailed without the
     * cargo is worse than no alert: it reads as urgent when it is meaningless, and as
     * meaningless when it becomes urgent again.
     */
    public void recalculateDeadline(Instant newDeadline, AlertCategory newCategory, Instant now) {
        if (status == AlertStatus.RESOLVED) {
            return;
        }
        Instant previous = this.deadlineAt;
        this.deadlineAt = newDeadline;
        if (newCategory != null && status != AlertStatus.ESCALATED) {
            this.category = newCategory;
        }
        record(AlertHistoryAction.DEADLINE_RECALCULATED, now, "SYSTEM",
                "%s → %s".formatted(previous, newDeadline));
    }

    /** Notes that the condition was checked again and still holds. */
    public void touch(Instant now) {
        this.lastEvaluatedAt = now;
    }

    /** Raises the category in place as the ETD closes in, without an escalation. */
    public void reclassify(AlertCategory newCategory, Instant now) {
        if (newCategory == null || newCategory == this.category
                || newCategory.ordinal() >= this.category.ordinal()) {
            return;
        }
        this.category = newCategory;
        record(AlertHistoryAction.DEADLINE_RECALCULATED, now, "SYSTEM",
                "Category raised to " + newCategory.name().toLowerCase());
    }

    public AlertNotification recordNotification(NotificationChannel channel, RecipientRole role,
                                         Instant now, boolean simulated) {
        AlertNotification notification =
                AlertNotification.sent(this, channel, role, now, simulated);
        notifications.add(notification);
        record(AlertHistoryAction.NOTIFICATION_SENT, now, "SYSTEM",
                "%s to %s".formatted(channel.label(), role.label()));
        registerEvent(new AlertEvent.AlertNotificationSent(
                id, bookingId, alertType, channel, role, now));
        return notification;
    }

    // ---------------------------------------------------------------- queries

    public boolean isOverdue(Instant now) {
        return status.isOpen() && deadlineAt != null && deadlineAt.isBefore(now);
    }

    public List<AlertHistory> getHistory() {
        return List.copyOf(history);
    }

    public List<AlertNotification> getNotifications() {
        return List.copyOf(notifications);
    }

    // ---------------------------------------------------------------- support

    private void requireActionable(String verb) {
        if (!status.isActionable()) {
            throw new DomainRuleViolationException(
                    "A %s alert cannot be %s".formatted(status.name().toLowerCase(), verb));
        }
    }

    private void record(AlertHistoryAction action, Instant now, String actor, String notes) {
        history.add(AlertHistory.of(this, history.size() + 1, action, now, actor, notes));
    }

    private static String truncate(String message) {
        if (message == null) {
            return "";
        }
        return message.length() <= MESSAGE_LIMIT ? message : message.substring(0, MESSAGE_LIMIT);
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    /** Exposes the protected accessor so tests can assert on what was published. */
    public Collection<Object> pendingEvents() {
        return domainEvents();
    }
}
