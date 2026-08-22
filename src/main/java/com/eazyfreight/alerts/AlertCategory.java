package com.eazyfreight.alerts;

/**
 * How much business damage the condition does if nobody acts.
 *
 * <p>The category is not decoration. It sets two hard numbers the aggregate enforces:
 * how long an alert may be silenced, and how long it may sit unacknowledged before it
 * goes over someone's head. Both come from the specification's escalation policy.
 */
public enum AlertCategory {

    /** Immediate action required; the shipment is at imminent risk. */
    CRITICAL(4, 4),
    /** Action required within 24 hours. */
    HIGH(8, 8),
    /** Action required within 2-3 business days. */
    MEDIUM(24, 24),
    /** Informational; action required but no immediate risk. */
    LOW(24, 48);

    private final int maxSnoozeHours;
    private final int escalationHours;

    AlertCategory(int maxSnoozeHours, int escalationHours) {
        this.maxSnoozeHours = maxSnoozeHours;
        this.escalationHours = escalationHours;
    }

    /** The longest a recipient may push this out of sight. */
    public int maxSnoozeHours() {
        return maxSnoozeHours;
    }

    /** How long the alert may go unacknowledged before it escalates to a supervisor. */
    public int escalationHours() {
        return escalationHours;
    }

    /**
     * Whether a notification may go out irrespective of the hour. Business rule 9:
     * only Critical alerts interrupt people outside working hours.
     */
    public boolean ignoresBusinessHours() {
        return this == CRITICAL;
    }
}
