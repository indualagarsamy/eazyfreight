package com.eazyfreight.booking.dto;

import com.eazyfreight.booking.domain.Booking;
import com.eazyfreight.booking.domain.BookingStatus;
import com.eazyfreight.booking.domain.CancellationInitiator;
import com.eazyfreight.booking.domain.ShippingMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * @param requiresCustomerEtdNotification true when the confirmed ETD moved more than
 *                                        two business days from what the customer
 *                                        asked for; the confirmation is blocked until
 *                                        acknowledged
 */
public record BookingResponse(
        UUID id,
        String bookingReference,
        UUID quoteId,
        UUID customerId,
        UUID shipperId,
        UUID consigneeId,
        UUID notifyPartyId,
        UUID alsoNotifyId,
        BookingStatus status,
        ShippingMode shippingMode,
        String originPortCode,
        String destinationPortCode,
        String incoterms,
        LocalDate requestedEtd,
        LocalDate requestedEta,
        boolean transportRequired,
        String pickupAddress,
        Instant pickupDateTime,
        String specialInstructions,
        String marksAndNumbers,
        String cancellationReason,
        CancellationInitiator cancellationInitiatedBy,
        Instant cancelledAt,
        boolean requiresCustomerEtdNotification,
        Instant etdVarianceAcknowledgedAt,
        Instant confirmationSentAt,
        boolean itnFiled,
        BigDecimal totalWeightKg,
        BigDecimal totalValueUsd,
        Instant createdAt,
        String createdBy,
        Instant lastModifiedAt,
        String lastModifiedBy,
        CarrierBookingResponse carrierBooking,
        List<BookingCargoDetailResponse> cargoDetails,
        List<BookingStatusHistoryResponse> statusHistory,
        List<BookingReinstatementResponse> reinstatements
) {
    public static BookingResponse fromEntity(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getBookingReference(),
                booking.getQuoteId(),
                booking.getCustomerId(),
                booking.getShipperId(),
                booking.getConsigneeId(),
                booking.getNotifyPartyId(),
                booking.getAlsoNotifyId(),
                booking.getStatus(),
                booking.getShippingMode(),
                booking.getOriginPortCode(),
                booking.getDestinationPortCode(),
                booking.getIncoterms(),
                booking.getRequestedEtd(),
                booking.getRequestedEta(),
                booking.isTransportRequired(),
                booking.getPickupAddress(),
                booking.getPickupDateTime(),
                booking.getSpecialInstructions(),
                booking.getMarksAndNumbers(),
                booking.getCancellationReason(),
                booking.getCancellationInitiatedBy(),
                booking.getCancelledAt(),
                booking.requiresCustomerEtdNotification(),
                booking.getEtdVarianceAcknowledgedAt(),
                booking.getConfirmationSentAt(),
                booking.isItnFiled(),
                booking.totalWeightKg(),
                booking.totalValueUsd(),
                booking.getCreatedAt(),
                booking.getCreatedBy(),
                booking.getLastModifiedAt(),
                booking.getLastModifiedBy(),
                CarrierBookingResponse.fromEntity(booking.getCarrierBooking()),
                booking.getCargoDetails().stream().map(BookingCargoDetailResponse::fromEntity).toList(),
                booking.getStatusHistory().stream().map(BookingStatusHistoryResponse::fromEntity).toList(),
                booking.getReinstatements().stream().map(BookingReinstatementResponse::fromEntity).toList()
        );
    }
}
