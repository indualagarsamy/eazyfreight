package com.eazyfreight.logistics;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Domain events raised by the container logistics aggregate. */
public sealed interface LogisticsEvent {

    UUID bookingId();

    Instant occurredAt();

    record TruckDeliveryOrderDispatched(
            UUID bookingId, MovementType movementType, String tdoReference, Instant occurredAt
    ) implements LogisticsEvent {
    }

    /** Container numbers are late-binding — unknown until the driver reaches the yard. */
    record ContainerNumberAssigned(
            UUID bookingId, String containerNumber, Instant occurredAt
    ) implements LogisticsEvent {
    }

    record ContainerDeliveredToCustomer(UUID bookingId, Instant occurredAt) implements LogisticsEvent {
    }

    record CustomerLoadingComplete(UUID bookingId, Instant occurredAt) implements LogisticsEvent {
    }

    record SealNumberIssued(
            UUID bookingId, String sealNumber, SealSource source, Instant occurredAt
    ) implements LogisticsEvent {
    }

    /** Customs cut the seal and fitted a new one. The old number is retained, not replaced. */
    record SealNumberReplaced(
            UUID bookingId, String previousSealNumber, String newSealNumber,
            SealDeactivationReason reason, Instant occurredAt
    ) implements LogisticsEvent {
    }

    record InboundDispatchBlocked(
            UUID bookingId, String reason, Instant occurredAt
    ) implements LogisticsEvent {
    }

    record ContainerReturnedToPort(UUID bookingId, Instant occurredAt) implements LogisticsEvent {
    }

    record TerminalAccepted(
            UUID bookingId, String gateReceiptNumber, boolean storageFeeApplies,
            BigDecimal estimatedStorageFee, Instant occurredAt
    ) implements LogisticsEvent {
    }

    record TerminalRejected(
            UUID bookingId, String reason, Instant occurredAt
    ) implements LogisticsEvent {
    }

    record ContainerHeldForExamination(
            UUID bookingId, String containerNumber, String cbpOfficerId, Instant occurredAt
    ) implements LogisticsEvent {
    }

    record ContainerReleasedFromExamination(
            UUID bookingId, ExaminationResult result, Instant occurredAt
    ) implements LogisticsEvent {
    }

    record ContainerLoadedOnVessel(UUID bookingId, String vesselName, Instant occurredAt)
            implements LogisticsEvent {
    }

    record VesselDeparted(UUID bookingId, Instant occurredAt) implements LogisticsEvent {
    }

    record ActualCargoDetailsRecorded(
            UUID bookingId, BigDecimal actualWeightKg, int actualPieces,
            BigDecimal actualCbm, Instant occurredAt
    ) implements LogisticsEvent {
    }

    /**
     * Actuals diverged from the booking after the EEI was filed. Consumed by the
     * Compliance context, which owes CBP an amendment.
     */
    record AesAmendmentRequired(
            UUID bookingId, BigDecimal weightVarianceKg, Instant occurredAt
    ) implements LogisticsEvent {
    }

    /** All four preconditions for Master BOL instructions are now satisfied. */
    record DocumentationPreconditionsMet(
            UUID bookingId, String containerNumber, String sealNumber, Instant occurredAt
    ) implements LogisticsEvent {
    }
}
