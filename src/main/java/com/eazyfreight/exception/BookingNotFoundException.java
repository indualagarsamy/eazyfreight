package com.eazyfreight.exception;

import java.util.UUID;

public class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException(UUID id) {
        super("Booking not found with id: " + id);
    }

    public BookingNotFoundException(String bookingReference) {
        super("Booking not found with reference: " + bookingReference);
    }
}
