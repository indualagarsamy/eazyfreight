package com.eazyfreight.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A roll onto a later sailing, capturing both the sailing left behind and the one
 * taken.
 *
 * <p>The booking keeps its identity across a reinstatement, so without this record
 * the previous vessel and ETD would be overwritten and lost — exactly what happens
 * in the monolith.
 */
@Entity
@Table(name = "booking_reinstatements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookingReinstatement {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "previous_vessel", length = 128)
    private String previousVessel;

    @Column(name = "previous_voyage", length = 64)
    private String previousVoyage;

    @Column(name = "previous_etd")
    private LocalDate previousEtd;

    @Column(name = "previous_eta")
    private LocalDate previousEta;

    @Column(name = "new_vessel_name", nullable = false, length = 128)
    private String newVesselName;

    @Column(name = "new_voyage_number", nullable = false, length = 64)
    private String newVoyageNumber;

    @Column(name = "new_etd", nullable = false)
    private LocalDate newEtd;

    @Column(name = "new_eta", nullable = false)
    private LocalDate newEta;

    @Column(name = "reinstated_at", nullable = false)
    private Instant reinstatedAt;

    @Column(name = "reinstated_by", nullable = false, length = 64)
    private String reinstatedBy;

    @Column(name = "reason", nullable = false)
    private String reason;

    static BookingReinstatement of(
            Booking booking,
            CarrierBooking previous,
            String newVesselName,
            String newVoyageNumber,
            LocalDate newEtd,
            LocalDate newEta,
            Instant reinstatedAt,
            String reinstatedBy,
            String reason
    ) {
        BookingReinstatement reinstatement = new BookingReinstatement();
        reinstatement.booking = booking;
        if (previous != null) {
            reinstatement.previousVessel = previous.getVesselName();
            reinstatement.previousVoyage = previous.getVoyageNumber();
            reinstatement.previousEtd = previous.getConfirmedEtd();
            reinstatement.previousEta = previous.getConfirmedEta();
        }
        reinstatement.newVesselName = newVesselName;
        reinstatement.newVoyageNumber = newVoyageNumber;
        reinstatement.newEtd = newEtd;
        reinstatement.newEta = newEta;
        reinstatement.reinstatedAt = reinstatedAt;
        reinstatement.reinstatedBy = reinstatedBy;
        reinstatement.reason = reason;
        return reinstatement;
    }
}
