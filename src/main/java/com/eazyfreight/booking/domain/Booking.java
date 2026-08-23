package com.eazyfreight.booking.domain;

import com.eazyfreight.booking.event.BookingEvent;

import com.eazyfreight.common.BusinessDays;
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
import jakarta.persistence.OrderBy;
import jakarta.persistence.OneToOne;
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
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Booking aggregate root.
 *
 * <p>Three things this class exists to guarantee, each of which the monolith failed:
 *
 * <ul>
 *   <li><b>Identity survives.</b> A reinstated booking keeps its reference and its
 *       row. The sailing it left is written to {@link BookingReinstatement} before
 *       the new one is applied, so the original ETD is never overwritten into
 *       nothing.</li>
 *   <li><b>Every move is recorded.</b> Status only changes through
 *       {@link #transitionTo}, which appends an immutable history entry naming the
 *       actor, the reason and the source.</li>
 *   <li><b>Gates are enforced, not remembered.</b> A carrier reference cannot be
 *       recorded before submission, and a truck cannot be dispatched before the
 *       carrier confirms.</li>
 * </ul>
 */
@Entity
@Table(name = "bookings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking extends AbstractAggregateRoot<Booking> implements Persistable<UUID> {

    /** Rule 5: an ETD moving more than this many business days needs a customer conversation. */
    private static final int ETD_VARIANCE_NOTIFICATION_THRESHOLD_BUSINESS_DAYS = 2;

    private static final int MIN_ETD_LEAD_BUSINESS_DAYS = 5;

    /**
     * Assigned in the factory rather than by the database, so that the creation
     * event can carry the identity of the thing that was created.
     */
    @Id
    private UUID id;

    @Transient
    private boolean isNew = true;

    @Column(name = "booking_reference", nullable = false, unique = true, length = 32)
    private String bookingReference;

    @Column(name = "quote_id")
    private UUID quoteId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "shipper_id", nullable = false)
    private UUID shipperId;

    @Column(name = "consignee_id", nullable = false)
    private UUID consigneeId;

    @Column(name = "notify_party_id")
    private UUID notifyPartyId;

    @Column(name = "also_notify_id")
    private UUID alsoNotifyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private BookingStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "shipping_mode", nullable = false, length = 16)
    private ShippingMode shippingMode;

    @Column(name = "origin_port_code", nullable = false, length = 8)
    private String originPortCode;

    @Column(name = "destination_port_code", nullable = false, length = 8)
    private String destinationPortCode;

    @Column(name = "incoterms", nullable = false, length = 8)
    private String incoterms;

    @Column(name = "requested_etd", nullable = false)
    private LocalDate requestedEtd;

    @Column(name = "requested_eta")
    private LocalDate requestedEta;

    @Column(name = "transport_required", nullable = false)
    private boolean transportRequired;

    @Column(name = "pickup_address")
    private String pickupAddress;

    @Column(name = "pickup_date_time")
    private Instant pickupDateTime;

    @Column(name = "special_instructions")
    private String specialInstructions;

    @Column(name = "marks_and_numbers")
    private String marksAndNumbers;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_initiated_by", length = 32)
    private CancellationInitiator cancellationInitiatedBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    /** Set when ops confirms the customer was told about a material ETD change. */
    @Column(name = "etd_variance_acknowledged_at")
    private Instant etdVarianceAcknowledgedAt;

    @Column(name = "confirmation_sent_at")
    private Instant confirmationSentAt;

    /** True once compliance reports an ITN# on file, so reinstatement knows to raise an amendment. */
    @Column(name = "itn_filed", nullable = false)
    private boolean itnFiled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "last_modified_at", nullable = false)
    private Instant lastModifiedAt;

    @Column(name = "last_modified_by", nullable = false, length = 64)
    private String lastModifiedBy;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "carrier_booking_id")
    private CarrierBooking carrierBooking;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<BookingCargoDetail> cargoDetails = new ArrayList<>();

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sequenceNumber")
    private List<BookingStatusHistory> statusHistory = new ArrayList<>();

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("reinstatedAt")
    private List<BookingReinstatement> reinstatements = new ArrayList<>();

    // ---------------------------------------------------------------- creation

    public static Booking request(
            String bookingReference,
            UUID quoteId,
            UUID customerId,
            UUID shipperId,
            UUID consigneeId,
            UUID notifyPartyId,
            UUID alsoNotifyId,
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
            LocalDate today,
            Instant now,
            String actor
    ) {
        if (originPortCode.equalsIgnoreCase(destinationPortCode)) {
            throw new DomainRuleViolationException(
                    "Origin and destination port must differ (both were " + originPortCode + ")");
        }
        LocalDate earliestEtd = BusinessDays.add(today, MIN_ETD_LEAD_BUSINESS_DAYS);
        if (requestedEtd.isBefore(earliestEtd)) {
            throw new DomainRuleViolationException(
                    "requestedEtd must be at least " + MIN_ETD_LEAD_BUSINESS_DAYS
                            + " business days out (earliest is " + earliestEtd + ")");
        }
        if (transportRequired && (pickupAddress == null || pickupAddress.isBlank())) {
            throw new DomainRuleViolationException(
                    "pickupAddress is required when Eazy Freight arranges transport");
        }

        Booking booking = new Booking();
        booking.id = UUID.randomUUID();
        booking.bookingReference = bookingReference;
        booking.quoteId = quoteId;
        booking.customerId = customerId;
        booking.shipperId = shipperId;
        booking.consigneeId = consigneeId;
        booking.notifyPartyId = notifyPartyId;
        booking.alsoNotifyId = alsoNotifyId;
        booking.status = BookingStatus.BOOKING_REQUESTED;
        booking.shippingMode = shippingMode;
        booking.originPortCode = originPortCode;
        booking.destinationPortCode = destinationPortCode;
        booking.incoterms = incoterms;
        booking.requestedEtd = requestedEtd;
        booking.requestedEta = requestedEta;
        booking.transportRequired = transportRequired;
        booking.pickupAddress = pickupAddress;
        booking.pickupDateTime = pickupDateTime;
        booking.specialInstructions = specialInstructions;
        booking.marksAndNumbers = marksAndNumbers;
        booking.createdAt = now;
        booking.createdBy = actor;
        booking.lastModifiedAt = now;
        booking.lastModifiedBy = actor;

        booking.statusHistory.add(BookingStatusHistory.record(
                booking, 1, null, BookingStatus.BOOKING_REQUESTED, now, actor,
                "Booking request created", StatusChangeSource.MANUAL));

        booking.registerEvent(new BookingEvent.BookingRequestCreated(
                booking.id, bookingReference, quoteId, customerId, shippingMode,
                originPortCode, destinationPortCode, requestedEtd, now));
        return booking;
    }

    public void addCargoDetail(BookingCargoDetail detail) {
        requireStatus("Cargo details can only be added before submission", BookingStatus.BOOKING_REQUESTED);
        detail.assignTo(this);
        cargoDetails.add(detail);
    }

    // -------------------------------------------------------------- submission

    /**
     * Sends the request to a carrier or co-loader.
     *
     * <p>Container payload is validated here rather than at confirmation, because a
     * booking that physically cannot be loaded should never reach the carrier.
     */
    public void submitTo(
            BookingSourceType sourceType,
            UUID carrierId,
            ContainerType containerType,
            Integer numberOfContainers,
            Instant now,
            String actor
    ) {
        requireStatus("Only a requested or rejected booking can be submitted",
                BookingStatus.BOOKING_REQUESTED, BookingStatus.REJECTED_BY_CARRIER);
        if (cargoDetails.isEmpty()) {
            throw new DomainRuleViolationException(
                    "Booking cannot be submitted without cargo details");
        }
        if (shippingMode == ShippingMode.OCEAN_FCL) {
            if (containerType == null) {
                throw new DomainRuleViolationException("containerType is required for OCEAN_FCL");
            }
            if (numberOfContainers == null || numberOfContainers < 1) {
                throw new DomainRuleViolationException("numberOfContainers must be at least 1 for OCEAN_FCL");
            }
            validatePayload(containerType, numberOfContainers);
        }

        this.carrierBooking = CarrierBooking.submittedTo(
                sourceType, carrierId, containerType, numberOfContainers, now);
        transitionTo(BookingStatus.SUBMITTED_TO_CARRIER, now, actor,
                "Submitted to " + sourceType, StatusChangeSource.MANUAL);

        registerEvent(new BookingEvent.BookingSubmittedToCarrier(
                id, bookingReference, carrierId, sourceType, now));
    }

    /**
     * Records the carrier's confirmation. The carrier reference cannot arrive before
     * submission — a guard the monolith's field-update method did not have.
     */
    public void recordCarrierConfirmation(
            String carrierBookingRef,
            String coLoaderBookingRef,
            String vesselName,
            String voyageNumber,
            LocalDate confirmedEtd,
            LocalDate confirmedEta,
            ContainerType containerType,
            Instant confirmedAt,
            Instant now,
            String actor
    ) {
        requireStatus("Only a submitted booking can be confirmed by the carrier",
                BookingStatus.SUBMITTED_TO_CARRIER, BookingStatus.COUNTER_OFFER_RECEIVED);
        if (carrierBookingRef == null || carrierBookingRef.isBlank()) {
            throw new DomainRuleViolationException(
                    "carrierBookingRef must be provided by the carrier to confirm a booking");
        }
        if (carrierBooking.getBookingSourceType() == BookingSourceType.CO_LOADER
                && (coLoaderBookingRef == null || coLoaderBookingRef.isBlank())) {
            throw new DomainRuleViolationException(
                    "coLoaderBookingRef must be recorded separately for a co-loader booking");
        }

        carrierBooking.recordConfirmation(carrierBookingRef, coLoaderBookingRef, vesselName, voyageNumber,
                confirmedEtd, confirmedEta, containerType, confirmedAt, actor);

        transitionTo(BookingStatus.CONFIRMED_BY_CARRIER, now, actor,
                "Carrier confirmed as " + carrierBookingRef, StatusChangeSource.MANUAL);

        registerEvent(new BookingEvent.CarrierBookingConfirmed(
                id, bookingReference, carrierBooking.getCarrierId(), carrierBooking.getBookingSourceType(),
                carrierBookingRef, coLoaderBookingRef, vesselName, voyageNumber,
                confirmedEtd, confirmedEta, requiresCustomerEtdNotification(), now));
        registerEvent(new BookingEvent.BookingConfirmedForInvoicing(id, bookingReference, customerId, now));
    }

    public void recordCarrierRejection(String reason, Instant now, String actor) {
        requireStatus("Only a submitted booking can be rejected by the carrier",
                BookingStatus.SUBMITTED_TO_CARRIER);
        UUID carrierId = carrierBooking == null ? null : carrierBooking.getCarrierId();
        transitionTo(BookingStatus.REJECTED_BY_CARRIER, now, actor, reason, StatusChangeSource.MANUAL);
        registerEvent(new BookingEvent.BookingRejectedByCarrier(
                id, bookingReference, carrierId, reason, now));
    }

    public void recordCounterOffer(
            String proposedVessel,
            String proposedVoyage,
            LocalDate proposedEtd,
            LocalDate proposedEta,
            Instant now,
            String actor
    ) {
        requireStatus("A counter-offer can only follow a submission", BookingStatus.SUBMITTED_TO_CARRIER);
        carrierBooking.applyCounterOffer(proposedVessel, proposedVoyage, proposedEtd, proposedEta);
        transitionTo(BookingStatus.COUNTER_OFFER_RECEIVED, now, actor,
                "Carrier proposed " + proposedVessel + " departing " + proposedEtd, StatusChangeSource.MANUAL);
        registerEvent(new BookingEvent.CarrierCounterOfferReceived(
                id, bookingReference, proposedVessel, proposedVoyage, proposedEtd, proposedEta, now));
    }

    /** Accepts the alternative sailing. The booking stays in submitted state awaiting the carrier reference. */
    public void acceptCounterOffer(Instant now, String actor) {
        requireStatus("No counter-offer is outstanding", BookingStatus.COUNTER_OFFER_RECEIVED);
        transitionTo(BookingStatus.SUBMITTED_TO_CARRIER, now, actor,
                "Counter-offer accepted", StatusChangeSource.MANUAL);
        registerEvent(new BookingEvent.CarrierCounterOfferAccepted(id, bookingReference, now));
    }

    public void rejectCounterOffer(Instant now, String actor) {
        requireStatus("No counter-offer is outstanding", BookingStatus.COUNTER_OFFER_RECEIVED);
        transitionTo(BookingStatus.REJECTED_BY_CARRIER, now, actor,
                "Counter-offer rejected — seeking alternative carrier", StatusChangeSource.MANUAL);
        registerEvent(new BookingEvent.CarrierCounterOfferRejected(id, bookingReference, now));
    }

    // ------------------------------------------------------------ confirmation

    /** Records that the customer was told about a material ETD change. Unblocks the confirmation. */
    public void acknowledgeEtdVariance(Instant now, String actor) {
        this.etdVarianceAcknowledgedAt = now;
        touch(now, actor);
    }

    /**
     * Issues the Company Booking Confirmation to the customer.
     *
     * <p>Refused while a material ETD change is unacknowledged — rule 5 makes that
     * conversation a precondition, so the system treats it as one.
     */
    public void sendConfirmationToCustomer(Instant now, String actor) {
        requireStatus("Only a carrier-confirmed booking can be confirmed to the customer",
                BookingStatus.CONFIRMED_BY_CARRIER, BookingStatus.CUSTOMER_CONFIRMED);
        if (requiresCustomerEtdNotification() && etdVarianceAcknowledgedAt == null) {
            throw new DomainRuleViolationException(
                    "Confirmed ETD " + carrierBooking.getConfirmedEtd() + " differs from requested ETD "
                            + requestedEtd + " by more than "
                            + ETD_VARIANCE_NOTIFICATION_THRESHOLD_BUSINESS_DAYS
                            + " business days — notify the customer and acknowledge before sending confirmation");
        }

        boolean reissued = confirmationSentAt != null;
        this.confirmationSentAt = now;
        if (status == BookingStatus.CONFIRMED_BY_CARRIER) {
            transitionTo(BookingStatus.CUSTOMER_CONFIRMED, now, actor,
                    "Booking confirmation sent to customer", StatusChangeSource.MANUAL);
        } else {
            touch(now, actor);
        }
        registerEvent(new BookingEvent.BookingConfirmationSentToCustomer(
                id, bookingReference, customerId, reissued, now));
    }

    // ------------------------------------------------ overbooking & lifecycle

    public void recordVesselOverbooking(Instant now, String actor) {
        requireStatus("Only a confirmed booking can be overbooked",
                BookingStatus.CONFIRMED_BY_CARRIER, BookingStatus.CUSTOMER_CONFIRMED);
        transitionTo(BookingStatus.VESSEL_OVERBOOKED, now, actor,
                "Carrier reported vessel overbooking", StatusChangeSource.INTTRA);
        registerEvent(new BookingEvent.VesselOverbookedNotified(
                id, bookingReference, carrierBooking.getVesselName(), carrierBooking.getConfirmedEtd(), now));
    }

    public void cancel(String reason, CancellationInitiator initiatedBy, Instant now, String actor) {
        if (status == BookingStatus.CANCELLED) {
            throw new DomainRuleViolationException("Booking " + bookingReference + " is already cancelled");
        }
        this.cancellationReason = reason;
        this.cancellationInitiatedBy = initiatedBy;
        this.cancelledAt = now;
        transitionTo(BookingStatus.CANCELLED, now, actor, reason, StatusChangeSource.MANUAL);
        registerEvent(new BookingEvent.BookingCancelled(id, bookingReference, reason, initiatedBy, now));
    }

    /**
     * Rolls the booking onto a later sailing, keeping its identity.
     *
     * <p>Permitted from VESSEL_OVERBOOKED, and from CANCELLED when the cancellation
     * was itself caused by overbooking — the specification describes both, so both
     * are accepted.
     */
    public void reinstate(
            String newVesselName,
            String newVoyageNumber,
            LocalDate newEtd,
            LocalDate newEta,
            String reason,
            LocalDate today,
            Instant now,
            String actor
    ) {
        boolean reinstatable = status == BookingStatus.VESSEL_OVERBOOKED
                || (status == BookingStatus.CANCELLED
                    && cancellationInitiatedBy == CancellationInitiator.CARRIER_OVERBOOKING);
        if (!reinstatable) {
            throw new DomainRuleViolationException(
                    "Only a booking overbooked by the carrier can be reinstated (status was " + status
                            + ", cancelled by " + cancellationInitiatedBy + ")");
        }
        LocalDate earliestEtd = BusinessDays.add(today, MIN_ETD_LEAD_BUSINESS_DAYS);
        if (newEtd.isBefore(earliestEtd)) {
            throw new DomainRuleViolationException(
                    "newEtd must be at least " + MIN_ETD_LEAD_BUSINESS_DAYS
                            + " business days out (earliest is " + earliestEtd + ")");
        }

        // Capture the sailing being left behind before the carrier booking is moved.
        BookingReinstatement reinstatement = BookingReinstatement.of(
                this, carrierBooking, newVesselName, newVoyageNumber, newEtd, newEta, now, actor, reason);
        reinstatements.add(reinstatement);

        carrierBooking.moveToSailing(newVesselName, newVoyageNumber, newEtd, newEta);
        this.cancellationReason = null;
        this.cancellationInitiatedBy = null;
        this.cancelledAt = null;
        // A new sailing is a new set of facts for the customer to be told about.
        this.etdVarianceAcknowledgedAt = null;

        transitionTo(BookingStatus.CONFIRMED_BY_CARRIER, now, actor,
                "Reinstated to " + newVesselName + " departing " + newEtd, StatusChangeSource.MANUAL);

        registerEvent(new BookingEvent.BookingReinstated(
                id, bookingReference, reinstatement.getPreviousVessel(), reinstatement.getPreviousEtd(),
                newVesselName, newVoyageNumber, newEtd, newEta, now));

        if (itnFiled) {
            registerEvent(new BookingEvent.ItnAmendmentRequired(
                    id, bookingReference, newEtd, newVesselName, now));
        }
    }

    /** Told by the Compliance track that an ITN# is on file against this booking. */
    public void markItnFiled(Instant now, String actor) {
        this.itnFiled = true;
        touch(now, actor);
    }

    // ----------------------------------------------------------------- queries

    public List<BookingCargoDetail> getCargoDetails() {
        return Collections.unmodifiableList(cargoDetails);
    }

    public List<BookingStatusHistory> getStatusHistory() {
        return Collections.unmodifiableList(statusHistory);
    }

    public List<BookingReinstatement> getReinstatements() {
        return Collections.unmodifiableList(reinstatements);
    }

    /** Declared cargo value, which the EEI filing threshold is assessed against. */
    public BigDecimal totalValueUsd() {
        return cargoDetails.stream()
                .map(BookingCargoDetail::getValueUsd)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal totalWeightKg() {
        return cargoDetails.stream()
                .map(BookingCargoDetail::getWeightKg)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Rule 5: confirmed ETD more than two business days from what the customer asked for. */
    public boolean requiresCustomerEtdNotification() {
        if (carrierBooking == null || carrierBooking.getConfirmedEtd() == null) {
            return false;
        }
        long variance = Math.abs(BusinessDays.between(requestedEtd, carrierBooking.getConfirmedEtd()));
        return variance > ETD_VARIANCE_NOTIFICATION_THRESHOLD_BUSINESS_DAYS;
    }

    /** Rule 8: both references travel together onto every downstream document. */
    public String carrierBookingRef() {
        return carrierBooking == null ? null : carrierBooking.getCarrierBookingRef();
    }

    // ----------------------------------------------------------------- helpers

    /**
     * The single point at which status changes, so no transition can happen without
     * a history entry naming who did it and why.
     */
    /**
     * Events registered on this aggregate but not yet published. Spring Data reads
     * these on save; exposed here so the aggregate can be tested without a context.
     */
    public java.util.Collection<Object> pendingEvents() {
        return domainEvents();
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

    private void transitionTo(
            BookingStatus target,
            Instant now,
            String actor,
            String reason,
            StatusChangeSource source
    ) {
        BookingStatus from = this.status;
        this.status = target;
        statusHistory.add(BookingStatusHistory.record(
                this, statusHistory.size() + 1, from, target, now, actor, reason, source));
        touch(now, actor);
    }

    private void touch(Instant now, String actor) {
        this.lastModifiedAt = now;
        this.lastModifiedBy = actor;
    }

    private void validatePayload(ContainerType containerType, int numberOfContainers) {
        if (!containerType.hasPublishedPayloadLimit()) {
            return;
        }
        BigDecimal perContainer = totalWeightKg()
                .divide(BigDecimal.valueOf(numberOfContainers), 3, java.math.RoundingMode.HALF_UP);
        BigDecimal limit = BigDecimal.valueOf(containerType.getMaxPayloadKg());
        if (perContainer.compareTo(limit) > 0) {
            throw new DomainRuleViolationException(
                    "Cargo weight " + perContainer + " kg per container exceeds the "
                            + containerType.getCode() + " payload limit of " + limit + " kg");
        }
    }

    private void requireStatus(String message, BookingStatus... allowed) {
        for (BookingStatus candidate : allowed) {
            if (status == candidate) {
                return;
            }
        }
        throw new DomainRuleViolationException(message + " (status was " + status + ")");
    }
}
