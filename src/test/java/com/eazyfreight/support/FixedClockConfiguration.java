package com.eazyfreight.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Pins the clock so date-sensitive rules — the five-business-day ETD floor, quote
 * validity, the two-business-day ETD variance threshold — behave the same on every
 * run.
 *
 * <p>2024-03-15 is a Friday, which also exercises the weekend skip in
 * {@link com.eazyfreight.common.BusinessDays}.
 */
@TestConfiguration
public class FixedClockConfiguration {

    public static final Instant FIXED_INSTANT = Instant.parse("2024-03-15T10:00:00Z");

    @Bean
    @Primary
    public Clock fixedClock() {
        return Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
    }
}
