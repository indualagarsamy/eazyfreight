package com.eazyfreight.alerts;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Records that a notification went out. It does not send one.
 *
 * <p>There is no mail server, no SMS gateway, and no configuration that would introduce
 * either. Alerts name real people's roles and quote real booking references, and a
 * workshop system that could put those into somebody's inbox is a workshop system that
 * eventually will. The delivery record is real — channel, recipient role, timestamp,
 * status — because the domain needs to know a notification was attempted. What is
 * simulated is the transmission, and every row says so in its {@code simulated} column.
 *
 * <p>The in-app channel is not simulated in any meaningful sense: the alert appears in
 * the alert list, which is the whole of what in-app delivery means here.
 */
@Component
@Slf4j
public class NotificationDispatcher {

    private final Clock clock;

    public NotificationDispatcher(Clock clock) {
        this.clock = clock;
    }

    @PostConstruct
    void announce() {
        log.warn("Alert notifications are SIMULATED. No email or SMS leaves this process; "
                + "delivery records are written locally and marked simulated.");
    }

    /**
     * Notes a delivery for each configured channel and recipient role.
     *
     * <p>In-app is marked delivered immediately because it is: the alert is in the list.
     * Email and SMS are marked delivered too, but as simulations — writing them as
     * PENDING would leave a queue nothing drains, which reads as a broken system rather
     * than an absent one.
     */
    public void dispatch(Alert alert, Iterable<NotificationChannel> channels) {
        for (NotificationChannel channel : channels) {
            if (channel != NotificationChannel.IN_APP
                    && !alert.getCategory().ignoresBusinessHours()
                    && !withinBusinessHours()) {
                // Business rule 9: only Critical alerts interrupt out of hours. The
                // notification is not lost — the next sweep picks it up in the morning.
                continue;
            }
            for (RecipientRole role : alert.getAlertType().recipients()) {
                AlertNotification notification = alert.recordNotification(
                        channel, role, clock.instant(), channel != NotificationChannel.IN_APP);
                notification.markDelivered(clock.instant());
            }
        }
    }

    private boolean withinBusinessHours() {
        int hour = java.time.LocalTime.now(clock).getHour();
        return hour >= 8 && hour < 18;
    }
}
