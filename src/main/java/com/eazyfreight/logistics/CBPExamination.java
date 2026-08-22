package com.eazyfreight.logistics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A CBP hold and examination. Breaking the seal is part of the process, so an
 * examination always pairs with a seal replacement when it completes.
 *
 * <p>The monolith tracked these on sticky notes and in email threads.
 */
@Entity
@Table(name = "cbp_examinations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CBPExamination {

    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "container_assignment_id", nullable = false)
    private ContainerAssignment assignment;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "container_number", length = 24)
    private String containerNumber;

    @Column(name = "hold_placed_at", nullable = false)
    private Instant holdPlacedAt;

    @Column(name = "examination_started_at")
    private Instant examinationStartedAt;

    @Column(name = "examination_completed_at")
    private Instant examinationCompletedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", length = 24)
    private ExaminationResult result;

    @Column(name = "original_seal_id")
    private UUID originalSealId;

    @Column(name = "replacement_seal_id")
    private UUID replacementSealId;

    @Column(name = "cbp_officer_id", length = 64)
    private String cbpOfficerId;

    @Column(name = "notes")
    private String notes;

    static CBPExamination holdPlaced(
            ContainerAssignment assignment, UUID bookingId, String containerNumber,
            UUID originalSealId, String cbpOfficerId, Instant at, String notes) {
        CBPExamination examination = new CBPExamination();
        examination.id = UUID.randomUUID();
        examination.assignment = assignment;
        examination.bookingId = bookingId;
        examination.containerNumber = containerNumber;
        examination.originalSealId = originalSealId;
        examination.cbpOfficerId = cbpOfficerId;
        examination.holdPlacedAt = at;
        examination.examinationStartedAt = at;
        examination.notes = notes;
        return examination;
    }

    void complete(ExaminationResult result, UUID replacementSealId, Instant at, String notes) {
        this.result = result;
        this.replacementSealId = replacementSealId;
        this.examinationCompletedAt = at;
        if (notes != null) {
            this.notes = notes;
        }
    }

    public boolean isOpen() {
        return examinationCompletedAt == null;
    }
}
