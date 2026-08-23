package com.eazyfreight.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The carrier's side of the booking — their reference, the vessel they gave us, and
 * the dates they confirmed.
 *
 * <p>Kept separate from {@link Booking} because these are externally-issued facts
 * that arrive later and change on reinstatement, while the Eazy Freight booking
 * reference and identity do not.
 */
@Entity
@Table(name = "carrier_bookings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CarrierBooking {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_source_type", nullable = false, length = 16)
    private BookingSourceType bookingSourceType;

    @Column(name = "carrier_id", nullable = false)
    private UUID carrierId;

    @Column(name = "carrier_booking_ref", length = 64)
    private String carrierBookingRef;

    /** The co-loader's own reference, distinct from the master carrier reference. */
    @Column(name = "co_loader_booking_ref", length = 64)
    private String coLoaderBookingRef;

    @Column(name = "vessel_name", length = 128)
    private String vesselName;

    @Column(name = "voyage_number", length = 64)
    private String voyageNumber;

    @Column(name = "confirmed_etd")
    private LocalDate confirmedEtd;

    @Column(name = "confirmed_eta")
    private LocalDate confirmedEta;

    @Enumerated(EnumType.STRING)
    @Column(name = "container_type", length = 16)
    private ContainerType containerType;

    @Column(name = "number_of_containers")
    private Integer numberOfContainers;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "confirmed_by", length = 64)
    private String confirmedBy;

    static CarrierBooking submittedTo(
            BookingSourceType sourceType,
            UUID carrierId,
            ContainerType containerType,
            Integer numberOfContainers,
            Instant submittedAt
    ) {
        CarrierBooking carrierBooking = new CarrierBooking();
        carrierBooking.bookingSourceType = sourceType;
        carrierBooking.carrierId = carrierId;
        carrierBooking.containerType = containerType;
        carrierBooking.numberOfContainers = numberOfContainers;
        carrierBooking.submittedAt = submittedAt;
        return carrierBooking;
    }

    void recordConfirmation(
            String carrierBookingRef,
            String coLoaderBookingRef,
            String vesselName,
            String voyageNumber,
            LocalDate confirmedEtd,
            LocalDate confirmedEta,
            ContainerType containerType,
            Instant confirmedAt,
            String confirmedBy
    ) {
        this.carrierBookingRef = carrierBookingRef;
        this.coLoaderBookingRef = coLoaderBookingRef;
        this.vesselName = vesselName;
        this.voyageNumber = voyageNumber;
        this.confirmedEtd = confirmedEtd;
        this.confirmedEta = confirmedEta;
        if (containerType != null) {
            this.containerType = containerType;
        }
        this.confirmedAt = confirmedAt;
        this.confirmedBy = confirmedBy;
    }

    /** Moves the booking onto a new sailing. The carrier reference is retained. */
    void moveToSailing(String vesselName, String voyageNumber, LocalDate newEtd, LocalDate newEta) {
        this.vesselName = vesselName;
        this.voyageNumber = voyageNumber;
        this.confirmedEtd = newEtd;
        this.confirmedEta = newEta;
    }

    void applyCounterOffer(String vesselName, String voyageNumber, LocalDate proposedEtd, LocalDate proposedEta) {
        moveToSailing(vesselName, voyageNumber, proposedEtd, proposedEta);
    }
}
