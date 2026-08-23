package com.eazyfreight.alerts.exception;

import java.util.UUID;

public class AlertNotFoundException extends RuntimeException {

    public AlertNotFoundException(UUID alertId) {
        super("Alert " + alertId + " not found");
    }
}
