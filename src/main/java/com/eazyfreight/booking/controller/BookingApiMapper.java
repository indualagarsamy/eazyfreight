package com.eazyfreight.booking.controller;

import com.eazyfreight.booking.dto.BookingCargoDetailRequest;
import com.eazyfreight.booking.dto.BookingCargoDetailResponse;
import com.eazyfreight.booking.dto.BookingReinstatementResponse;
import com.eazyfreight.booking.dto.BookingResponse;
import com.eazyfreight.booking.dto.BookingStatusHistoryResponse;
import com.eazyfreight.booking.dto.CancelBookingRequest;
import com.eazyfreight.booking.dto.CarrierBookingResponse;
import com.eazyfreight.booking.dto.CreateBookingRequest;
import com.eazyfreight.booking.dto.RecordCarrierConfirmationRequest;
import com.eazyfreight.booking.dto.RecordCarrierRejectionRequest;
import com.eazyfreight.booking.dto.RecordCounterOfferRequest;
import com.eazyfreight.booking.dto.ReinstateBookingRequest;
import com.eazyfreight.booking.dto.SubmitBookingRequest;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** Converts between Booking DTOs and the generated OpenAPI models. */
final class BookingApiMapper {

    private BookingApiMapper() {
    }

    static com.eazyfreight.booking.model.BookingResponse toModel(BookingResponse response) {
        if (response == null) {
            return null;
        }
        com.eazyfreight.booking.model.BookingResponse model = new com.eazyfreight.booking.model.BookingResponse();
        model.setId(response.id());
        model.setBookingReference(response.bookingReference());
        model.setQuoteId(response.quoteId());
        model.setCustomerId(response.customerId());
        model.setShipperId(response.shipperId());
        model.setConsigneeId(response.consigneeId());
        model.setNotifyPartyId(response.notifyPartyId());
        model.setAlsoNotifyId(response.alsoNotifyId());
        model.setStatus(mapEnum(response.status(), com.eazyfreight.booking.model.BookingStatus.class));
        // Not mapEnum(): the generated model.ShippingMode's Java constants are FCL/LCL (openapi-generator
        // stripped the shared OCEAN_ prefix), so Enum.valueOf can't find "OCEAN_FCL" — fromValue(String)
        // is the generated type's own wire-value parser and handles the rename correctly.
        model.setShippingMode(response.shippingMode() == null
                ? null
                : com.eazyfreight.booking.model.ShippingMode.fromValue(response.shippingMode().toString()));
        model.setOriginPortCode(response.originPortCode());
        model.setDestinationPortCode(response.destinationPortCode());
        model.setIncoterms(response.incoterms());
        model.setRequestedEtd(response.requestedEtd());
        model.setRequestedEta(response.requestedEta());
        model.setTransportRequired(response.transportRequired());
        model.setPickupAddress(response.pickupAddress());
        model.setPickupDateTime(toOffsetDateTime(response.pickupDateTime()));
        model.setSpecialInstructions(response.specialInstructions());
        model.setMarksAndNumbers(response.marksAndNumbers());
        model.setCancellationReason(response.cancellationReason());
        model.setCancellationInitiatedBy(mapEnum(response.cancellationInitiatedBy(),
                com.eazyfreight.booking.model.CancellationInitiator.class));
        model.setCancelledAt(toOffsetDateTime(response.cancelledAt()));
        model.setRequiresCustomerEtdNotification(response.requiresCustomerEtdNotification());
        model.setEtdVarianceAcknowledgedAt(toOffsetDateTime(response.etdVarianceAcknowledgedAt()));
        model.setConfirmationSentAt(toOffsetDateTime(response.confirmationSentAt()));
        model.setItnFiled(response.itnFiled());
        model.setTotalWeightKg(response.totalWeightKg());
        model.setTotalValueUsd(response.totalValueUsd());
        model.setCreatedAt(toOffsetDateTime(response.createdAt()));
        model.setCreatedBy(response.createdBy());
        model.setLastModifiedAt(toOffsetDateTime(response.lastModifiedAt()));
        model.setLastModifiedBy(response.lastModifiedBy());
        model.setCarrierBooking(toModel(response.carrierBooking()));
        model.setCargoDetails(response.cargoDetails().stream().map(BookingApiMapper::toModel).toList());
        model.setStatusHistory(response.statusHistory().stream().map(BookingApiMapper::toModel).toList());
        model.setReinstatements(response.reinstatements().stream().map(BookingApiMapper::toModel).toList());
        return model;
    }

    private static com.eazyfreight.booking.model.CarrierBookingResponse toModel(CarrierBookingResponse response) {
        if (response == null) {
            return null;
        }
        com.eazyfreight.booking.model.CarrierBookingResponse model = new com.eazyfreight.booking.model.CarrierBookingResponse();
        model.setId(response.id());
        model.setBookingSourceType(mapEnum(response.bookingSourceType(), com.eazyfreight.booking.model.BookingSourceType.class));
        model.setCarrierId(response.carrierId());
        model.setCarrierBookingRef(response.carrierBookingRef());
        model.setCoLoaderBookingRef(response.coLoaderBookingRef());
        model.setVesselName(response.vesselName());
        model.setVoyageNumber(response.voyageNumber());
        model.setConfirmedEtd(response.confirmedEtd());
        model.setConfirmedEta(response.confirmedEta());
        model.setContainerType(mapEnum(response.containerType(), com.eazyfreight.booking.model.ContainerType.class));
        model.setNumberOfContainers(response.numberOfContainers());
        model.setSubmittedAt(toOffsetDateTime(response.submittedAt()));
        model.setConfirmedAt(toOffsetDateTime(response.confirmedAt()));
        model.setConfirmedBy(response.confirmedBy());
        return model;
    }

    private static com.eazyfreight.booking.model.BookingCargoDetailResponse toModel(BookingCargoDetailResponse response) {
        com.eazyfreight.booking.model.BookingCargoDetailResponse model = new com.eazyfreight.booking.model.BookingCargoDetailResponse();
        model.setId(response.id());
        model.setDescription(response.description());
        model.setHsCode(response.hsCode());
        model.setPieces(response.pieces());
        model.setWeightKg(response.weightKg());
        model.setLengthCm(response.lengthCm());
        model.setWidthCm(response.widthCm());
        model.setHeightCm(response.heightCm());
        model.setCbm(response.cbm());
        model.setValueUsd(response.valueUsd());
        model.setHazmat(response.hazmat());
        model.setTemperatureControlled(response.temperatureControlled());
        model.setOversized(response.oversized());
        model.setMarksAndNumbers(response.marksAndNumbers());
        return model;
    }

    static com.eazyfreight.booking.model.BookingStatusHistoryResponse toModel(BookingStatusHistoryResponse response) {
        com.eazyfreight.booking.model.BookingStatusHistoryResponse model = new com.eazyfreight.booking.model.BookingStatusHistoryResponse();
        model.setId(response.id());
        model.setSequenceNumber(response.sequenceNumber());
        model.setFromStatus(mapEnum(response.fromStatus(), com.eazyfreight.booking.model.BookingStatus.class));
        model.setToStatus(mapEnum(response.toStatus(), com.eazyfreight.booking.model.BookingStatus.class));
        model.setChangedAt(toOffsetDateTime(response.changedAt()));
        model.setChangedBy(response.changedBy());
        model.setReason(response.reason());
        model.setSource(mapEnum(response.source(), com.eazyfreight.booking.model.StatusChangeSource.class));
        return model;
    }

    private static com.eazyfreight.booking.model.BookingReinstatementResponse toModel(BookingReinstatementResponse response) {
        com.eazyfreight.booking.model.BookingReinstatementResponse model = new com.eazyfreight.booking.model.BookingReinstatementResponse();
        model.setId(response.id());
        model.setPreviousVessel(response.previousVessel());
        model.setPreviousVoyage(response.previousVoyage());
        model.setPreviousEtd(response.previousEtd());
        model.setPreviousEta(response.previousEta());
        model.setNewVesselName(response.newVesselName());
        model.setNewVoyageNumber(response.newVoyageNumber());
        model.setNewEtd(response.newEtd());
        model.setNewEta(response.newEta());
        model.setReinstatedAt(toOffsetDateTime(response.reinstatedAt()));
        model.setReinstatedBy(response.reinstatedBy());
        model.setReason(response.reason());
        return model;
    }

    static CreateBookingRequest toDto(com.eazyfreight.booking.model.CreateBookingRequest request) {
        List<BookingCargoDetailRequest> cargoDetails = request.getCargoDetails() == null
                ? List.of()
                : request.getCargoDetails().stream().map(BookingApiMapper::toDto).toList();
        return new CreateBookingRequest(
                request.getQuoteId(),
                request.getCustomerId(),
                request.getShipperId(),
                request.getConsigneeId(),
                request.getNotifyPartyId(),
                request.getAlsoNotifyId(),
                mapEnum(request.getShippingMode(), com.eazyfreight.booking.domain.ShippingMode.class),
                request.getOriginPortCode(),
                request.getDestinationPortCode(),
                request.getIncoterms(),
                request.getRequestedEtd(),
                request.getRequestedEta(),
                Boolean.TRUE.equals(request.getTransportRequired()),
                request.getPickupAddress(),
                toInstant(request.getPickupDateTime()),
                request.getSpecialInstructions(),
                request.getMarksAndNumbers(),
                cargoDetails);
    }

    private static BookingCargoDetailRequest toDto(com.eazyfreight.booking.model.BookingCargoDetailRequest request) {
        return new BookingCargoDetailRequest(
                request.getDescription(),
                request.getHsCode(),
                request.getPieces(),
                request.getWeightKg(),
                request.getLengthCm(),
                request.getWidthCm(),
                request.getHeightCm(),
                request.getValueUsd(),
                Boolean.TRUE.equals(request.getHazmat()),
                Boolean.TRUE.equals(request.getTemperatureControlled()),
                Boolean.TRUE.equals(request.getOversized()),
                request.getMarksAndNumbers());
    }

    static SubmitBookingRequest toDto(com.eazyfreight.booking.model.SubmitBookingRequest request) {
        return new SubmitBookingRequest(
                mapEnum(request.getBookingSourceType(), com.eazyfreight.booking.domain.BookingSourceType.class),
                request.getCarrierId(),
                mapEnum(request.getContainerType(), com.eazyfreight.booking.domain.ContainerType.class),
                request.getNumberOfContainers());
    }

    static RecordCarrierConfirmationRequest toDto(com.eazyfreight.booking.model.RecordCarrierConfirmationRequest request) {
        return new RecordCarrierConfirmationRequest(
                request.getCarrierBookingRef(),
                request.getCoLoaderBookingRef(),
                request.getVesselName(),
                request.getVoyageNumber(),
                request.getConfirmedEtd(),
                request.getConfirmedEta(),
                mapEnum(request.getContainerType(), com.eazyfreight.booking.domain.ContainerType.class),
                toInstant(request.getConfirmedAt()));
    }

    static RecordCarrierRejectionRequest toDto(com.eazyfreight.booking.model.RecordCarrierRejectionRequest request) {
        return new RecordCarrierRejectionRequest(request.getReason());
    }

    static RecordCounterOfferRequest toDto(com.eazyfreight.booking.model.RecordCounterOfferRequest request) {
        return new RecordCounterOfferRequest(
                request.getProposedVessel(),
                request.getProposedVoyage(),
                request.getProposedEtd(),
                request.getProposedEta());
    }

    static ReinstateBookingRequest toDto(com.eazyfreight.booking.model.ReinstateBookingRequest request) {
        return new ReinstateBookingRequest(
                request.getNewVesselName(),
                request.getNewVoyageNumber(),
                request.getNewEtd(),
                request.getNewEta(),
                request.getReason());
    }

    static CancelBookingRequest toDto(com.eazyfreight.booking.model.CancelBookingRequest request) {
        return new CancelBookingRequest(
                request.getReason(),
                mapEnum(request.getInitiatedBy(), com.eazyfreight.booking.domain.CancellationInitiator.class));
    }

    static com.eazyfreight.booking.domain.BookingStatus toDomain(com.eazyfreight.booking.model.BookingStatus status) {
        return mapEnum(status, com.eazyfreight.booking.domain.BookingStatus.class);
    }

    private static Instant toInstant(OffsetDateTime dateTime) {
        return dateTime == null ? null : dateTime.toInstant();
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static <S extends Enum<S>, T extends Enum<T>> T mapEnum(S source, Class<T> targetType) {
        // source.toString(), not source.name(): the generated OpenAPI model enums override
        // toString() to return the wire value (e.g. "OCEAN_FCL"), but when every value of an
        // enum shares a common prefix, openapi-generator strips it from the Java constant name
        // (model.ShippingMode's constants are FCL/LCL, not OCEAN_FCL/OCEAN_LCL) — so name()
        // doesn't reliably match the domain enum's constant name, but toString() does.
        return source == null ? null : Enum.valueOf(targetType, source.toString());
    }
}
