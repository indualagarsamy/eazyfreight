package com.eazyfreight;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Scheduling is on because the Alerts track needs it: alert conditions are time-based,
 * and nothing else in the system will notice at 03:00 that an ETD is now three days away.
 */
@SpringBootApplication
@EnableScheduling
public class EazyFreightServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EazyFreightServiceApplication.class, args);
    }
}
