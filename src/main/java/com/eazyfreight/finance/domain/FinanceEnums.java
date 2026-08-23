package com.eazyfreight.finance.domain;

/** Enumerations for the Finance context. */
public final class FinanceEnums {

    private FinanceEnums() {
    }

    public enum InvoiceStatus {
        /** Prepared at booking confirmation, but not yet a demand for payment. */
        PREPARED,
        ISSUED,
        PARTIALLY_PAID,
        PAID,
        VOIDED;

        public boolean isIssued() {
            return this == ISSUED || this == PARTIALLY_PAID || this == PAID;
        }
    }

    public enum InvoiceType {
        FREIGHT,
        STORAGE_FEE,
        CREDIT_NOTE
    }

    /**
     * How the due date is computed. Defaults to the more conservative option when a
     * customer has no terms on file.
     */
    public enum PaymentTermsType {
        /** Due 14 days before the vessel arrives — payment before the cargo lands. */
        TWO_WEEKS_BEFORE_ARRIVAL,
        /** Due 30 days from the invoice date. For established credit customers. */
        NET_30
    }

    /**
     * A payable moves through matching and approval before money leaves. The carrier
     * gets paid from the customer's money, not ours.
     */
    public enum PayableStatus {
        PENDING,
        AWAITING_INVOICE_MATCH,
        APPROVED,
        PAID
    }

    /** Who ends up carrying a storage fee. */
    public enum StorageFeeResponsibility {
        /** The customer asked for early delivery — passed through. */
        CUSTOMER,
        /** Our documentation was late — absorbed. */
        EAZY_FREIGHT,
        /** The carrier's acceptance window was unclear — disputed. */
        CARRIER_DISPUTED,
        /** Not yet determined. */
        UNDETERMINED
    }

    public enum StorageFeeCause {
        EARLY_PORT_DELIVERY,
        BOL_INSTRUCTIONS_LATE
    }

    public enum PaymentMethod {
        WIRE,
        CHECK,
        ACH
    }
}
