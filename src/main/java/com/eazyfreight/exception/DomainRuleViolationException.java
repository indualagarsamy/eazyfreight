package com.eazyfreight.exception;

/**
 * Raised when a command is rejected by an aggregate because it would violate a
 * business rule or an illegal state transition. Distinct from bean-validation
 * failures, which are structural rather than behavioural.
 */
public class DomainRuleViolationException extends RuntimeException {

    public DomainRuleViolationException(String message) {
        super(message);
    }
}
