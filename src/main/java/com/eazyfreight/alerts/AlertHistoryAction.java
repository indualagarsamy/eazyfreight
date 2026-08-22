package com.eazyfreight.alerts;

/** The kinds of thing that can happen to an alert. Recorded, never edited. */
public enum AlertHistoryAction {
    CREATED,
    NOTIFICATION_SENT,
    ACKNOWLEDGED,
    SNOOZED,
    REACTIVATED,
    ESCALATED,
    DEADLINE_RECALCULATED,
    RESOLVED
}
