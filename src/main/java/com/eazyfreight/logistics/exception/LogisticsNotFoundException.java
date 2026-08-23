package com.eazyfreight.logistics.exception;

import java.util.UUID;

public class LogisticsNotFoundException extends RuntimeException {

    public LogisticsNotFoundException(UUID bookingId) {
        super("No container logistics record for booking: " + bookingId);
    }
}
