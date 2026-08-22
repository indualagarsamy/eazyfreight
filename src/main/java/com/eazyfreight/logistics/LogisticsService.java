package com.eazyfreight.logistics;

import com.eazyfreight.booking.Booking;
import com.eazyfreight.booking.BookingCargoDetail;
import com.eazyfreight.booking.BookingRepository;
import com.eazyfreight.booking.BookingStatus;
import com.eazyfreight.common.ReferenceGenerator;
import com.eazyfreight.exception.BookingNotFoundException;
import com.eazyfreight.exception.DomainRuleViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Application service for the Container and Equipment context — the seventeen
 * commands from the specification.
 */
@Service
@RequiredArgsConstructor
public class LogisticsService {

    private final ContainerAssignmentRepository repository;
    private final BookingRepository bookingRepository;
    private final ReferenceGenerator referenceGenerator;
    private final Clock clock;

    // ------------------------------------------------------ 1. outbound truck

    @Transactional
    public LogisticsResponses.Logistics dispatchOutbound(
            UUID bookingId, LogisticsRequests.DispatchTruck request, String actor) {
        Booking booking = requireConfirmedBooking(bookingId);
        ContainerAssignment assignment = repository.findByBookingId(bookingId)
                .orElseGet(() -> ContainerAssignment.open(
                        bookingId,
                        booking.getCarrierBooking() == null
                                ? null : booking.getCarrierBooking().getContainerType(),
                        clock.instant()));

        assignment.dispatchOutbound(
                () -> dispatchReference(MovementType.OUTBOUND),
                request.driverId(), request.truckingVendorId(), request.vehicleReference(),
                request.pickupAddress(), request.deliveryAddress(),
                request.scheduledPickupDate(), request.scheduledDeliveryDate(),
                clock.instant(), actor);
        return save(assignment);
    }

    // ---------------------------------------------------- 2. container number

    @Transactional
    public LogisticsResponses.Logistics recordContainerNumber(
            UUID bookingId, LogisticsRequests.RecordContainerNumber request, String actor) {
        ContainerAssignment assignment = get(bookingId);
        assignment.recordContainerNumber(
                request.containerNumber(), request.containerType(),
                request.source() == null ? ContainerSource.CARRIER_YARD : request.source(),
                clock.instant(), actor);
        return save(assignment);
    }

    // ------------------------------------------------- 3-4. delivery, loading

    @Transactional
    public LogisticsResponses.Logistics recordDeliveredToCustomer(UUID bookingId) {
        ContainerAssignment assignment = get(bookingId);
        assignment.recordDeliveredToCustomer(clock.instant());
        return save(assignment);
    }

    @Transactional
    public LogisticsResponses.Logistics recordLoadingComplete(UUID bookingId) {
        ContainerAssignment assignment = get(bookingId);
        assignment.recordLoadingComplete(clock.instant());
        return save(assignment);
    }

    // ---------------------------------------------------------------- 5. seal

    @Transactional
    public LogisticsResponses.Logistics recordSeal(
            UUID bookingId, LogisticsRequests.RecordSeal request, String actor) {
        ContainerAssignment assignment = get(bookingId);
        assignment.recordSealNumber(request.sealNumber(), clock.instant(), actor);
        return save(assignment);
    }

    // --------------------------------------------------------- 6-7. ITN gate

    /** Reports whether the inbound movement may go, and why not if it may not. */
    @Transactional(readOnly = true)
    public String checkItnGate(UUID bookingId) {
        return get(bookingId).inboundBlockedReason();
    }

    @Transactional
    public LogisticsResponses.Logistics dispatchInbound(
            UUID bookingId, LogisticsRequests.DispatchTruck request, String actor) {
        ContainerAssignment assignment = get(bookingId);
        assignment.dispatchInbound(
                () -> dispatchReference(MovementType.INBOUND),
                request.driverId(), request.truckingVendorId(), request.vehicleReference(),
                request.pickupAddress(), request.deliveryAddress(),
                request.scheduledPickupDate(), request.scheduledDeliveryDate(),
                clock.instant(), actor);
        return save(assignment);
    }

    @Transactional
    public LogisticsResponses.Logistics recordLoadedContainerPickedUp(UUID bookingId) {
        ContainerAssignment assignment = get(bookingId);
        assignment.recordLoadedContainerPickedUp(clock.instant());
        return save(assignment);
    }

    @Transactional
    public LogisticsResponses.Logistics recordDeliveredToPort(UUID bookingId) {
        ContainerAssignment assignment = get(bookingId);
        assignment.recordDeliveredToPort(clock.instant());
        return save(assignment);
    }

    // ------------------------------------------------------- 8-9. terminal

    @Transactional
    public LogisticsResponses.Logistics recordTerminalGateReceipt(
            UUID bookingId, LogisticsRequests.TerminalGateReceipt request) {
        ContainerAssignment assignment = get(bookingId);
        assignment.recordTerminalGateReceipt(
                request.gateReceiptNumber(), request.terminalName(),
                request.earliestAcceptanceDate(), request.vesselCutOffDate(),
                request.storageFeeDailyRate(), clock.instant());
        return save(assignment);
    }

    @Transactional
    public LogisticsResponses.Logistics recordTerminalGateRejection(
            UUID bookingId, LogisticsRequests.TerminalGateRejection request) {
        ContainerAssignment assignment = get(bookingId);
        assignment.recordTerminalGateRejection(request.reason(), clock.instant());
        return save(assignment);
    }

    // ------------------------------------------------- 10-11. CBP examination

    @Transactional
    public LogisticsResponses.Logistics recordExaminationHold(
            UUID bookingId, LogisticsRequests.ExaminationHold request) {
        ContainerAssignment assignment = get(bookingId);
        assignment.recordExaminationHold(request.cbpOfficerId(), request.notes(), clock.instant());
        return save(assignment);
    }

    @Transactional
    public LogisticsResponses.Logistics recordExaminationRelease(
            UUID bookingId, LogisticsRequests.ExaminationRelease request, String actor) {
        ContainerAssignment assignment = get(bookingId);
        if (request.result() == ExaminationResult.RELEASED
                && (request.replacementSealNumber() == null || request.replacementSealNumber().isBlank())) {
            throw new DomainRuleViolationException(
                    "CBP cut the original seal — a replacement seal number is required on release");
        }
        UUID replacementSealId = null;
        if (request.result() == ExaminationResult.RELEASED) {
            replacementSealId = swapSeal(assignment, request.replacementSealNumber(),
                    SealSource.CUSTOMS_ISSUED, SealDeactivationReason.CUSTOMS_INSPECTION, actor);
        }
        assignment.recordExaminationRelease(
                request.result(), replacementSealId, request.notes(), clock.instant());
        return save(assignment);
    }

    /** 13. RecordSealReplacement, for a damaged seal outside an examination. */
    @Transactional
    public LogisticsResponses.Logistics replaceSeal(
            UUID bookingId, LogisticsRequests.RecordSeal request, String actor) {
        ContainerAssignment assignment = get(bookingId);
        swapSeal(assignment, request.sealNumber(), SealSource.CUSTOMER_ISSUED,
                SealDeactivationReason.DAMAGED_SEAL, actor);
        return save(assignment);
    }

    // ------------------------------------------------------ 15-16. vessel

    @Transactional
    public LogisticsResponses.Logistics recordLoadedOnVessel(
            UUID bookingId, LogisticsRequests.LoadedOnVessel request) {
        ContainerAssignment assignment = get(bookingId);
        assignment.recordLoadedOnVessel(request.vesselName(), clock.instant());
        return save(assignment);
    }

    @Transactional
    public LogisticsResponses.Logistics recordVesselDeparted(UUID bookingId) {
        ContainerAssignment assignment = get(bookingId);
        assignment.recordVesselDeparted(clock.instant());
        return save(assignment);
    }

    // ------------------------------------------------------ 17. actual cargo

    @Transactional
    public LogisticsResponses.Logistics recordActualCargo(
            UUID bookingId, LogisticsRequests.ActualCargo request, String actor) {
        ContainerAssignment assignment = get(bookingId);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        BigDecimal bookedWeight = booking.totalWeightKg();
        int bookedPieces = booking.getCargoDetails().stream()
                .mapToInt(BookingCargoDetail::getPieces).sum();
        BigDecimal bookedCbm = booking.getCargoDetails().stream()
                .map(BookingCargoDetail::getCbm)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assignment.recordActualCargo(
                request.actualWeightKg(), request.actualPieces(), request.actualCbm(),
                bookedWeight, bookedPieces, bookedCbm, clock.instant(), actor);
        return save(assignment);
    }

    /** Told by the Compliance context that CBP has issued an ITN. Opens the inbound gate. */
    @Transactional
    public void recordItnReceived(UUID bookingId, String itnNumber) {
        repository.findByBookingId(bookingId).ifPresent(assignment -> {
            assignment.recordItnReceived(itnNumber, clock.instant());
            repository.save(assignment);
        });
    }

    // ----------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public List<LogisticsResponses.Logistics> findAll() {
        return repository.findAll().stream().map(LogisticsResponses.Logistics::from).toList();
    }

    @Transactional(readOnly = true)
    public LogisticsResponses.Logistics findByBooking(UUID bookingId) {
        return LogisticsResponses.Logistics.from(get(bookingId));
    }

    /** Sealed and ready to move, but stuck behind the ITN. */
    @Transactional(readOnly = true)
    public List<LogisticsResponses.Logistics> findBlockedOnItn() {
        return repository.findByStageAndItnReceivedFalse(LogisticsStage.SEALED).stream()
                .map(LogisticsResponses.Logistics::from)
                .toList();
    }

    /**
     * Confirmed bookings that need a truck and have no outbound dispatch.
     *
     * <p>This lives here rather than on the booking, because trucking belongs to
     * this context — the booking only records that we arrange it and where from.
     */
    @Transactional(readOnly = true)
    public List<LogisticsResponses.AwaitingDispatch> findAwaitingOutboundDispatch() {
        return bookingRepository
                .findByStatusInAndTransportRequiredTrue(List.of(
                        BookingStatus.CONFIRMED_BY_CARRIER, BookingStatus.CUSTOMER_CONFIRMED))
                .stream()
                .filter(booking -> repository.findByBookingId(booking.getId())
                        .flatMap(ContainerAssignment::outboundDispatch).isEmpty())
                .map(booking -> new LogisticsResponses.AwaitingDispatch(
                        booking.getId(), booking.getBookingReference(),
                        booking.getRequestedEtd(),
                        booking.getCarrierBooking() == null
                                ? null : booking.getCarrierBooking().getConfirmedEtd(),
                        booking.getPickupAddress()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LogisticsResponses.Logistics> findUnderExamination() {
        return repository.findByStage(LogisticsStage.UNDER_CBP_EXAMINATION).stream()
                .map(LogisticsResponses.Logistics::from)
                .toList();
    }

    // ----------------------------------------------------------------- helpers

    /**
     * Replaces the active seal, flushing the deactivation before the replacement is
     * written. PostgreSQL permits one active seal per booking and Hibernate orders
     * inserts ahead of updates, so without the flush the index rejects the insert.
     */
    private UUID swapSeal(
            ContainerAssignment assignment, String newSealNumber,
            SealSource source, SealDeactivationReason reason, String actor) {
        SealRecord previous = assignment.deactivateActiveSeal(reason, clock.instant());
        repository.saveAndFlush(assignment);
        return assignment.attachReplacementSeal(
                previous, newSealNumber, source, clock.instant(), actor).getId();
    }

    private String dispatchReference(MovementType movementType) {
        String base = referenceGenerator.next(ReferenceGenerator.DISPATCH_PREFIX);
        return base + (movementType == MovementType.OUTBOUND ? "-OUT" : "-IN");
    }

    private Booking requireConfirmedBooking(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        boolean confirmed = booking.getStatus() == BookingStatus.CONFIRMED_BY_CARRIER
                || booking.getStatus() == BookingStatus.CUSTOMER_CONFIRMED;
        if (!confirmed) {
            throw new DomainRuleViolationException(
                    "No truck is dispatched before the carrier confirms space (booking is "
                            + booking.getStatus() + ")");
        }
        return booking;
    }

    private ContainerAssignment get(UUID bookingId) {
        return repository.findByBookingId(bookingId)
                .orElseThrow(() -> new LogisticsNotFoundException(bookingId));
    }

    private LogisticsResponses.Logistics save(ContainerAssignment assignment) {
        return LogisticsResponses.Logistics.from(repository.save(assignment));
    }
}
