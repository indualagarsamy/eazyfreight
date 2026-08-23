package com.eazyfreight.quote.client;

import java.math.BigDecimal;

/**
 * Response from the denied party screening service.
 *
 * @param matchedList the list a match was found on, e.g. "OFAC SDN List"; null when cleared
 * @param matchScore  0.0 to 1.0; null when cleared
 */
public record ScreeningResult(
        boolean cleared,
        String matchedList,
        BigDecimal matchScore,
        String referenceId
) {
    public static ScreeningResult cleared(String referenceId) {
        return new ScreeningResult(true, null, null, referenceId);
    }

    public static ScreeningResult flagged(String matchedList, BigDecimal matchScore, String referenceId) {
        return new ScreeningResult(false, matchedList, matchScore, referenceId);
    }
}
