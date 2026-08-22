package com.eazyfreight.booking;

/**
 * Who caused a cancellation. Carrier overbooking and a customer changing their mind
 * are commercially different events; the monolith recorded neither.
 */
public enum CancellationInitiator {
    CUSTOMER,
    CARRIER_OVERBOOKING,
    OPERATIONS
}
