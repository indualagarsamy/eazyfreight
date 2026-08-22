package com.eazyfreight.booking;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BookingReinstatementResponse(
        UUID id,
        String previousVessel,
        String previousVoyage,
        LocalDate previousEtd,
        LocalDate previousEta,
        String newVesselName,
        String newVoyageNumber,
        LocalDate newEtd,
        LocalDate newEta,
        Instant reinstatedAt,
        String reinstatedBy,
        String reason
) {
    public static BookingReinstatementResponse fromEntity(BookingReinstatement reinstatement) {
        return new BookingReinstatementResponse(
                reinstatement.getId(),
                reinstatement.getPreviousVessel(),
                reinstatement.getPreviousVoyage(),
                reinstatement.getPreviousEtd(),
                reinstatement.getPreviousEta(),
                reinstatement.getNewVesselName(),
                reinstatement.getNewVoyageNumber(),
                reinstatement.getNewEtd(),
                reinstatement.getNewEta(),
                reinstatement.getReinstatedAt(),
                reinstatement.getReinstatedBy(),
                reinstatement.getReason()
        );
    }
}
