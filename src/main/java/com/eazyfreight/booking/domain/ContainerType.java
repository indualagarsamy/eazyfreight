package com.eazyfreight.booking.domain;

/**
 * Container types with their maximum payload in kilograms.
 *
 * <p>The Ocean Booking specification omits a limit for 45HC; the Container and
 * Equipment specification publishes one (26,500 kg, business rule 8). The latter is
 * used, so every container type is now validated.
 */
public enum ContainerType {

    TWENTY_GP("20GP", 28_000),
    FORTY_GP("40GP", 26_500),
    FORTY_HC("40HC", 26_500),
    FORTY_FIVE_HC("45HC", 26_500);

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
