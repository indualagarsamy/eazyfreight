package com.eazyfreight.booking;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CarrierBookingResponse(
        UUID id,
        BookingSourceType bookingSourceType,
        UUID carrierId,
        String carrierBookingRef,
        String coLoaderBookingRef,
        String vesselName,
        String voyageNumber,
        LocalDate confirmedEtd,
        LocalDate confirmedEta,
        ContainerType containerType,
        Integer numberOfContainers,
        Instant submittedAt,
        Instant confirmedAt,
        String confirmedBy
) {
    public static CarrierBookingResponse fromEntity(CarrierBooking carrierBooking) {
        if (carrierBooking == null) {
            return null;
        }
        return new CarrierBookingResponse(
                carrierBooking.getId(),
                carrierBooking.getBookingSourceType(),
                carrierBooking.getCarrierId(),
                carrierBooking.getCarrierBookingRef(),
                carrierBooking.getCoLoaderBookingRef(),
                carrierBooking.getVesselName(),
                carrierBooking.getVoyageNumber(),
                carrierBooking.getConfirmedEtd(),
                carrierBooking.getConfirmedEta(),
                carrierBooking.getContainerType(),
                carrierBooking.getNumberOfContainers(),
                carrierBooking.getSubmittedAt(),
                carrierBooking.getConfirmedAt(),
                carrierBooking.getConfirmedBy()
        );
    }
}
