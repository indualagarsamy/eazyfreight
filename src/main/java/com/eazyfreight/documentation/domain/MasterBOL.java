package com.eazyfreight.documentation.domain;

import com.eazyfreight.documentation.domain.DocumentationEnums.VerificationStatus;

import com.eazyfreight.documentation.event.DocumentationEvent;

import com.eazyfreight.exception.DomainRuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.AbstractAggregateRoot;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The carrier's Bill of Lading, as received back from them.
 *
 * <p>It exists to be checked. The carrier keys our instructions into their own
 * system, and what comes back does not always match — a transposed seal number or
 * a wrong weight on a document of title is a real problem. Verification is
 * mandatory before any House BOL can be generated against it.
 *
 * <p>The monolith stores the number in a text field and the PDF in a folder on a
 * network drive, so nothing compares the two.
 */
@Entity
@Table(name = "master_bols")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MasterBOL extends AbstractAggregateRoot<MasterBOL> implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew = true;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "instructions_id", nullable = false)
    private UUID instructionsId;

    @Column(name = "master_bol_number", nullable = false, length = 64)
    private String masterBolNumber;

    @Column(name = "carrier_id")
    private UUID carrierId;

    @Column(name = "issued_by_carrier_at")
    private Instant issuedByCarrierAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "received_by", nullable = false, length = 64)
    private String receivedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 24)
    private VerificationStatus verificationStatus;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verified_by", length = 64)
    private String verifiedBy;

    /** Named fields that differ from the instructions — not a free-text note. */
    @ElementCollection(fetch = FetchType.LAZY)
    @jakarta.persistence.CollectionTable(
            name = "master_bol_discrepancies",
            joinColumns = @JoinColumn(name = "master_bol_id"))
    @Column(name = "field_name", length = 64)
    private List<String> discrepancyFields = new ArrayList<>();

    @Column(name = "discrepancy_raised_at")
    private Instant discrepancyRaisedAt;

    @Column(name = "discrepancy_resolved_at")
    private Instant discrepancyResolvedAt;

    @Column(name = "document_file_reference", length = 255)
    private String documentFileReference;

    public static MasterBOL received(
            UUID bookingId, UUID instructionsId, String masterBolNumber, UUID carrierId,
            Instant issuedByCarrierAt, String documentFileReference, Instant now, String actor
    ) {
        MasterBOL bol = new MasterBOL();
        bol.id = UUID.randomUUID();
        bol.bookingId = bookingId;
        bol.instructionsId = instructionsId;
        bol.masterBolNumber = masterBolNumber;
        bol.carrierId = carrierId;
        bol.issuedByCarrierAt = issuedByCarrierAt;
        bol.documentFileReference = documentFileReference;
        bol.receivedAt = now;
        bol.receivedBy = actor;
        bol.verificationStatus = VerificationStatus.PENDING;
        bol.registerEvent(new DocumentationEvent.MasterBOLReceived(
                bol.id, bookingId, masterBolNumber, now));
        return bol;
    }

    /** Confirms the carrier's document matches the instructions. */
    public void verify(Instant now, String actor) {
        if (verificationStatus == VerificationStatus.DISCREPANCY_RAISED) {
            throw new DomainRuleViolationException(
                    "Resolve the discrepancy on " + masterBolNumber + " before verifying");
        }
        this.verificationStatus = VerificationStatus.VERIFIED;
        this.verifiedAt = now;
        this.verifiedBy = actor;
        registerEvent(new DocumentationEvent.MasterBOLVerified(
                id, bookingId, masterBolNumber, now));
    }

    public void raiseDiscrepancy(List<String> fields, Instant now) {
        if (fields == null || fields.isEmpty()) {
            throw new DomainRuleViolationException(
                    "A discrepancy must name the fields that differ from the instructions");
        }
        this.verificationStatus = VerificationStatus.DISCREPANCY_RAISED;
        this.discrepancyFields.clear();
        this.discrepancyFields.addAll(fields);
        this.discrepancyRaisedAt = now;
        registerEvent(new DocumentationEvent.MasterBOLDiscrepancyRaised(
                id, bookingId, masterBolNumber, List.copyOf(fields), now));
    }

    /** The carrier reissued a corrected document. */
    public void recordCorrection(String correctedMasterBolNumber, String fileReference, Instant now) {
        if (verificationStatus != VerificationStatus.DISCREPANCY_RAISED) {
            throw new DomainRuleViolationException(
                    "No discrepancy is outstanding on " + masterBolNumber);
        }
        if (correctedMasterBolNumber != null && !correctedMasterBolNumber.isBlank()) {
            this.masterBolNumber = correctedMasterBolNumber;
        }
        if (fileReference != null) {
            this.documentFileReference = fileReference;
        }
        this.verificationStatus = VerificationStatus.CORRECTED;
        this.discrepancyResolvedAt = now;
    }

    public boolean isVerified() {
        return verificationStatus == VerificationStatus.VERIFIED;
    }

    public List<String> getDiscrepancyFields() {
        return Collections.unmodifiableList(discrepancyFields);
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
}
