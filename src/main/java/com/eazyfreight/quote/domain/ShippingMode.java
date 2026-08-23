package com.eazyfreight.quote.domain;

/**
 * Modes a quote can be raised for. Note this is deliberately not shared with the
 * booking context, which supports ocean only — the two contexts own their own
 * vocabulary even where the terms overlap.
 */
public enum ShippingMode {
    OCEAN_FCL,
    OCEAN_LCL,
    AIR
}
