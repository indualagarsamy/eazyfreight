package com.eazyfreight.booking.domain;

/**
 * Booking lifecycle, per the Ocean Booking specification.
 *
 * <p>The monolith carried this as a {@code VARCHAR(30)} of magic strings shared with
 * every later phase of the shipment. Here it covers the booking phase only, and
 * transitions are enforced by {@link Booking}.
 */
public enum BookingStatus {

    BOOKING_REQUESTED,
    SUBMITTED_TO_CARRIER,
    REJECTED_BY_CARRIER,
    COUNTER_OFFER_RECEIVED,
    CONFIRMED_BY_CARRIER,
    CUSTOMER_CONFIRMED,
    VESSEL_OVERBOOKED,
    CANCELLED;

    public boolean isTerminal() {
        return this == CANCELLED;
    }
}
