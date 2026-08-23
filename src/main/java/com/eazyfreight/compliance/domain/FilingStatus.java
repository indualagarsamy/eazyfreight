package com.eazyfreight.compliance.domain;

public enum FilingStatus {
    DRAFT,
    SUBMITTED,
    ACCEPTED,
    REJECTED,
    CANCELLED;

    /** Once submitted, the filing's data is frozen — CBP has seen it. */
    public boolean isImmutable() {
        return this != DRAFT;
    }
}
