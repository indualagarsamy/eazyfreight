package com.eazyfreight.alerts;

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
 * A single delivery attempt down a single channel to a single role.
 *
 * <p>Recorded separately from the alert because delivery can fail independently of the
 * alert being correct. The specification is explicit about this: if email delivery
 * fails, the in-app notification still stands and the alert is still live. Folding
 * delivery state into the alert would make a bounced email look like a resolved
 * problem.
 *
 * <p><strong>Nothing here sends anything.</strong> See {@link NotificationDispatcher}.
 */
@Entity
@Table(name = "alert_notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlertNotification {

    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "alert_id", nullable = false)
    private Alert alert;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 16)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_role", nullable = false, length = 32)
    private RecipientRole recipientRole;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 16)
    private DeliveryStatus deliveryStatus;

    @Column(name = "failure_reason", length = 256)
    private String failureReason;

    /** True when the record describes a simulated send rather than a real one. */
    @Column(name = "simulated", nullable = false)
    private boolean simulated;

    static AlertNotification sent(Alert alert, NotificationChannel channel,
                                  RecipientRole role, Instant sentAt, boolean simulated) {
        AlertNotification notification = new AlertNotification();
        notification.id = UUID.randomUUID();
        notification.alert = alert;
        notification.channel = channel;
        notification.recipientRole = role;
        notification.sentAt = sentAt;
        notification.deliveryStatus = DeliveryStatus.PENDING;
        notification.simulated = simulated;
        return notification;
    }

    void markDelivered(Instant now) {
        this.deliveryStatus = DeliveryStatus.DELIVERED;
        this.deliveredAt = now;
    }

    void markFailed(String reason) {
        this.deliveryStatus = DeliveryStatus.FAILED;
        this.failureReason = reason;
    }
}
