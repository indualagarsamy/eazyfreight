package com.eazyfreight.logistics.domain;

import com.eazyfreight.booking.domain.ContainerType;
import com.eazyfreight.logistics.event.LogisticsEvent;

import com.eazyfreight.exception.DomainRuleViolationException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.AbstractAggregateRoot;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Container logistics aggregate root — one physical container's journey.
 *
 * <p>Three rules justify the shape:
 *
 * <ul>
 *   <li><b>Container numbers are late-binding.</b> Nothing is known at booking
 *       time; the number arrives when the driver reaches the yard, and is
 *       immutable once recorded.</li>
 *   <li><b>The ITN gates the inbound movement.</b> {@link #dispatchInbound} refuses
 *       until Compliance has reported an accepted filing. Sending the container to
 *       the terminal without one risks rejection and storage fees, which is
 *       precisely what happens in the monolith because nothing checks.</li>
 *   <li><b>Seals accumulate, they do not overwrite.</b> A customs re-seal
 *       deactivates the previous record with its reason and links the successor.</li>
 * </ul>
 */
@Entity
@Table(name = "container_assignments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContainerAssignment extends AbstractAggregateRoot<ContainerAssignment>
        implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew = true;

    @Column(name = "booking_id", nullable = false, unique = true)
    private UUID bookingId;

    /** Null until the driver reports it from the carrier yard. */
    @Column(name = "container_number", length = 24)
    private String containerNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "container_type", length = 16)
    private ContainerType containerType;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "assigned_by", length = 64)
    private String assignedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 24)
    private ContainerSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 32)
    private LogisticsStage stage;

    /** Set by an event from the Compliance context when CBP accepts the filing. */
    @Column(name = "itn_received", nullable = false)
    private boolean itnReceived;

    @Column(name = "itn_number", length = 16)
    private String itnNumber;

    @Column(name = "loading_completed_at")
    private Instant loadingCompletedAt;

    @Column(name = "loaded_on_vessel_at")
    private Instant loadedOnVesselAt;

    @Column(name = "vessel_departed_at")
    private Instant vesselDepartedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "assignment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("issuedAt")
    private List<SealRecord> sealRecords = new ArrayList<>();

    @OneToMany(mappedBy = "assignment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("dispatchedAt")
    private List<TruckDispatch> dispatches = new ArrayList<>();

    @OneToMany(mappedBy = "assignment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("holdPlacedAt")
    private List<CBPExamination> examinations = new ArrayList<>();

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "terminal_acceptance_id")
    private TerminalAcceptance terminalAcceptance;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "actual_cargo_details_id")
    private ActualCargoDetails actualCargoDetails;

    // ----------------------------------------------------------------- opening

    public static ContainerAssignment open(UUID bookingId, ContainerType containerType, Instant now) {
        ContainerAssignment assignment = new ContainerAssignment();
        assignment.id = UUID.randomUUID();
        assignment.bookingId = bookingId;
        assignment.containerType = containerType;
        assignment.stage = LogisticsStage.NOT_STARTED;
        assignment.createdAt = now;
        return assignment;
    }

    // ------------------------------------------------------ 1. outbound truck

    /**
     * The reference is supplied lazily and drawn only once the guards pass. The
     * generator commits in its own transaction, so taking one up front would
     * consume a TDO number on every rejected attempt.
     */
    public TruckDispatch dispatchOutbound(
            Supplier<String> tdoReference, UUID driverId, UUID truckingVendorId, String vehicleReference,
            String yardAddress, String customerAddress,
            Instant scheduledPickup, Instant scheduledDelivery, Instant now, String actor
    ) {
        if (outboundDispatch().isPresent()) {
            throw new DomainRuleViolationException("The outbound truck has already been dispatched");
        }
        String reference = tdoReference.get();
        TruckDispatch dispatch = TruckDispatch.dispatch(this, bookingId, MovementType.OUTBOUND,
                reference, driverId, truckingVendorId, vehicleReference,
                yardAddress, AddressType.CARRIER_YARD,
                customerAddress, AddressType.CUSTOMER_PREMISES,
                scheduledPickup, scheduledDelivery, now, actor);
        dispatches.add(dispatch);
        advanceTo(LogisticsStage.OUTBOUND_DISPATCHED);
        registerEvent(new LogisticsEvent.TruckDeliveryOrderDispatched(
                bookingId, MovementType.OUTBOUND, reference, now));
        return dispatch;
    }

    // ---------------------------------------------------- 2. container number

    /** Recorded when the driver reaches the yard. Immutable thereafter. */
    public void recordContainerNumber(
            String containerNumber, ContainerType type, ContainerSource source,
            Instant now, String actor) {
        if (this.containerNumber != null) {
            throw new DomainRuleViolationException(
                    "Container " + this.containerNumber + " is already assigned and cannot be changed");
        }
        this.containerNumber = containerNumber;
        if (type != null) {
            this.containerType = type;
        }
        this.source = source;
        this.assignedAt = now;
        this.assignedBy = actor;
        outboundDispatch().ifPresent(dispatch -> dispatch.recordPickedUp(now));
        registerEvent(new LogisticsEvent.ContainerNumberAssigned(bookingId, containerNumber, now));
    }

    // ------------------------------------------- 3-4. delivery and loading

    public void recordDeliveredToCustomer(Instant now) {
        requireContainer("recording delivery to the customer");
        outboundDispatch()
                .orElseThrow(() -> new DomainRuleViolationException("No outbound dispatch exists"))
                .recordDelivered(now, null);
        advanceTo(LogisticsStage.AT_CUSTOMER);
        registerEvent(new LogisticsEvent.ContainerDeliveredToCustomer(bookingId, now));
    }

    public void recordLoadingComplete(Instant now) {
        requireStage("Loading can only complete once the container is with the customer",
                LogisticsStage.AT_CUSTOMER);
        this.loadingCompletedAt = now;
        advanceTo(LogisticsStage.LOADING_COMPLETE);
        registerEvent(new LogisticsEvent.CustomerLoadingComplete(bookingId, now));
    }

    // ---------------------------------------------------------------- 5. seal

    public void recordSealNumber(String sealNumber, Instant now, String actor) {
        if (activeSeal().isPresent()) {
            throw new DomainRuleViolationException(
                    "Seal " + activeSeal().orElseThrow().getSealNumber()
                            + " is already on the container — record a replacement instead");
        }
        sealRecords.add(SealRecord.issued(
                this, bookingId, sealNumber, SealSource.CUSTOMER_ISSUED, now, actor));
        advanceTo(LogisticsStage.SEALED);
        registerEvent(new LogisticsEvent.SealNumberIssued(
                bookingId, sealNumber, SealSource.CUSTOMER_ISSUED, now));
        raisePreconditionsMetIfReady(now);
    }

    /**
     * Stands the current seal down. Split from {@link #attachReplacementSeal} so the
     * caller can flush between the two: PostgreSQL allows one active seal per
     * booking, and Hibernate orders inserts ahead of updates, so writing the
     * replacement before the predecessor is deactivated trips the index.
     */
    public SealRecord deactivateActiveSeal(SealDeactivationReason reason, Instant now) {
        SealRecord previous = activeSeal().orElseThrow(() -> new DomainRuleViolationException(
                "There is no active seal to replace"));
        previous.deactivate(reason, now);
        return previous;
    }

    /** Fits the new seal and links it to the one it replaced. */
    public SealRecord attachReplacementSeal(
            SealRecord previous, String newSealNumber, SealSource source,
            Instant now, String actor) {
        SealRecord replacement = SealRecord.issued(this, bookingId, newSealNumber, source, now, actor);
        previous.replacedBy(replacement.getId());
        sealRecords.add(replacement);
        registerEvent(new LogisticsEvent.SealNumberReplaced(
                bookingId, previous.getSealNumber(), newSealNumber,
                previous.getDeactivationReason(), now));
        return replacement;
    }

    // ------------------------------------------------- 6-7. ITN gate, inbound

    /** Told by the Compliance context that CBP has issued an ITN for this booking. */
    public void recordItnReceived(String itnNumber, Instant now) {
        this.itnReceived = true;
        this.itnNumber = itnNumber;
        raisePreconditionsMetIfReady(now);
    }

    /** Whether the inbound movement may be authorised, and why not if it may not. */
    public String inboundBlockedReason() {
        if (!itnReceived) {
            return "ITN not yet received from CBP — the terminal can reject the container";
        }
        if (activeSeal().isEmpty()) {
            return "Container is not sealed";
        }
        if (containerNumber == null) {
            return "Container number is not known";
        }
        if (inboundDispatch().isPresent()) {
            return "The inbound truck has already been dispatched";
        }
        return null;
    }

    /**
     * Business rule 5: the inbound dispatch is refused until the ITN is in hand.
     * This is the gate the monolith does not have.
     */
    public TruckDispatch dispatchInbound(
            Supplier<String> tdoReference, UUID driverId, UUID truckingVendorId, String vehicleReference,
            String customerAddress, String terminalAddress,
            Instant scheduledPickup, Instant scheduledDelivery, Instant now, String actor
    ) {
        String blocked = inboundBlockedReason();
        if (blocked != null) {
            registerEvent(new LogisticsEvent.InboundDispatchBlocked(bookingId, blocked, now));
            throw new DomainRuleViolationException("Inbound dispatch refused: " + blocked);
        }

        String reference = tdoReference.get();
        TruckDispatch dispatch = TruckDispatch.dispatch(this, bookingId, MovementType.INBOUND,
                reference, driverId, truckingVendorId, vehicleReference,
                customerAddress, AddressType.CUSTOMER_PREMISES,
                terminalAddress, AddressType.PORT_TERMINAL,
                scheduledPickup, scheduledDelivery, now, actor);
        dispatches.add(dispatch);
        advanceTo(LogisticsStage.INBOUND_DISPATCHED);
        registerEvent(new LogisticsEvent.TruckDeliveryOrderDispatched(
                bookingId, MovementType.INBOUND, reference, now));
        return dispatch;
    }

    public void recordLoadedContainerPickedUp(Instant now) {
        inboundDispatch()
                .orElseThrow(() -> new DomainRuleViolationException("No inbound dispatch exists"))
                .recordPickedUp(now);
    }

    public void recordDeliveredToPort(Instant now) {
        inboundDispatch()
                .orElseThrow(() -> new DomainRuleViolationException("No inbound dispatch exists"))
                .recordDelivered(now, null);
        registerEvent(new LogisticsEvent.ContainerReturnedToPort(bookingId, now));
    }

    // -------------------------------------------------- 8-9. terminal gate

    public void recordTerminalGateReceipt(
            String gateReceiptNumber, String terminalName, LocalDate earliestAcceptanceDate,
            LocalDate vesselCutOffDate, BigDecimal storageFeeDailyRate, Instant now) {
        requireContainer("recording a terminal gate receipt");
        this.terminalAcceptance = TerminalAcceptance.record(
                bookingId, containerNumber, gateReceiptNumber, terminalName,
                now, earliestAcceptanceDate, vesselCutOffDate, storageFeeDailyRate);
        inboundDispatch().ifPresent(dispatch -> {
            if (dispatch.getStatus() != DispatchStatus.DELIVERED) {
                dispatch.recordDelivered(now, gateReceiptNumber);
            }
        });
        advanceTo(LogisticsStage.AT_TERMINAL);
        registerEvent(new LogisticsEvent.TerminalAccepted(
                bookingId, gateReceiptNumber, terminalAcceptance.isStorageFeeApplies(),
                terminalAcceptance.estimatedStorageFee(), now));
    }

    public void recordTerminalGateRejection(String reason, Instant now) {
        inboundDispatch().ifPresent(dispatch -> dispatch.recordFailed(reason));
        registerEvent(new LogisticsEvent.TerminalRejected(bookingId, reason, now));
    }

    // ------------------------------------------------ 10-11. CBP examination

    public CBPExamination recordExaminationHold(String cbpOfficerId, String notes, Instant now) {
        CBPExamination examination = CBPExamination.holdPlaced(this, bookingId, containerNumber,
                activeSeal().map(SealRecord::getId).orElse(null), cbpOfficerId, now, notes);
        examinations.add(examination);
        advanceTo(LogisticsStage.UNDER_CBP_EXAMINATION);
        registerEvent(new LogisticsEvent.ContainerHeldForExamination(
                bookingId, containerNumber, cbpOfficerId, now));
        return examination;
    }

    public CBPExamination openExamination() {
        return examinations.stream()
                .filter(CBPExamination::isOpen)
                .reduce((first, second) -> second)
                .orElseThrow(() -> new DomainRuleViolationException("No open CBP examination"));
    }

    /**
     * Closes the examination. The replacement seal is fitted by the caller before
     * this is called, because CBP always cuts the original.
     */
    public void recordExaminationRelease(
            ExaminationResult result, UUID replacementSealId,
            String notes, Instant now) {
        openExamination().complete(result, replacementSealId, now, notes);
        if (result == ExaminationResult.RELEASED) {
            advanceTo(LogisticsStage.AT_TERMINAL);
        }
        registerEvent(new LogisticsEvent.ContainerReleasedFromExamination(bookingId, result, now));
    }

    // ------------------------------------------- 12-13. vessel loading

    public void recordLoadedOnVessel(String vesselName, Instant now) {
        this.loadedOnVesselAt = now;
        advanceTo(LogisticsStage.LOADED_ON_VESSEL);
        registerEvent(new LogisticsEvent.ContainerLoadedOnVessel(bookingId, vesselName, now));
    }

    public void recordVesselDeparted(Instant now) {
        this.vesselDepartedAt = now;
        advanceTo(LogisticsStage.DEPARTED);
        registerEvent(new LogisticsEvent.VesselDeparted(bookingId, now));
    }

    // ------------------------------------------------------ 14. actual cargo

    /**
     * Records what was actually loaded. If it diverges materially from the booking
     * and an EEI is already on file, CBP is owed an amendment.
     */
    public void recordActualCargo(
            BigDecimal actualWeightKg, int actualPieces, BigDecimal actualCbm,
            BigDecimal bookedWeightKg, int bookedPieces, BigDecimal bookedCbm,
            Instant now, String actor) {
        this.actualCargoDetails = ActualCargoDetails.record(bookingId,
                actualWeightKg, actualPieces, actualCbm,
                bookedWeightKg, bookedPieces, bookedCbm, now, actor);

        registerEvent(new LogisticsEvent.ActualCargoDetailsRecorded(
                bookingId, actualWeightKg, actualPieces, actualCbm, now));

        if (itnReceived && actualCargoDetails.divergesMaterially()) {
            registerEvent(new LogisticsEvent.AesAmendmentRequired(
                    bookingId, actualCargoDetails.weightVarianceKg(), now));
        }
    }

    // ----------------------------------------------------------------- queries

    public Optional<SealRecord> activeSeal() {
        return sealRecords.stream().filter(SealRecord::isActive).findFirst();
    }

    public Optional<TruckDispatch> outboundDispatch() {
        return dispatches.stream()
                .filter(dispatch -> dispatch.getMovementType() == MovementType.OUTBOUND)
                .findFirst();
    }

    public Optional<TruckDispatch> inboundDispatch() {
        return dispatches.stream()
                .filter(dispatch -> dispatch.getMovementType() == MovementType.INBOUND)
                .filter(dispatch -> dispatch.getStatus() != DispatchStatus.FAILED)
                .findFirst();
    }

    public List<SealRecord> getSealRecords() {
        return Collections.unmodifiableList(sealRecords);
    }

    public List<TruckDispatch> getDispatches() {
        return Collections.unmodifiableList(dispatches);
    }

    public List<CBPExamination> getExaminations() {
        return Collections.unmodifiableList(examinations);
    }

    /** The three things the Documentation track waits on from this context and Compliance. */
    public boolean documentationPreconditionsMet() {
        return containerNumber != null && activeSeal().isPresent() && itnReceived;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    public java.util.Collection<Object> pendingEvents() {
        return domainEvents();
    }

    // ----------------------------------------------------------------- helpers

    private void raisePreconditionsMetIfReady(Instant now) {
        if (documentationPreconditionsMet()) {
            registerEvent(new LogisticsEvent.DocumentationPreconditionsMet(
                    bookingId, containerNumber, activeSeal().orElseThrow().getSealNumber(), now));
        }
    }

    /** Stages only move forward; a CBP hold is the one case that returns to a prior stage. */
    private void advanceTo(LogisticsStage target) {
        this.stage = target;
    }

    private void requireContainer(String action) {
        if (containerNumber == null) {
            throw new DomainRuleViolationException(
                    "Container number is not known yet — required before " + action);
        }
    }

    private void requireStage(String message, LogisticsStage expected) {
        if (stage != expected) {
            throw new DomainRuleViolationException(message + " (stage was " + stage + ")");
        }
    }
}
