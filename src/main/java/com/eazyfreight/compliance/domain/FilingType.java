package com.eazyfreight.compliance.domain;

/**
 * A filing is never edited. A correction or a change of facts produces a new
 * filing that references the one it supersedes.
 */
public enum FilingType {
    ORIGINAL,
    AMENDMENT,
    CANCELLATION
}
