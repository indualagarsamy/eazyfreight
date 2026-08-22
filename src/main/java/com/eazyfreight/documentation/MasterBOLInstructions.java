package com.eazyfreight.documentation;

import com.eazyfreight.documentation.DocumentationEnums.FreightTerms;
import com.eazyfreight.documentation.DocumentationEnums.InstructionsStatus;
import com.eazyfreight.exception.DomainRuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
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
import java.util.UUID;

/**
 * Shipping instructions sent to the carrier.
 *
 * <p>Every field is a <em>snapshot</em> taken at compilation — container number,
 * seal number, ITN, party names, cargo figures. That is the point of the record:
 * it proves what was communicated to the carrier and when. The monolith types
 * these straight into the INTTRA portal and keeps nothing, so a dispute over
 * whether the right seal number was sent before the cut-off has no answer.
 *
 * <p>Immutable once sent. A carrier query produces a new instructions record
 * referencing this one, never an edit.
 */
@Entity
@Table(name = "master_bol_instructions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MasterBOLInstructions extends AbstractAggregateRoot<MasterBOLInstructions>
        implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew = true;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "instructions_reference", nullable = false, unique = true, length = 32)
    private String instructionsReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private InstructionsStatus status;

    /** Set when this record supersedes an earlier one after a carrier query. */
    @Column(name = "supersedes_instructions_id")
    private UUID supersedesInstructionsId;

    @Column(name = "carrier_id")
    private UUID carrierId;

    @Column(name = "carrier_booking_ref", length = 64)
    private String carrierBookingRef;

    // --- the three late-arriving facts, snapshotted -------------------------

    @Column(name = "container_number", nullable = false, length = 24)
    private String containerNumber;

    @Column(name = "seal_number", nullable = false, length = 64)
    private String sealNumber;

    @Column(name = "itn_number", nullable = false, length = 16)
    private String itnNumber;

    // --- parties, snapshotted ------------------------------------------------

    @Column(name = "shipper_name", nullable = false, length = 128)
    private String shipperName;

    @Column(name = "shipper_address")
    private String shipperAddress;

    @Column(name = "consignee_name", nullable = false, length = 128)
    private String consigneeName;

    @Column(name = "consignee_address")
    private String consigneeAddress;

    @Column(name = "notify_party_name", length = 128)
    private String notifyPartyName;

    @Column(name = "notify_party_address")
    private String notifyPartyAddress;

    // --- carriage and cargo --------------------------------------------------

    @Column(name = "port_of_loading_code", nullable = false, length = 8)
    private String portOfLoadingCode;

    @Column(name = "port_of_discharge_code", nullable = false, length = 8)
    private String portOfDischargeCode;

    @Column(name = "vessel_name", length = 128)
    private String vesselName;

    @Column(name = "voyage_number", length = 64)
    private String voyageNumber;

    @Column(name = "cargo_description", nullable = false)
    private String cargoDescription;

    @Column(name = "hs_code", length = 16)
    private String hsCode;

    @Column(name = "actual_weight_kg", precision = 12, scale = 3)
    private BigDecimal actualWeightKg;

    @Column(name = "actual_pieces")
    private Integer actualPieces;

    @Column(name = "actual_cbm", precision = 12, scale = 4)
    private BigDecimal actualCbm;

    @Column(name = "marks_and_numbers")
    private String marksAndNumbers;

    @Enumerated(EnumType.STRING)
    @Column(name = "freight_terms", nullable = false, length = 16)
    private FreightTerms freightTerms;

    // --- lifecycle -----------------------------------------------------------

    @Column(name = "drafted_at", nullable = false)
    private Instant draftedAt;

    @Column(name = "drafted_by", nullable = false, length = 64)
    private String draftedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_by", length = 64)
    private String approvedBy;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "documentation_cut_off_date")
    private LocalDate documentationCutOffDate;

    @Column(name = "sent_after_cut_off", nullable = false)
    private boolean sentAfterCutOff;

    @Column(name = "carrier_query")
    private String carrierQuery;

    public static MasterBOLInstructions compile(
            String reference, UUID bookingId, UUID carrierId, String carrierBookingRef,
            String containerNumber, String sealNumber, String itnNumber,
            String shipperName, String shipperAddress,
            String consigneeName, String consigneeAddress,
            String notifyPartyName, String notifyPartyAddress,
            String portOfLoadingCode, String portOfDischargeCode,
            String vesselName, String voyageNumber,
            String cargoDescription, String hsCode,
            BigDecimal actualWeightKg, Integer actualPieces, BigDecimal actualCbm,
            String marksAndNumbers, FreightTerms freightTerms,
            LocalDate documentationCutOffDate, UUID supersedes, Instant now, String actor
    ) {
        MasterBOLInstructions instructions = new MasterBOLInstructions();
        instructions.id = UUID.randomUUID();
        instructions.instructionsReference = reference;
        instructions.bookingId = bookingId;
        instructions.status = InstructionsStatus.DRAFT;
        instructions.supersedesInstructionsId = supersedes;
        instructions.carrierId = carrierId;
        instructions.carrierBookingRef = carrierBookingRef;
        instructions.containerNumber = containerNumber;
        instructions.sealNumber = sealNumber;
        instructions.itnNumber = itnNumber;
        instructions.shipperName = shipperName;
        instructions.shipperAddress = shipperAddress;
        instructions.consigneeName = consigneeName;
        instructions.consigneeAddress = consigneeAddress;
        instructions.notifyPartyName = notifyPartyName;
        instructions.notifyPartyAddress = notifyPartyAddress;
        instructions.portOfLoadingCode = portOfLoadingCode;
        instructions.portOfDischargeCode = portOfDischargeCode;
        instructions.vesselName = vesselName;
        instructions.voyageNumber = voyageNumber;
        instructions.cargoDescription = cargoDescription;
        instructions.hsCode = hsCode;
        instructions.actualWeightKg = actualWeightKg;
        instructions.actualPieces = actualPieces;
        instructions.actualCbm = actualCbm;
        instructions.marksAndNumbers = marksAndNumbers;
        instructions.freightTerms = freightTerms;
        instructions.documentationCutOffDate = documentationCutOffDate;
        instructions.draftedAt = now;
        instructions.draftedBy = actor;

        instructions.registerEvent(new DocumentationEvent.MasterBOLInstructionsDrafted(
                instructions.id, bookingId, reference, now));
        return instructions;
    }

    public void approve(Instant now, String actor) {
        requireStatus("Only draft instructions can be approved", InstructionsStatus.DRAFT);
        this.status = InstructionsStatus.APPROVED;
        this.approvedAt = now;
        this.approvedBy = actor;
    }

    /**
     * Sends to the carrier and freezes the record. Sending after the documentation
     * cut-off is permitted but flagged — the carrier may roll the cargo, and the
     * fact needs to be on file.
     */
    public void send(LocalDate today, Instant now) {
        requireStatus("Instructions must be approved before they are sent",
                InstructionsStatus.APPROVED);
        this.status = InstructionsStatus.SENT;
        this.sentAt = now;
        this.sentAfterCutOff = documentationCutOffDate != null
                && today.isAfter(documentationCutOffDate);

        registerEvent(new DocumentationEvent.MasterBOLInstructionsSentToCarrier(
                id, bookingId, instructionsReference, containerNumber, sealNumber,
                itnNumber, sentAfterCutOff, now));
    }

    public void recordCarrierQuery(String query, Instant now) {
        requireStatus("Only sent instructions can be queried by the carrier",
                InstructionsStatus.SENT);
        this.status = InstructionsStatus.QUERIED;
        this.carrierQuery = query;
        registerEvent(new DocumentationEvent.CarrierQueryRaised(
                id, bookingId, instructionsReference, query, now));
    }

    /** Marks this record as replaced by a corrected one. */
    public void supersede() {
        this.status = InstructionsStatus.SUPERSEDED;
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

    private void requireStatus(String message, InstructionsStatus expected) {
        if (status != expected) {
            throw new DomainRuleViolationException(message + " (status was " + status + ")");
        }
    }
}
