package com.eazyfreight.compliance;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Domain events raised by the EEI compliance aggregate. */
public sealed interface ComplianceEvent {

    UUID filingId();

    UUID bookingId();

    Instant occurredAt();

    record EEIFilingInitiated(
            UUID filingId, UUID bookingId, String filingReference,
            FilingType filingType, Instant occurredAt
    ) implements ComplianceEvent {
    }

    record EEISubmittedToCBP(
            UUID filingId, UUID bookingId, String filingReference,
            FilingType filingType, Instant occurredAt
    ) implements ComplianceEvent {
    }

    /** Consumed by the Booking context, which records that an ITN is on file. */
    record ItnNumberReceived(
            UUID filingId, UUID bookingId, String filingReference,
            String itnNumber, boolean simulated, Instant occurredAt
    ) implements ComplianceEvent {
    }

    /**
     * The gate the Logistics track waits on: an inbound truck may not be dispatched
     * to the port until this has fired for the booking.
     */
    record ItnGateCheckPassed(
            UUID filingId, UUID bookingId, String itnNumber, Instant occurredAt
    ) implements ComplianceEvent {
    }

    record EEIFilingRejected(
            UUID filingId, UUID bookingId, String filingReference,
            String rejectionCode, String rejectionDescription, Instant occurredAt
    ) implements ComplianceEvent {
    }

    /** Raised when facts changed after acceptance — an amendment is now owed to CBP. */
    record EEIAmendmentRequired(
            UUID filingId, UUID bookingId, String filingReference,
            String reason, LocalDate etdAtRisk, Instant occurredAt
    ) implements ComplianceEvent {
    }

    record EEIAmendmentSubmitted(
            UUID filingId, UUID bookingId, UUID parentFilingId,
            String filingReference, Instant occurredAt
    ) implements ComplianceEvent {
    }

    record ItnSuperseded(
            UUID filingId, UUID bookingId,
            String previousItnNumber, String newItnNumber, Instant occurredAt
    ) implements ComplianceEvent {
    }

    record EEIFilingCancelled(
            UUID filingId, UUID bookingId, String filingReference,
            String voidedItnNumber, String reason, Instant occurredAt
    ) implements ComplianceEvent {
    }

    /** Submission is blocked until a licence is recorded for a licensable commodity. */
    record ExportLicenseRequired(
            UUID filingId, UUID bookingId, String scheduleBNumber, Instant occurredAt
    ) implements ComplianceEvent {
    }

    record ExportLicenseRecorded(
            UUID filingId, UUID bookingId, String licenseNumber, Instant occurredAt
    ) implements ComplianceEvent {
    }
}
