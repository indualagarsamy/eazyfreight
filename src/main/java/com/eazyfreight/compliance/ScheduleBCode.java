package com.eazyfreight.compliance;

import com.eazyfreight.exception.DomainRuleViolationException;

import java.util.regex.Pattern;

/**
 * A CBP Schedule B commodity code.
 *
 * <p>This type exists to mark a Conformist boundary. CBP dictates the schema, and
 * Schedule B is CBP's classification — related to, but not the same as, the HS code
 * the customer gives us at quote time. A real implementation would translate one to
 * the other against the Census Bureau's published table.
 *
 * <p>No such table exists here. {@link #fromHsCode} carries the HS code through
 * unchanged and marks it {@code translated = false}, so the gap is visible in the
 * data rather than hidden behind an assumption that the two are interchangeable.
 */
public record ScheduleBCode(String value, boolean translated) {

    private static final Pattern FORMAT = Pattern.compile("\\d{4}\\.\\d{2}\\.\\d{4}");

    public ScheduleBCode {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new DomainRuleViolationException(
                    "Schedule B number must match format NNNN.NN.NNNN (got: " + value + ")");
        }
    }

    /**
     * Carries an HS code across the boundary untranslated. The returned code is
     * flagged so downstream consumers know it has not been mapped to Schedule B.
     */
    public static ScheduleBCode fromHsCode(String hsCode) {
        return new ScheduleBCode(hsCode, false);
    }

    /** A code entered directly by compliance staff as a genuine Schedule B number. */
    public static ScheduleBCode declared(String scheduleBNumber) {
        return new ScheduleBCode(scheduleBNumber, true);
    }
}
