package com.eazyfreight.alerts.domain;

/**
 * Where an alert is in its life.
 *
 * <p>{@code RESOLVED} is the only terminal state, and everything short of it counts as
 * open — which is what duplicate suppression keys on. An acknowledged alert is still
 * open: someone has seen it, but the condition that raised it has not gone away.
 */
public enum AlertStatus {
    ACTIVE,
    ACKNOWLEDGED,
    SNOOZED,
    ESCALATED,
    RESOLVED;

    public boolean isOpen() {
        return this != RESOLVED;
    }

    /** Acknowledging, snoozing and escalating all require the alert to still be live. */
    public boolean isActionable() {
        return this == ACTIVE || this == ESCALATED || this == SNOOZED;
    }
}
