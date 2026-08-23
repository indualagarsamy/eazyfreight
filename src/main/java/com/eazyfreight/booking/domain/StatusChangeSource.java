package com.eazyfreight.booking.domain;

/** Where a status change originated — recorded on every history entry. */
public enum StatusChangeSource {
    MANUAL,
    INTTRA,
    SYSTEM
}
