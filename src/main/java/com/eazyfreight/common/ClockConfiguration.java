package com.eazyfreight.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * A single injected Clock keeps every deadline calculation — quote expiry, the
 * five-business-day ETD floor, the T+2 carrier payment rule — testable without
 * waiting for real time to pass.
 */
@Configuration
public class ClockConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
