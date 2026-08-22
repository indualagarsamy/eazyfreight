package com.eazyfreight.booking;

/**
 * Container types with their maximum payload in kilograms.
 *
 * <p>Payload limits come from the Ocean Booking specification. No limit is
 * published there for 45HC, so it is left null and payload validation is skipped
 * for that type rather than guessed at.
 */
public enum ContainerType {

    TWENTY_GP("20GP", 28_000),
    FORTY_GP("40GP", 26_500),
    FORTY_HC("40HC", 26_500),
    FORTY_FIVE_HC("45HC", null);

    private final String code;
    private final Integer maxPayloadKg;

    ContainerType(String code, Integer maxPayloadKg) {
        this.code = code;
        this.maxPayloadKg = maxPayloadKg;
    }

    public String getCode() {
        return code;
    }

    public Integer getMaxPayloadKg() {
        return maxPayloadKg;
    }

    public boolean hasPublishedPayloadLimit() {
        return maxPayloadKg != null;
    }
}
