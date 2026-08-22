package com.eazyfreight.booking;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Domain events raised by the Booking aggregate, per the Ocean Booking specification. */
public sealed interface BookingEvent {

    UUID bookingId();

    Instant occurredAt();

    record BookingRequestCreated(
            UUID bookingId,
            String bookingReference,
            UUID quoteId,
            UUID customerId,
            ShippingMode shippingMode,
            String originPortCode,
            String destinationPortCode,
            LocalDate requestedEtd,
            Instant occurredAt
    ) implements BookingEvent {
    }

    record BookingSubmittedToCarrier(
            UUID bookingId,
            String bookingReference,
            UUID carrierId,
            BookingSourceType sourceType,
            Instant occurredAt
    ) implements BookingEvent {
    }

    record CarrierBookingConfirmed(
            UUID bookingId,
            String bookingReference,
            UUID carrierId,
            BookingSourceType sourceType,
            String carrierBookingRef,
            String coLoaderBookingRef,
            String vesselName,
            String voyageNumber,
            LocalDate confirmedEtd,
            LocalDate confirmedEta,
            boolean etdVarianceRequiresCustomerNotification,
            Instant occurredAt
    ) implements BookingEvent {
    }

    record BookingRejectedByCarrier(
            UUID bookingId,
            String bookingReference,
            UUID carrierId,
            String reason,
            Instant occurredAt
    ) implements BookingEvent {
    }

    record CarrierCounterOfferReceived(
            UUID bookingId,
            String bookingReference,
            String proposedVessel,
            String proposedVoyage,
            LocalDate proposedEtd,
            LocalDate proposedEta,
            Instant occurredAt
    ) implements BookingEvent {
    }

    record CarrierCounterOfferAccepted(
            UUID bookingId,
            String bookingReference,
            Instant occurredAt
    ) implements BookingEvent {
    }

    record CarrierCounterOfferRejected(
            UUID bookingId,
            String bookingReference,
            Instant occurredAt
    ) implements BookingEvent {
    }

    record BookingConfirmationSentToCustomer(
            UUID bookingId,
            String bookingReference,
            UUID customerId,
            boolean reissued,
            Instant occurredAt
    ) implements BookingEvent {
    }

    /** Consumed by the Finance track — the invoice can be prepared from here. */
    record BookingConfirmedForInvoicing(
            UUID bookingId,
            String bookingReference,
            UUID customerId,
            Instant occurredAt
    ) implements BookingEvent {
    }

    record TruckDeliveryOrderGenerated(
            UUID bookingId,
            String bookingReference,
            String tdoReference,
            String pickupAddress,
            Instant occurredAt
    ) implements BookingEvent {
    }

    record TruckDeliveryOrderDispatched(
            UUID bookingId,
            String tdoReference,
            UUID driverId,
            UUID truckingVendorId,
            Instant occurredAt
    ) implements BookingEvent {
    }

    record VesselOverbookedNotified(
            UUID bookingId,
            String bookingReference,
            String vesselName,
            LocalDate confirmedEtd,
            Instant occurredAt
    ) implements BookingEvent {
    }

    record BookingCancelled(
            UUID bookingId,
            String bookingReference,
            String reason,
            CancellationInitiator initiatedBy,
            Instant occurredAt
    ) implements BookingEvent {
    }

    record BookingReinstated(
            UUID bookingId,
            String bookingReference,
            String previousVessel,
            LocalDate previousEtd,
            String newVesselName,
            String newVoyageNumber,
            LocalDate newEtd,
            LocalDate newEta,
            Instant occurredAt
    ) implements BookingEvent {
    }

    /**
     * Raised alongside {@code BookingReinstated} when compliance has already filed.
     * A changed ETD or vessel invalidates the EEI on record with CBP.
     */
    record ItnAmendmentRequired(
            UUID bookingId,
            String bookingReference,
            LocalDate newEtd,
            String newVesselName,
            Instant occurredAt
    ) implements BookingEvent {
    }
}
