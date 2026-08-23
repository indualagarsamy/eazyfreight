package com.eazyfreight.alerts.service;

import com.eazyfreight.alerts.domain.Alert;
import com.eazyfreight.alerts.domain.AlertConfiguration;
import com.eazyfreight.alerts.domain.AlertFacts;
import com.eazyfreight.alerts.domain.AlertType;
import com.eazyfreight.alerts.domain.NotificationChannel;
import com.eazyfreight.alerts.domain.RecipientRole;
import com.eazyfreight.alerts.event.AlertEvent;
import com.eazyfreight.alerts.exception.AlertNotFoundException;
import com.eazyfreight.alerts.repository.AlertConfigurationRepository;
import com.eazyfreight.alerts.repository.AlertRepository;
import com.eazyfreight.booking.domain.Booking;
import com.eazyfreight.booking.domain.BookingStatus;
import com.eazyfreight.booking.repository.BookingRepository;
import com.eazyfreight.exception.BookingNotFoundException;

import com.eazyfreight.exception.DomainRuleViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The Alerts application service: evaluation, the human responses, and resolution.
 *
 * <p>Evaluation is idempotent by construction. Running it twice on unchanged facts
 * produces no second alert and no second notification, because every raise goes through
 * {@link #raise}, which looks for an open alert of the same type on the same booking
 * first. That is business rule 6, and it is what makes a thirty-minute sweep safe to run
 * every thirty minutes.
 *
 * <p>Resolution runs the same way and in the same pass: an alert whose condition no
 * longer holds is closed with the reason it stopped holding. Nothing waits for a
 * listener to notice, so an alert cannot outlive its cause just because an event was
 * dropped.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private static final List<BookingStatus> WATCHED = List.of(
            BookingStatus.SUBMITTED_TO_CARRIER,
            BookingStatus.COUNTER_OFFER_RECEIVED,
            BookingStatus.CONFIRMED_BY_CARRIER,
            BookingStatus.CUSTOMER_CONFIRMED,
            BookingStatus.VESSEL_OVERBOOKED);

    private final AlertRepository repository;
    private final AlertConfigurationRepository configurationRepository;
    private final BookingRepository bookingRepository;
    private final AlertFactsAssembler assembler;
    private final NotificationDispatcher dispatcher;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    // ------------------------------------------------------------ evaluation

    /** Sweeps every booking that could still have a condition worth raising. */
    @Transactional
    public int evaluateAll() {
        int changes = 0;
        for (Booking booking : bookingRepository.findByStatusIn(WATCHED)) {
            changes += evaluate(booking);
        }
        changes += reactivateExpiredSnoozes();
        changes += escalateStale();
        return changes;
    }

    @Transactional
    public int evaluateBooking(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new com.eazyfreight.exception.BookingNotFoundException(bookingId));
        return evaluate(booking);
    }

    private int evaluate(Booking booking) {
        AlertFacts facts = assembler.assemble(booking);
        Instant now = facts.now();
        Map<AlertType, Alert> open = new EnumMap<>(AlertType.class);
        repository.findOpenByBooking(booking.getId())
                .forEach(alert -> open.put(alert.getAlertType(), alert));

        int changes = 0;
        for (AlertType type : AlertRules.polledTypes()) {
            boolean holds = AlertRules.holds(type, facts);
            Alert existing = open.get(type);

            if (holds && existing == null) {
                raise(type, facts, AlertRules.message(type, facts), AlertRules.deadline(type, facts));
                changes++;
            } else if (holds) {
                existing.touch(now);
                existing.reclassify(type.categoryAt(facts.daysToEtd()), now);
            } else if (existing != null) {
                existing.resolve("SYSTEM", "ConditionCleared", null, now);
                repository.save(existing);
                changes++;
            }
        }
        return changes;
    }

    /**
     * Creates an alert unless one for the same condition is already open.
     *
     * <p>The suppression is the difference between an alert system people read and one
     * they filter to a folder. A container that has not moved for a week is one problem,
     * not three hundred and thirty-six.
     */
    @Transactional
    public Optional<Alert> raise(AlertType type, AlertFacts facts, String message, Instant deadline) {
        AlertConfiguration configuration = configurationFor(type);
        if (!configuration.isEnabled()) {
            return Optional.empty();
        }

        Optional<Alert> existing = repository.findOpen(facts.bookingId(), type);
        if (existing.isPresent()) {
            existing.get().touch(facts.now());
            events.publishEvent(new AlertEvent.AlertDuplicateSuppressed(
                    existing.get().getId(), facts.bookingId(), type, facts.now()));
            return Optional.empty();
        }

        Alert alert = Alert.raise(
                facts.bookingId(), facts.bookingReference(), type,
                type.categoryAt(facts.daysToEtd()),
                configuration.getCustomMessage() == null ? message : configuration.getCustomMessage(),
                deadline, facts.now());
        dispatcher.dispatch(alert, configuration.channels());
        return Optional.of(repository.save(alert));
    }

    /** Raises an alert for a condition that leaves no trace in state — C-006 and B-003. */
    @Transactional
    public Optional<Alert> raiseFromEvent(UUID bookingId, AlertType type,
                                          String message, Instant deadline) {
        return bookingRepository.findById(bookingId)
                .flatMap(booking -> raise(type, assembler.assemble(booking), message, deadline));
    }

    // --------------------------------------------------------------- responses

    @Transactional
    public Alert acknowledge(UUID alertId, String actor) {
        Alert alert = get(alertId);
        alert.acknowledge(actor, clock.instant());
        return repository.save(alert);
    }

    @Transactional
    public Alert snooze(UUID alertId, String actor, Instant until) {
        Alert alert = get(alertId);
        alert.snooze(actor, until, clock.instant());
        return repository.save(alert);
    }

    @Transactional
    public Alert resolve(UUID alertId, String actor, String reason) {
        Alert alert = get(alertId);
        if (reason == null || reason.isBlank()) {
            throw new DomainRuleViolationException("A resolution reason is required");
        }
        alert.resolve(actor, reason, null, clock.instant());
        return repository.save(alert);
    }

    // -------------------------------------------------------- resolution paths

    /**
     * Closes every open alert of the given types for a booking, because the event named
     * in {@code reason} has cleared what they were warning about.
     */
    @Transactional
    public int resolveFor(UUID bookingId, String reason, UUID resolvingEventId, AlertType... types) {
        List<AlertType> targets = List.of(types);
        int resolved = 0;
        for (Alert alert : repository.findOpenByBooking(bookingId)) {
            if (targets.contains(alert.getAlertType())) {
                alert.resolve("SYSTEM", reason, resolvingEventId, clock.instant());
                repository.save(alert);
                resolved++;
            }
        }
        return resolved;
    }

    /** Business rule 10: a cancelled booking has no live problems left to warn about. */
    @Transactional
    public int cancelAllFor(UUID bookingId, String reason) {
        int resolved = 0;
        for (Alert alert : repository.findOpenByBooking(bookingId)) {
            alert.resolve("SYSTEM", reason, null, clock.instant());
            repository.save(alert);
            resolved++;
        }
        return resolved;
    }

    /**
     * Business rule 1: a reinstated booking is on a different vessel, so every deadline
     * counted from the old ETD is now wrong.
     */
    @Transactional
    public int recalculateFor(UUID bookingId, java.time.LocalDate newEtd) {
        Instant now = clock.instant();
        Instant deadline = newEtd == null
                ? null : newEtd.atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
        int updated = 0;
        for (Alert alert : repository.findOpenByBooking(bookingId)) {
            if (alert.getAlertType().thresholdDays() == null) {
                continue;
            }
            alert.recalculateDeadline(deadline, alert.getAlertType().baseCategory(), now);
            repository.save(alert);
            updated++;
        }
        return updated;
    }

    // ------------------------------------------------------------- background

    /** Business rule 3: a snooze ends when it ends, business hours or not. */
    @Transactional
    public int reactivateExpiredSnoozes() {
        Instant now = clock.instant();
        List<Alert> expired = repository.findSnoozeExpired(now);
        expired.forEach(alert -> {
            alert.reactivate(now);
            repository.save(alert);
        });
        return expired.size();
    }

    /**
     * Escalates alerts nobody has answered, and flags the booking when one runs past its
     * hard deadline.
     */
    @Transactional
    public int escalateStale() {
        Instant now = clock.instant();
        int escalated = 0;
        for (Alert alert : repository.findActive()) {
            if (!alert.escalationDue(now)) {
                continue;
            }
            AlertType type = alert.getAlertType();
            alert.escalate(supervisorFor(type), type.categoryAt(null), now);
            if (alert.isOverdue(now)) {
                events.publishEvent(new AlertEvent.BookingFlaggedAtRisk(
                        alert.getBookingId(), alert.getBookingReference(), alert.getId(),
                        type, alert.getTitle(), now));
            }
            repository.save(alert);
            escalated++;
        }
        return escalated;
    }

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public Alert get(UUID alertId) {
        return repository.findById(alertId)
                .orElseThrow(() -> new AlertNotFoundException(alertId));
    }

    @Transactional(readOnly = true)
    public List<Alert> findOpen() {
        return repository.findOpenAlerts();
    }

    @Transactional(readOnly = true)
    public List<Alert> findForBooking(UUID bookingId) {
        return repository.findByBookingIdOrderByCreatedAtDesc(bookingId);
    }

    @Transactional(readOnly = true)
    public List<Alert> findOverdue() {
        return repository.findOverdue(clock.instant());
    }

    @Transactional(readOnly = true)
    public AlertConfiguration configurationFor(AlertType type) {
        return configurationRepository.findByAlertType(type)
                .orElseGet(() -> {
                    log.debug("No configuration for {} — using system defaults", type);
                    return AlertConfiguration.defaultsFor(type);
                });
    }

    @Transactional(readOnly = true)
    public List<AlertConfiguration> configurations() {
        return java.util.Arrays.stream(AlertType.values()).map(this::configurationFor).toList();
    }

    @Transactional
    public AlertConfiguration updateConfiguration(
            AlertType type, boolean enabled, Integer thresholdDays, int escalationHours,
            java.util.Set<NotificationChannel> channels, int snoozeMaxHours, String customMessage) {
        AlertConfiguration configuration = configurationRepository.findByAlertType(type)
                .orElseGet(() -> AlertConfiguration.defaultsFor(type));
        configuration.update(enabled, thresholdDays, escalationHours, channels,
                snoozeMaxHours, customMessage);
        return configurationRepository.save(configuration);
    }

    /** Who a stale alert goes over the head of. Derived from the track, not configured. */
    private RecipientRole supervisorFor(AlertType type) {
        return switch (type.track()) {
            case FINANCE -> RecipientRole.FINANCE_MANAGEMENT;
            case LOGISTICS, BOOKING, DOCUMENTATION -> RecipientRole.OPERATIONS_MANAGEMENT;
            case COMPLIANCE -> RecipientRole.MANAGEMENT;
        };
    }
}
