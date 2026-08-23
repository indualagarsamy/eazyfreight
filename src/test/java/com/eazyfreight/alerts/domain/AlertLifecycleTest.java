package com.eazyfreight.alerts.domain;

import com.eazyfreight.alerts.event.AlertEvent;

import com.eazyfreight.exception.DomainRuleViolationException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Alert aggregate on its own — no Spring, no database.
 *
 * <p>These are the rules that make an alert system survive contact with people who are
 * busy: a cap on how long something can be silenced, a reason required to close it, and
 * a trail that records every one of those decisions.
 */
class AlertLifecycleTest {

    private static final Instant NOW = Instant.parse("2024-03-15T10:00:00Z");
    private static final UUID BOOKING = UUID.randomUUID();

    private Alert critical() {
        return Alert.raise(BOOKING, "EF-2024-00287",
                AlertType.C005_ITN_MISSING_CUTOFF_BREACHED, AlertCategory.CRITICAL,
                "No ITN and the cut-off has passed", NOW.plus(Duration.ofDays(3)), NOW);
    }

    private Alert medium() {
        return Alert.raise(BOOKING, "EF-2024-00287",
                AlertType.L001_CONTAINER_NOT_DISPATCHED, AlertCategory.MEDIUM,
                "Container not dispatched", NOW.plus(Duration.ofDays(10)), NOW);
    }

    @Test
    void raisingAnAlertRecordsItAndAnnouncesIt() {
        Alert alert = critical();

        assertThat(alert.getStatus()).isEqualTo(AlertStatus.ACTIVE);
        assertThat(alert.getTitle()).isEqualTo("ITN Not Received — Documentation Cut-off Breached");
        assertThat(alert.getRecommendedAction()).contains("Contact CBP directly");
        assertThat(alert.getHistory())
                .extracting(AlertHistory::getAction)
                .containsExactly(AlertHistoryAction.CREATED);
        assertThat(alert.pendingEvents()).hasExactlyElementsOfTypes(AlertEvent.AlertCreated.class);

        // The id is on the event, so a subscriber can act on the alert it describes.
        AlertEvent.AlertCreated created =
                (AlertEvent.AlertCreated) List.copyOf(alert.pendingEvents()).getFirst();
        assertThat(created.alertId()).isEqualTo(alert.getId());
        assertThat(created.recipients()).contains(RecipientRole.COMPLIANCE_STAFF);
    }

    @Test
    void aCriticalAlertCannotBeSnoozedPastFourHours() {
        Alert alert = critical();

        assertThatThrownBy(() -> alert.snooze("ops.jane", NOW.plus(Duration.ofHours(5)), NOW))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("cannot be snoozed for more than 4 hours");

        alert.snooze("ops.jane", NOW.plus(Duration.ofHours(4)), NOW);
        assertThat(alert.getStatus()).isEqualTo(AlertStatus.SNOOZED);
    }

    @Test
    void aMediumAlertGetsTheLongerWindow() {
        Alert alert = medium();

        alert.snooze("ops.jane", NOW.plus(Duration.ofHours(24)), NOW);

        assertThat(alert.getStatus()).isEqualTo(AlertStatus.SNOOZED);
        assertThat(alert.getSnoozedUntil()).isEqualTo(NOW.plus(Duration.ofHours(24)));
    }

    @Test
    void aSnoozeEndsWhenItEndsRegardlessOfTheHour() {
        Alert alert = medium();
        alert.snooze("ops.jane", NOW.plus(Duration.ofHours(4)), NOW);

        assertThat(alert.snoozeExpired(NOW.plus(Duration.ofHours(3)))).isFalse();
        assertThat(alert.snoozeExpired(NOW.plus(Duration.ofHours(4)))).isTrue();

        // 02:00 on a Saturday is still expiry.
        Instant middleOfTheNight = Instant.parse("2024-03-16T02:00:00Z");
        alert.reactivate(middleOfTheNight);
        assertThat(alert.getStatus()).isEqualTo(AlertStatus.ACTIVE);
        assertThat(alert.getSnoozedUntil()).isNull();
    }

    @Test
    void escalationRaisesTheCategoryAndNamesWhoItWentTo() {
        Alert alert = medium();

        alert.escalate(RecipientRole.OPERATIONS_MANAGEMENT, AlertCategory.HIGH,
                NOW.plus(Duration.ofHours(24)));

        assertThat(alert.getStatus()).isEqualTo(AlertStatus.ESCALATED);
        assertThat(alert.getCategory()).isEqualTo(AlertCategory.HIGH);
        assertThat(alert.getEscalatedTo()).isEqualTo(RecipientRole.OPERATIONS_MANAGEMENT);
    }

    @Test
    void escalationNeverQuietlyDowngradesAnAlert() {
        Alert alert = critical();

        alert.escalate(RecipientRole.MANAGEMENT, AlertCategory.MEDIUM,
                NOW.plus(Duration.ofHours(4)));

        assertThat(alert.getCategory()).isEqualTo(AlertCategory.CRITICAL);
    }

    @Test
    void anAcknowledgedAlertStopsEscalatingButStaysOpen() {
        Alert alert = medium();
        assertThat(alert.escalationDue(NOW.plus(Duration.ofHours(24)))).isTrue();

        alert.acknowledge("ops.jane", NOW.plus(Duration.ofHours(1)));

        assertThat(alert.getStatus()).isEqualTo(AlertStatus.ACKNOWLEDGED);
        assertThat(alert.getStatus().isOpen()).isTrue();
        assertThat(alert.escalationDue(NOW.plus(Duration.ofDays(3)))).isFalse();
    }

    @Test
    void closingAnAlertWithoutSayingWhyIsRefused() {
        Alert alert = medium();

        assertThatThrownBy(() -> alert.resolve("ops.jane", "  ", null, NOW))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("resolution reason is required");
    }

    @Test
    void resolvingCarriesTheEventThatClearedTheCondition() {
        Alert alert = critical();
        UUID itnEvent = UUID.randomUUID();

        alert.resolve("SYSTEM", "ITNNumberReceived", itnEvent, NOW.plus(Duration.ofHours(6)));

        assertThat(alert.getStatus()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(alert.getResolutionReason()).isEqualTo("ITNNumberReceived");

        AlertEvent.AlertResolved resolved = alert.pendingEvents().stream()
                .filter(AlertEvent.AlertResolved.class::isInstance)
                .map(AlertEvent.AlertResolved.class::cast)
                .findFirst().orElseThrow();
        assertThat(resolved.resolvingEventId()).isEqualTo(itnEvent);
        assertThat(resolved.automatic()).isTrue();
    }

    @Test
    void aResolvedAlertIsFinished() {
        Alert alert = medium();
        alert.resolve("SYSTEM", "ConditionCleared", null, NOW);

        assertThatThrownBy(() -> alert.acknowledge("ops.jane", NOW))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("cannot be acknowledged");
    }

    @Test
    void theHistoryKeepsEveryStepInOrder() {
        Alert alert = medium();
        alert.snooze("ops.jane", NOW.plus(Duration.ofHours(4)), NOW);
        alert.reactivate(NOW.plus(Duration.ofHours(4)));
        alert.acknowledge("ops.jane", NOW.plus(Duration.ofHours(5)));
        alert.resolve("ops.jane", "Truck booked by phone", null, NOW.plus(Duration.ofHours(6)));

        assertThat(alert.getHistory())
                .extracting(AlertHistory::getAction)
                .containsExactly(
                        AlertHistoryAction.CREATED,
                        AlertHistoryAction.SNOOZED,
                        AlertHistoryAction.REACTIVATED,
                        AlertHistoryAction.ACKNOWLEDGED,
                        AlertHistoryAction.RESOLVED);
        assertThat(alert.getHistory())
                .extracting(AlertHistory::getSequenceNumber)
                .containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void reinstatementMovesTheDeadlineWithTheVessel() {
        Alert alert = medium();
        Instant newEtd = Instant.parse("2024-04-02T00:00:00Z");

        alert.recalculateDeadline(newEtd, AlertCategory.MEDIUM, NOW.plus(Duration.ofHours(2)));

        assertThat(alert.getDeadlineAt()).isEqualTo(newEtd);
        assertThat(alert.getHistory())
                .extracting(AlertHistory::getAction)
                .contains(AlertHistoryAction.DEADLINE_RECALCULATED);
    }

    @Test
    void theSameQuestionGetsMoreSeriousAsTheEtdCloses() {
        AlertType loading = AlertType.L003_CUSTOMER_LOADING_OVERDUE;

        assertThat(loading.categoryAt(7)).isEqualTo(AlertCategory.MEDIUM);
        assertThat(loading.categoryAt(3)).isEqualTo(AlertCategory.CRITICAL);
        assertThat(loading.categoryAt(null)).isEqualTo(AlertCategory.MEDIUM);
    }
}
