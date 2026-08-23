package com.eazyfreight.alerts.dto;

import com.eazyfreight.alerts.domain.NotificationChannel;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Set;

/** Request payloads for the Alerts commands. */
public final class AlertRequests {

    private AlertRequests() {
    }

    public record Snooze(
            @NotNull(message = "until is required") Instant until
    ) {
    }

    public record Resolve(
            @jakarta.validation.constraints.NotBlank(message = "reason is required") String reason
    ) {
    }

    public record UpdateConfiguration(
            boolean enabled,
            Integer thresholdDays,
            @Min(value = 1, message = "escalationHours must be at least 1") int escalationHours,
            @NotEmpty(message = "channels is required") Set<NotificationChannel> channels,
            @Min(value = 1, message = "snoozeMaxHours must be at least 1") int snoozeMaxHours,
            String customMessage
    ) {
    }
}
