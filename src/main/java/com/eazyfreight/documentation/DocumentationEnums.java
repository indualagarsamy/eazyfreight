package com.eazyfreight.documentation;

/** Enumerations for the Documentation context, grouped since each is small. */
public final class DocumentationEnums {

    private DocumentationEnums() {
    }

    /** Shipping instructions to the carrier. Frozen once sent. */
    public enum InstructionsStatus {
        DRAFT, APPROVED, SENT, QUERIED, SUPERSEDED;

        public boolean isImmutable() {
            return this != DRAFT;
        }
    }

    /** Whether the carrier's Master BOL matches what we instructed. */
    public enum VerificationStatus {
        PENDING, VERIFIED, DISCREPANCY_RAISED, CORRECTED
    }

    public enum HouseBOLStatus {
        ISSUED, VOIDED
    }

    /**
     * How the consignee obtains the cargo. This is the commercially significant
     * choice on the document — the monolith records it nowhere at all.
     */
    public enum ReleaseType {
        /** Three negotiable originals, couriered; one must be surrendered at destination. */
        ORIGINAL_BOL,
        /** Shipper surrenders their claim electronically; no original issued. */
        TELEX_RELEASE,
        /** Non-negotiable; the named consignee collects on proof of identity. */
        SEA_WAYBILL
    }

    public enum FreightTerms {
        PREPAID, COLLECT
    }

    public enum DistributionRecipient {
        SHIPPER, CONSIGNEE_AGENT, NOTIFY_PARTY
    }

    public enum DistributionChannel {
        EMAIL, PORTAL, COURIER
    }
}
