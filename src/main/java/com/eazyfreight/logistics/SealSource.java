package com.eazyfreight.logistics;

/** Who applied the seal. Customs re-sealing is a different event from the customer sealing. */
public enum SealSource {
    CUSTOMER_ISSUED,
    CUSTOMS_ISSUED
}
