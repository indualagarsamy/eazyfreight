package com.eazyfreight.logistics.controller;

import com.eazyfreight.logistics.dto.LogisticsRequests;
import com.eazyfreight.logistics.dto.LogisticsResponses;
import com.eazyfreight.logistics.model.ActualCargoView;
import com.eazyfreight.logistics.model.AddressType;
import com.eazyfreight.logistics.model.AwaitingDispatch;
import com.eazyfreight.logistics.model.ContainerSource;
import com.eazyfreight.logistics.model.ContainerType;
import com.eazyfreight.logistics.model.Dispatch;
import com.eazyfreight.logistics.model.DispatchStatus;
import com.eazyfreight.logistics.model.DispatchTruck;
import com.eazyfreight.logistics.model.Examination;
import com.eazyfreight.logistics.model.ExaminationHold;
import com.eazyfreight.logistics.model.ExaminationRelease;
import com.eazyfreight.logistics.model.LoadedOnVessel;
import com.eazyfreight.logistics.model.Logistics;
import com.eazyfreight.logistics.model.LogisticsStage;
import com.eazyfreight.logistics.model.MovementType;
import com.eazyfreight.logistics.model.RecordContainerNumber;
import com.eazyfreight.logistics.model.RecordSeal;
import com.eazyfreight.logistics.model.Seal;
import com.eazyfreight.logistics.model.SealDeactivationReason;
import com.eazyfreight.logistics.model.SealSource;
import com.eazyfreight.logistics.model.Terminal;
import com.eazyfreight.logistics.model.TerminalGateReceipt;
import com.eazyfreight.logistics.model.TerminalGateRejection;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Converts between the logistics domain read models and the generated OpenAPI models. */
final class LogisticsApiMapper {

    private LogisticsApiMapper() {
    }

    static Logistics toView(LogisticsResponses.Logistics logistics) {
        Logistics view = new Logistics();
        view.setId(logistics.id());
        view.setBookingId(logistics.bookingId());
        view.setContainerNumber(logistics.containerNumber());
        view.setContainerType(mapEnum(logistics.containerType(), ContainerType.class));
        view.setSource(mapEnum(logistics.source(), ContainerSource.class));
        view.setAssignedAt(toOffsetDateTime(logistics.assignedAt()));
        view.setAssignedBy(logistics.assignedBy());
        view.setStage(mapEnum(logistics.stage(), LogisticsStage.class));
        view.setItnReceived(logistics.itnReceived());
        view.setItnNumber(logistics.itnNumber());
        view.setInboundBlockedReason(logistics.inboundBlockedReason());
        view.setDocumentationPreconditionsMet(logistics.documentationPreconditionsMet());
        view.setLoadingCompletedAt(toOffsetDateTime(logistics.loadingCompletedAt()));
        view.setLoadedOnVesselAt(toOffsetDateTime(logistics.loadedOnVesselAt()));
        view.setVesselDepartedAt(toOffsetDateTime(logistics.vesselDepartedAt()));
        view.setCreatedAt(toOffsetDateTime(logistics.createdAt()));
        view.setActiveSealNumber(logistics.activeSealNumber());
        view.setSealRecords(logistics.sealRecords().stream().map(LogisticsApiMapper::toSeal).toList());
        view.setDispatches(logistics.dispatches().stream().map(LogisticsApiMapper::toDispatch).toList());
        view.setExaminations(logistics.examinations().stream().map(LogisticsApiMapper::toExamination).toList());
        view.setTerminalAcceptance(toTerminal(logistics.terminalAcceptance()));
        view.setActualCargoDetails(toActualCargoView(logistics.actualCargoDetails()));
        return view;
    }

    static AwaitingDispatch toAwaitingDispatch(LogisticsResponses.AwaitingDispatch dispatch) {
        AwaitingDispatch view = new AwaitingDispatch();
        view.setBookingId(dispatch.bookingId());
        view.setBookingReference(dispatch.bookingReference());
        view.setRequestedEtd(dispatch.requestedEtd());
        view.setConfirmedEtd(dispatch.confirmedEtd());
        view.setPickupAddress(dispatch.pickupAddress());
        return view;
    }

    private static Seal toSeal(LogisticsResponses.Seal seal) {
        Seal view = new Seal();
        view.setId(seal.id());
        view.setSealNumber(seal.sealNumber());
        view.setSealSource(mapEnum(seal.sealSource(), SealSource.class));
        view.setActive(seal.active());
        view.setIssuedAt(toOffsetDateTime(seal.issuedAt()));
        view.setDeactivatedAt(toOffsetDateTime(seal.deactivatedAt()));
        view.setDeactivationReason(mapEnum(seal.deactivationReason(), SealDeactivationReason.class));
        view.setReplacedBySealId(seal.replacedBySealId());
        view.setRecordedBy(seal.recordedBy());
        return view;
    }

    private static Dispatch toDispatch(LogisticsResponses.Dispatch dispatch) {
        Dispatch view = new Dispatch();
        view.setId(dispatch.id());
        view.setMovementType(mapEnum(dispatch.movementType(), MovementType.class));
        view.setTdoReference(dispatch.tdoReference());
        view.setDriverId(dispatch.driverId());
        view.setTruckingVendorId(dispatch.truckingVendorId());
        view.setVehicleReference(dispatch.vehicleReference());
        view.setPickupAddress(dispatch.pickupAddress());
        view.setPickupAddressType(mapEnum(dispatch.pickupAddressType(), AddressType.class));
        view.setDeliveryAddress(dispatch.deliveryAddress());
        view.setDeliveryAddressType(mapEnum(dispatch.deliveryAddressType(), AddressType.class));
        view.setScheduledPickupDate(toOffsetDateTime(dispatch.scheduledPickupDate()));
        view.setActualPickupDate(toOffsetDateTime(dispatch.actualPickupDate()));
        view.setScheduledDeliveryDate(toOffsetDateTime(dispatch.scheduledDeliveryDate()));
        view.setActualDeliveryDate(toOffsetDateTime(dispatch.actualDeliveryDate()));
        view.setStatus(mapEnum(dispatch.status(), DispatchStatus.class));
        view.setDispatchedAt(toOffsetDateTime(dispatch.dispatchedAt()));
        view.setDispatchedBy(dispatch.dispatchedBy());
        view.setDeliveryReceiptReference(dispatch.deliveryReceiptReference());
        view.setNotes(dispatch.notes());
        return view;
    }

    private static Terminal toTerminal(LogisticsResponses.Terminal terminal) {
        if (terminal == null) {
            return null;
        }
        Terminal view = new Terminal();
        view.setId(terminal.id());
        view.setGateReceiptNumber(terminal.gateReceiptNumber());
        view.setTerminalName(terminal.terminalName());
        view.setAcceptedAt(toOffsetDateTime(terminal.acceptedAt()));
        view.setEarliestAcceptanceDate(terminal.earliestAcceptanceDate());
        view.setVesselCutOffDate(terminal.vesselCutOffDate());
        view.setStorageFeeApplies(terminal.storageFeeApplies());
        view.setDaysEarly(terminal.daysEarly());
        view.setStorageFeeDailyRate(terminal.storageFeeDailyRate());
        view.setEstimatedStorageFee(terminal.estimatedStorageFee());
        return view;
    }

    private static Examination toExamination(LogisticsResponses.Examination examination) {
        Examination view = new Examination();
        view.setId(examination.id());
        view.setHoldPlacedAt(toOffsetDateTime(examination.holdPlacedAt()));
        view.setExaminationCompletedAt(toOffsetDateTime(examination.examinationCompletedAt()));
        view.setResult(mapEnum(examination.result(), com.eazyfreight.logistics.model.ExaminationResult.class));
        view.setOriginalSealId(examination.originalSealId());
        view.setReplacementSealId(examination.replacementSealId());
        view.setCbpOfficerId(examination.cbpOfficerId());
        view.setNotes(examination.notes());
        view.setOpen(examination.open());
        return view;
    }

    private static ActualCargoView toActualCargoView(LogisticsResponses.ActualCargo cargo) {
        if (cargo == null) {
            return null;
        }
        ActualCargoView view = new ActualCargoView();
        view.setId(cargo.id());
        view.setActualWeightKg(cargo.actualWeightKg());
        view.setActualPieces(cargo.actualPieces());
        view.setActualCbm(cargo.actualCbm());
        view.setBookedWeightKg(cargo.bookedWeightKg());
        view.setBookedPieces(cargo.bookedPieces());
        view.setBookedCbm(cargo.bookedCbm());
        view.setWeightVarianceKg(cargo.weightVarianceKg());
        view.setDivergesMaterially(cargo.divergesMaterially());
        view.setRecordedAt(toOffsetDateTime(cargo.recordedAt()));
        view.setRecordedBy(cargo.recordedBy());
        return view;
    }

    static LogisticsRequests.DispatchTruck toDispatchTruck(DispatchTruck request) {
        return new LogisticsRequests.DispatchTruck(
                request.getDriverId(), request.getTruckingVendorId(), request.getVehicleReference(),
                request.getPickupAddress(), request.getDeliveryAddress(),
                toInstant(request.getScheduledPickupDate()), toInstant(request.getScheduledDeliveryDate()));
    }

    static LogisticsRequests.RecordContainerNumber toRecordContainerNumber(RecordContainerNumber request) {
        return new LogisticsRequests.RecordContainerNumber(
                request.getContainerNumber(),
                mapEnum(request.getContainerType(), com.eazyfreight.booking.domain.ContainerType.class),
                mapEnum(request.getSource(), com.eazyfreight.logistics.domain.ContainerSource.class));
    }

    static LogisticsRequests.RecordSeal toRecordSeal(RecordSeal request) {
        return new LogisticsRequests.RecordSeal(request.getSealNumber());
    }

    static LogisticsRequests.TerminalGateReceipt toTerminalGateReceipt(TerminalGateReceipt request) {
        return new LogisticsRequests.TerminalGateReceipt(
                request.getGateReceiptNumber(), request.getTerminalName(),
                request.getEarliestAcceptanceDate(), request.getVesselCutOffDate(),
                request.getStorageFeeDailyRate());
    }

    static LogisticsRequests.TerminalGateRejection toTerminalGateRejection(TerminalGateRejection request) {
        return new LogisticsRequests.TerminalGateRejection(request.getReason());
    }

    static LogisticsRequests.ExaminationHold toExaminationHold(ExaminationHold request) {
        if (request == null) {
            return new LogisticsRequests.ExaminationHold(null, null);
        }
        return new LogisticsRequests.ExaminationHold(request.getCbpOfficerId(), request.getNotes());
    }

    static LogisticsRequests.ExaminationRelease toExaminationRelease(ExaminationRelease request) {
        return new LogisticsRequests.ExaminationRelease(
                mapEnum(request.getResult(), com.eazyfreight.logistics.domain.ExaminationResult.class),
                request.getReplacementSealNumber(), request.getNotes());
    }

    static LogisticsRequests.LoadedOnVessel toLoadedOnVessel(LoadedOnVessel request) {
        if (request == null) {
            return new LogisticsRequests.LoadedOnVessel(null);
        }
        return new LogisticsRequests.LoadedOnVessel(request.getVesselName());
    }

    static LogisticsRequests.ActualCargo toActualCargo(com.eazyfreight.logistics.model.ActualCargo request) {
        return new LogisticsRequests.ActualCargo(
                request.getActualWeightKg(), request.getActualPieces(), request.getActualCbm());
    }

    private static Instant toInstant(OffsetDateTime dateTime) {
        return dateTime == null ? null : dateTime.toInstant();
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static <S extends Enum<S>, T extends Enum<T>> T mapEnum(S source, Class<T> targetType) {
        return source == null ? null : Enum.valueOf(targetType, source.name());
    }
}
