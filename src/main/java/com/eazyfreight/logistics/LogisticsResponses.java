package com.eazyfreight.logistics;

import com.eazyfreight.booking.ContainerType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Read models for the logistics context. */
public final class LogisticsResponses {

    private LogisticsResponses() {
    }

    /** A confirmed booking that needs a truck but has no outbound dispatch yet. */
    public record AwaitingDispatch(
            java.util.UUID bookingId,
            String bookingReference,
            java.time.LocalDate requestedEtd,
            java.time.LocalDate confirmedEtd,
            String pickupAddress
    ) {
    }

    public record Seal(
            UUID id, String sealNumber, SealSource sealSource, boolean active,
            Instant issuedAt, Instant deactivatedAt, SealDeactivationReason deactivationReason,
            UUID replacedBySealId, String recordedBy
    ) {
        static Seal from(SealRecord record) {
            return new Seal(record.getId(), record.getSealNumber(), record.getSealSource(),
                    record.isActive(), record.getIssuedAt(), record.getDeactivatedAt(),
                    record.getDeactivationReason(), record.getReplacedBySealId(),
                    record.getRecordedBy());
        }
    }

    public record Dispatch(
            UUID id, MovementType movementType, String tdoReference,
            UUID driverId, UUID truckingVendorId, String vehicleReference,
            String pickupAddress, AddressType pickupAddressType,
            String deliveryAddress, AddressType deliveryAddressType,
            Instant scheduledPickupDate, Instant actualPickupDate,
            Instant scheduledDeliveryDate, Instant actualDeliveryDate,
            DispatchStatus status, Instant dispatchedAt, String dispatchedBy,
            String deliveryReceiptReference, String notes
    ) {
        static Dispatch from(TruckDispatch dispatch) {
            return new Dispatch(dispatch.getId(), dispatch.getMovementType(),
                    dispatch.getTdoReference(), dispatch.getDriverId(),
                    dispatch.getTruckingVendorId(), dispatch.getVehicleReference(),
                    dispatch.getPickupAddress(), dispatch.getPickupAddressType(),
                    dispatch.getDeliveryAddress(), dispatch.getDeliveryAddressType(),
                    dispatch.getScheduledPickupDate(), dispatch.getActualPickupDate(),
                    dispatch.getScheduledDeliveryDate(), dispatch.getActualDeliveryDate(),
                    dispatch.getStatus(), dispatch.getDispatchedAt(), dispatch.getDispatchedBy(),
                    dispatch.getDeliveryReceiptReference(), dispatch.getNotes());
        }
    }

    public record Terminal(
            UUID id, String gateReceiptNumber, String terminalName, Instant acceptedAt,
            LocalDate earliestAcceptanceDate, LocalDate vesselCutOffDate,
            boolean storageFeeApplies, Integer daysEarly,
            BigDecimal storageFeeDailyRate, BigDecimal estimatedStorageFee
    ) {
        static Terminal from(TerminalAcceptance acceptance) {
            if (acceptance == null) {
                return null;
            }
            return new Terminal(acceptance.getId(), acceptance.getGateReceiptNumber(),
                    acceptance.getTerminalName(), acceptance.getAcceptedAt(),
                    acceptance.getEarliestAcceptanceDate(), acceptance.getVesselCutOffDate(),
                    acceptance.isStorageFeeApplies(), acceptance.getDaysEarly(),
                    acceptance.getStorageFeeDailyRate(), acceptance.estimatedStorageFee());
        }
    }

    public record Examination(
            UUID id, Instant holdPlacedAt, Instant examinationCompletedAt,
            ExaminationResult result, UUID originalSealId, UUID replacementSealId,
            String cbpOfficerId, String notes, boolean open
    ) {
        static Examination from(CBPExamination examination) {
            return new Examination(examination.getId(), examination.getHoldPlacedAt(),
                    examination.getExaminationCompletedAt(), examination.getResult(),
                    examination.getOriginalSealId(), examination.getReplacementSealId(),
                    examination.getCbpOfficerId(), examination.getNotes(), examination.isOpen());
        }
    }

    public record ActualCargo(
            UUID id, BigDecimal actualWeightKg, int actualPieces, BigDecimal actualCbm,
            BigDecimal bookedWeightKg, int bookedPieces, BigDecimal bookedCbm,
            BigDecimal weightVarianceKg, boolean divergesMaterially,
            Instant recordedAt, String recordedBy
    ) {
        static ActualCargo from(ActualCargoDetails details) {
            if (details == null) {
                return null;
            }
            return new ActualCargo(details.getId(), details.getActualWeightKg(),
                    details.getActualPieces(), details.getActualCbm(),
                    details.getBookedWeightKg(), details.getBookedPieces(), details.getBookedCbm(),
                    details.weightVarianceKg(), details.divergesMaterially(),
                    details.getRecordedAt(), details.getRecordedBy());
        }
    }

    /**
     * @param inboundBlockedReason why the inbound truck cannot go yet, or null when
     *                             it can. The ITN gate lives behind this.
     */
    public record Logistics(
            UUID id,
            UUID bookingId,
            String containerNumber,
            ContainerType containerType,
            ContainerSource source,
            Instant assignedAt,
            String assignedBy,
            LogisticsStage stage,
            boolean itnReceived,
            String itnNumber,
            String inboundBlockedReason,
            boolean documentationPreconditionsMet,
            Instant loadingCompletedAt,
            Instant loadedOnVesselAt,
            Instant vesselDepartedAt,
            Instant createdAt,
            String activeSealNumber,
            List<Seal> sealRecords,
            List<Dispatch> dispatches,
            List<Examination> examinations,
            Terminal terminalAcceptance,
            ActualCargo actualCargoDetails
    ) {
        public static Logistics from(ContainerAssignment assignment) {
            return new Logistics(
                    assignment.getId(),
                    assignment.getBookingId(),
                    assignment.getContainerNumber(),
                    assignment.getContainerType(),
                    assignment.getSource(),
                    assignment.getAssignedAt(),
                    assignment.getAssignedBy(),
                    assignment.getStage(),
                    assignment.isItnReceived(),
                    assignment.getItnNumber(),
                    assignment.inboundBlockedReason(),
                    assignment.documentationPreconditionsMet(),
                    assignment.getLoadingCompletedAt(),
                    assignment.getLoadedOnVesselAt(),
                    assignment.getVesselDepartedAt(),
                    assignment.getCreatedAt(),
                    assignment.activeSeal().map(SealRecord::getSealNumber).orElse(null),
                    assignment.getSealRecords().stream().map(Seal::from).toList(),
                    assignment.getDispatches().stream().map(Dispatch::from).toList(),
                    assignment.getExaminations().stream().map(Examination::from).toList(),
                    Terminal.from(assignment.getTerminalAcceptance()),
                    ActualCargo.from(assignment.getActualCargoDetails()));
        }
    }
}
