package com.eazyfreight.compliance.domain;

import com.eazyfreight.compliance.client.SimulatedAesFilingClient;

import com.eazyfreight.exception.DomainRuleViolationException;

import java.util.regex.Pattern;

/**
 * Internal Transaction Number issued by CBP on accepting an EEI filing.
 *
 * <p>Format is the letter X followed by 14 digits. The specification requires any
 * value not matching to be rejected as invalid — in the monolith this was a free
 * text column that accepted anything typed into it.
 *
 * <p>The first eight digits of a genuine ITN are the filing date. Simulated values
 * issued by {@link SimulatedAesFilingClient} deliberately use 99999999 there, so a
 * test or demo ITN can never be mistaken for one CBP actually issued.
 */
public record ItnNumber(String value) {

    private static final Pattern FORMAT = Pattern.compile("X\\d{14}");
    public static final String SIMULATED_DATE_PART = "99999999";

    public ItnNumber {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new DomainRuleViolationException(
                    "ITN number must be the letter X followed by 14 digits (got: " + value + ")");
        }
    }

    /** True when this ITN came from the simulator rather than CBP. */
    public boolean isSimulated() {
        return value.startsWith("X" + SIMULATED_DATE_PART);
    }

    @Override
    public String toString() {
        return value;
    }
}
