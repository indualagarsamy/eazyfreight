package com.eazyfreight.booking;

/**
 * Whether space was bought direct from the carrier or through a co-loader.
 * Commercial terms, liability and escalation paths differ, so the two are never
 * collapsed into one free-text "Carrier" field.
 */
public enum BookingSourceType {
    DIRECT_CARRIER,
    CO_LOADER
}
