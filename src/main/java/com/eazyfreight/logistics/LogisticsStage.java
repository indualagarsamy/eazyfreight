package com.eazyfreight.logistics;

/**
 * Where the container has got to. Derived from the events recorded rather than
 * typed in — the monolith's {@code ContainerStatus} was a magic string with no
 * timestamps behind it.
 */
public enum LogisticsStage {
    NOT_STARTED,
    OUTBOUND_DISPATCHED,
    AT_CUSTOMER,
    LOADING_COMPLETE,
    SEALED,
    INBOUND_DISPATCHED,
    AT_TERMINAL,
    UNDER_CBP_EXAMINATION,
    LOADED_ON_VESSEL,
    DEPARTED
}
