package com.eazyfreight.alerts;

/**
 * Who is expected to act.
 *
 * <p>Roles, not people. The specification names roles throughout, and an alert
 * addressed to a named individual is an alert that goes unread when they are on
 * leave. Routing a role to a person is a concern for whatever directory the
 * deployment has; the domain only knows the role.
 */
public enum RecipientRole {
    OPERATIONS_STAFF("Operations Staff"),
    COMPLIANCE_STAFF("Compliance Staff"),
    ACCOUNTING_STAFF("Accounting Staff"),
    OPERATIONS_MANAGEMENT("Operations Management"),
    FINANCE_MANAGEMENT("Finance Management"),
    MANAGEMENT("Management");

    private final String label;

    RecipientRole(String label) {
        this.label = label;
    }

    /** For audit notes, which are read by people rather than parsed. */
    public String label() {
        return label;
    }
}
