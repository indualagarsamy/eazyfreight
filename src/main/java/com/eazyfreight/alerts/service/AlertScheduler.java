package com.eazyfreight.alerts.service;

import com.eazyfreight.alerts.listener.AlertListeners;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the sweep on a timer.
 *
 * <p>Half-hourly, per the specification. Every alert threshold in the system is measured
 * in days or hours, so a thirty-minute grain is well inside the resolution of anything
 * being watched, and a sweep that costs a handful of queries per live booking is cheap
 * enough to run whether or not anything has changed.
 *
 * <p>The sweep is a backstop, not the primary mechanism. Most conditions are cleared the
 * moment the operator does the thing, by {@link AlertListeners}. What the sweep adds is
 * that nothing depends on a listener having fired: an alert missed because a handler
 * threw turns up on the next pass, and one whose cause quietly went away is closed on
 * the next pass too.
 *
 * <p>Disabled in tests, where the clock is fixed and a background thread evaluating
 * against it would race with whatever the test is asserting.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "eazyfreight.alerts.scheduler.enabled",
        havingValue = "true", matchIfMissing = true)
public class AlertScheduler {

    private final AlertService alerts;

    @Scheduled(fixedDelayString = "${eazyfreight.alerts.scheduler.interval-ms:1800000}",
            initialDelayString = "${eazyfreight.alerts.scheduler.initial-delay-ms:30000}")
    public void sweep() {
        try {
            int changes = alerts.evaluateAll();
            if (changes > 0) {
                log.info("Alert sweep: {} alerts raised, resolved or escalated", changes);
            }
        } catch (RuntimeException ex) {
            // The specification is explicit that a failed sweep is itself an incident.
            // Swallowing it here keeps the scheduler alive for the next run — a dead
            // scheduler is silent, and silence reads as "nothing is wrong".
            log.error("Alert sweep failed; the next run will retry", ex);
        }
    }
}
