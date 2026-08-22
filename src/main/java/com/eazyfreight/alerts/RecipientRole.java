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
    OPERATIONS_STAFF,
    COMPLIANCE_STAFF,
    ACCOUNTING_STAFF,
    OPERATIONS_MANAGEMENT,
    FINANCE_MANAGEMENT,
    MANAGEMENT
}
