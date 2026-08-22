package com.eazyfreight.documentation;

import com.eazyfreight.documentation.DocumentationEnums.DistributionRecipient;
import com.eazyfreight.documentation.DocumentationEnums.ReleaseType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Domain events raised by the Documentation context. */
public sealed interface DocumentationEvent {

    UUID bookingId();

    Instant occurredAt();

    /** All four preconditions are satisfied — instructions can be compiled. */
    record DocumentationPreconditionsMet(
            UUID bookingId, String containerNumber, String sealNumber,
            String itnNumber, String carrierBookingRef, Instant occurredAt
    ) implements DocumentationEvent {
    }

    record MasterBOLInstructionsDrafted(
            UUID instructionsId, UUID bookingId, String reference, Instant occurredAt
    ) implements DocumentationEvent {
    }

    record MasterBOLInstructionsSentToCarrier(
            UUID instructionsId, UUID bookingId, String reference,
            String containerNumber, String sealNumber, String itnNumber,
            boolean sentAfterCutOff, Instant occurredAt
    ) implements DocumentationEvent {
    }

    record CarrierQueryRaised(
            UUID instructionsId, UUID bookingId, String reference, String query, Instant occurredAt
    ) implements DocumentationEvent {
    }

    record MasterBOLReceived(
            UUID masterBolId, UUID bookingId, String masterBolNumber, Instant occurredAt
    ) implements DocumentationEvent {
    }

    record MasterBOLVerified(
            UUID masterBolId, UUID bookingId, String masterBolNumber, Instant occurredAt
    ) implements DocumentationEvent {
    }

    record MasterBOLDiscrepancyRaised(
            UUID masterBolId, UUID bookingId, String masterBolNumber,
            List<String> discrepancyFields, Instant occurredAt
    ) implements DocumentationEvent {
    }

    /** Consumed by Finance — the invoice is issued once the House BOL exists. */
    record HouseBOLGenerated(
            UUID houseBolId, UUID bookingId, String houseBolNumber,
            int revisionNumber, ReleaseType releaseType, Instant occurredAt
    ) implements DocumentationEvent {
    }

    record HouseBOLDistributed(
            UUID houseBolId, UUID bookingId, String houseBolNumber,
            int revisionNumber, DistributionRecipient recipient, Instant occurredAt
    ) implements DocumentationEvent {
    }

    record HouseBOLAmended(
            UUID houseBolId, UUID bookingId, String houseBolNumber,
            int revisionNumber, String reason, Instant occurredAt
    ) implements DocumentationEvent {
    }

    /** Cargo figures on the BOL changed — Compliance may owe CBP an amendment. */
    record BOLCargoDetailsChanged(
            UUID houseBolId, UUID bookingId, String houseBolNumber, Instant occurredAt
    ) implements DocumentationEvent {
    }

    record OriginalBOLsReleased(
            UUID houseBolId, UUID bookingId, String houseBolNumber,
            int count, String courierReference, Instant occurredAt
    ) implements DocumentationEvent {
    }

    record OriginalBOLsSurrendered(
            UUID houseBolId, UUID bookingId, String houseBolNumber, Instant occurredAt
    ) implements DocumentationEvent {
    }
}
