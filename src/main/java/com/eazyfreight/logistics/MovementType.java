package com.eazyfreight.logistics;

/**
 * The two truck movements. They are separate dispatches with their own driver,
 * timeline and delivery receipt — the monolith collapsed both into one
 * {@code PickupDate} column and lost the distinction.
 */
public enum MovementType {
    /** Empty container from the carrier yard to the customer. */
    OUTBOUND,
    /** Loaded, sealed container from the customer to the port terminal. */
    INBOUND
}
