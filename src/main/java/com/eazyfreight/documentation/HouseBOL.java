package com.eazyfreight.documentation;

import com.eazyfreight.documentation.DocumentationEnums.DistributionChannel;
import com.eazyfreight.documentation.DocumentationEnums.DistributionRecipient;
import com.eazyfreight.documentation.DocumentationEnums.FreightTerms;
import com.eazyfreight.documentation.DocumentationEnums.HouseBOLStatus;
import com.eazyfreight.documentation.DocumentationEnums.ReleaseType;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * House Bill of Lading — the document of title Eazy Freight issues.
 *
 * <p>This is the most legally significant artifact the business produces, and three
 * properties follow from that:
 *
 * <ul>
 *   <li><b>Parties are snapshotted, not referenced.</b> The names and addresses are
 *       copied in at issuance. If the customer moves next year, the BOL that was
 *       issued must not silently change.</li>
 *   <li><b>Amendment is revision, not replacement.</b> The BOL number is stable;
 *       the revision increments; the previous revision is voided and kept. The
 *       monolith saves over the same Word file, so the revision the consignee is
 *       holding no longer exists anywhere.</li>
 *   <li><b>Originals gate amendment.</b> When three negotiable originals are out
 *       with a courier, whoever holds one controls the cargo. A new set cannot be
 *       issued until all three come back.</li>
 * </ul>
 */
@Entity
@Table(name = "house_bols")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HouseBOL extends AbstractAggregateRoot<HouseBOL> implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew = true;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "master_bol_id", nullable = false)
    private UUID masterBolId;

    /** Stable across every revision. */
    @Column(name = "house_bol_number", nullable = false, length = 32)
    private String houseBolNumber;

    @Column(name = "revision_number", nullable = false)
    private int revisionNumber;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private HouseBOLStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "release_type", nullable = false, length = 24)
    private ReleaseType releaseType;

    @Column(name = "release_type_confirmed_at")
    private Instant releaseTypeConfirmedAt;

    @Column(name = "release_type_confirmed_by", length = 64)
    private String releaseTypeConfirmedBy;

    // --- party snapshots -----------------------------------------------------

    @Column(name = "shipper_id")
    private UUID shipperId;

    @Column(name = "shipper_name_snapshot", nullable = false, length = 128)
    private String shipperNameSnapshot;

    @Column(name = "shipper_address_snapshot")
    private String shipperAddressSnapshot;

    @Column(name = "consignee_id")
    private UUID consigneeId;

    @Column(name = "consignee_name_snapshot", nullable = false, length = 128)
    private String consigneeNameSnapshot;

    @Column(name = "consignee_address_snapshot")
    private String consigneeAddressSnapshot;

    @Column(name = "notify_party_name_snapshot", length = 128)
    private String notifyPartyNameSnapshot;

    @Column(name = "notify_party_address_snapshot")
    private String notifyPartyAddressSnapshot;

    // --- carriage and cargo, also snapshotted --------------------------------

    @Column(name = "port_of_loading_code", nullable = false, length = 8)
    private String portOfLoadingCode;

    @Column(name = "port_of_discharge_code", nullable = false, length = 8)
    private String portOfDischargeCode;

    @Column(name = "vessel_name", length = 128)
    private String vesselName;

    @Column(name = "voyage_number", length = 64)
    private String voyageNumber;

    @Column(name = "container_number", nullable = false, length = 24)
    private String containerNumber;

    @Column(name = "seal_number", nullable = false, length = 64)
    private String sealNumber;

    @Column(name = "cargo_description", nullable = false)
    private String cargoDescription;

    @Column(name = "hs_code", length = 16)
    private String hsCode;

    @Column(name = "weight_kg", precision = 12, scale = 3)
    private BigDecimal weightKg;

    @Column(name = "pieces")
    private Integer pieces;

    @Column(name = "cbm", precision = 12, scale = 4)
    private BigDecimal cbm;

    @Column(name = "marks_and_numbers")
    private String marksAndNumbers;

    @Enumerated(EnumType.STRING)
    @Column(name = "freight_terms", nullable = false, length = 16)
    private FreightTerms freightTerms;

    // --- lifecycle -----------------------------------------------------------

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "issued_by", nullable = false, length = 64)
    private String issuedBy;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "superseded_by_house_bol_id")
    private UUID supersededByHouseBolId;

    @Column(name = "amendment_reason")
    private String amendmentReason;

    @Column(name = "pdf_reference", length = 255)
    private String pdfReference;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "original_bol_tracking_id")
    private OriginalBOLTracking originalBolTracking;

    @OneToMany(mappedBy = "houseBol", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sentAt")
    private List<HouseBOLDistribution> distributions = new ArrayList<>();

    // ----------------------------------------------------------------- issuing

    public static HouseBOL issue(
            String houseBolNumber, UUID bookingId, UUID masterBolId, ReleaseType releaseType,
            UUID shipperId, String shipperName, String shipperAddress,
            UUID consigneeId, String consigneeName, String consigneeAddress,
            String notifyPartyName, String notifyPartyAddress,
            String portOfLoadingCode, String portOfDischargeCode,
            String vesselName, String voyageNumber,
            String containerNumber, String sealNumber,
            String cargoDescription, String hsCode,
            BigDecimal weightKg, Integer pieces, BigDecimal cbm,
            String marksAndNumbers, FreightTerms freightTerms,
            Instant now, String actor
    ) {
        HouseBOL bol = newRevision(houseBolNumber, 0, bookingId, masterBolId, releaseType,
                shipperId, shipperName, shipperAddress, consigneeId, consigneeName, consigneeAddress,
                notifyPartyName, notifyPartyAddress, portOfLoadingCode, portOfDischargeCode,
                vesselName, voyageNumber, containerNumber, sealNumber, cargoDescription, hsCode,
                weightKg, pieces, cbm, marksAndNumbers, freightTerms, now, actor);

        bol.registerEvent(new DocumentationEvent.HouseBOLGenerated(
                bol.id, bookingId, houseBolNumber, 0, releaseType, now));
        return bol;
    }

    private static HouseBOL newRevision(
            String houseBolNumber, int revisionNumber, UUID bookingId, UUID masterBolId,
            ReleaseType releaseType, UUID shipperId, String shipperName, String shipperAddress,
            UUID consigneeId, String consigneeName, String consigneeAddress,
            String notifyPartyName, String notifyPartyAddress,
            String portOfLoadingCode, String portOfDischargeCode,
            String vesselName, String voyageNumber, String containerNumber, String sealNumber,
            String cargoDescription, String hsCode, BigDecimal weightKg, Integer pieces,
            BigDecimal cbm, String marksAndNumbers, FreightTerms freightTerms,
            Instant now, String actor
    ) {
        HouseBOL bol = new HouseBOL();
        bol.id = UUID.randomUUID();
        bol.houseBolNumber = houseBolNumber;
        bol.revisionNumber = revisionNumber;
        bol.active = true;
        bol.status = HouseBOLStatus.ISSUED;
        bol.bookingId = bookingId;
        bol.masterBolId = masterBolId;
        bol.releaseType = releaseType;
        bol.releaseTypeConfirmedAt = now;
        bol.releaseTypeConfirmedBy = actor;
        bol.shipperId = shipperId;
        bol.shipperNameSnapshot = shipperName;
        bol.shipperAddressSnapshot = shipperAddress;
        bol.consigneeId = consigneeId;
        bol.consigneeNameSnapshot = consigneeName;
        bol.consigneeAddressSnapshot = consigneeAddress;
        bol.notifyPartyNameSnapshot = notifyPartyName;
        bol.notifyPartyAddressSnapshot = notifyPartyAddress;
        bol.portOfLoadingCode = portOfLoadingCode;
        bol.portOfDischargeCode = portOfDischargeCode;
        bol.vesselName = vesselName;
        bol.voyageNumber = voyageNumber;
        bol.containerNumber = containerNumber;
        bol.sealNumber = sealNumber;
        bol.cargoDescription = cargoDescription;
        bol.hsCode = hsCode;
        bol.weightKg = weightKg;
        bol.pieces = pieces;
        bol.cbm = cbm;
        bol.marksAndNumbers = marksAndNumbers;
        bol.freightTerms = freightTerms;
        bol.issuedAt = now;
        bol.issuedBy = actor;
        return bol;
    }

    public void recordPdfGenerated(String pdfReference) {
        this.pdfReference = pdfReference;
    }

    // ------------------------------------------------------------ distribution

    public void recordDistribution(
            DistributionRecipient recipient, String recipientName, String recipientAddress,
            DistributionChannel channel, String reference, Instant now, String actor) {
        requireActive("distribute");
        distributions.add(HouseBOLDistribution.record(this, recipient, recipientName,
                recipientAddress, channel, revisionNumber, reference, now, actor));
        registerEvent(new DocumentationEvent.HouseBOLDistributed(
                id, bookingId, houseBolNumber, revisionNumber, recipient, now));
    }

    // -------------------------------------------------------------- originals

    /** Releases the three negotiable originals to a courier. */
    public void releaseOriginals(String releasedTo, String courierReference, Instant now) {
        requireActive("release originals for");
        if (releaseType != ReleaseType.ORIGINAL_BOL) {
            throw new DomainRuleViolationException(
                    "Originals only exist for an ORIGINAL_BOL release (this is " + releaseType + ")");
        }
        if (originalBolTracking != null) {
            throw new DomainRuleViolationException("Originals have already been released");
        }
        this.originalBolTracking = OriginalBOLTracking.issue(id, releasedTo, courierReference, now);
        registerEvent(new DocumentationEvent.OriginalBOLsReleased(
                id, bookingId, houseBolNumber, OriginalBOLTracking.FULL_SET, courierReference, now));
    }

    public void recordOriginalsSurrendered(int count, Instant now) {
        if (originalBolTracking == null) {
            throw new DomainRuleViolationException("No originals have been released");
        }
        originalBolTracking.recordSurrendered(count, now);
        if (originalBolTracking.allSurrendered()) {
            registerEvent(new DocumentationEvent.OriginalBOLsSurrendered(
                    id, bookingId, houseBolNumber, now));
        }
    }

    // -------------------------------------------------------------- amendment

    /** Why an amendment cannot proceed, or null when it can. */
    public String amendmentBlockedReason() {
        if (!active) {
            return "This revision has been superseded";
        }
        if (releaseType == ReleaseType.ORIGINAL_BOL && originalBolTracking != null
                && !originalBolTracking.allSurrendered()) {
            return "%d of %d negotiable originals are still outstanding — all must be surrendered "
                    .formatted(originalBolTracking.outstanding(),
                            originalBolTracking.getOriginalsIssued())
                    + "before a new set can be issued";
        }
        return null;
    }

    /**
     * Produces the next revision and voids this one. The BOL number carries over;
     * only the revision changes.
     */
    public HouseBOL amendInto(
            String reason, UUID masterBolId,
            String consigneeName, String consigneeAddress,
            String notifyPartyName, String notifyPartyAddress,
            String vesselName, String voyageNumber, String sealNumber,
            String cargoDescription, BigDecimal weightKg, Integer pieces, BigDecimal cbm,
            ReleaseType releaseType, Instant now, String actor
    ) {
        String blocked = amendmentBlockedReason();
        if (blocked != null) {
            throw new DomainRuleViolationException("Amendment refused: " + blocked);
        }

        HouseBOL next = newRevision(houseBolNumber, revisionNumber + 1, bookingId,
                masterBolId == null ? this.masterBolId : masterBolId,
                releaseType == null ? this.releaseType : releaseType,
                shipperId, shipperNameSnapshot, shipperAddressSnapshot,
                consigneeId,
                consigneeName == null ? consigneeNameSnapshot : consigneeName,
                consigneeAddress == null ? consigneeAddressSnapshot : consigneeAddress,
                notifyPartyName == null ? notifyPartyNameSnapshot : notifyPartyName,
                notifyPartyAddress == null ? notifyPartyAddressSnapshot : notifyPartyAddress,
                portOfLoadingCode, portOfDischargeCode,
                vesselName == null ? this.vesselName : vesselName,
                voyageNumber == null ? this.voyageNumber : voyageNumber,
                containerNumber,
                sealNumber == null ? this.sealNumber : sealNumber,
                cargoDescription == null ? this.cargoDescription : cargoDescription,
                hsCode,
                weightKg == null ? this.weightKg : weightKg,
                pieces == null ? this.pieces : pieces,
                cbm == null ? this.cbm : cbm,
                marksAndNumbers, freightTerms, now, actor);
        next.amendmentReason = reason;

        // Deactivated without the successor pointer: the caller flushes this first so
        // the partial unique index sees one active revision, then links the pointer
        // once the successor row exists. See DocumentationService#amendHouseBol.
        voidRevision(null, now);

        next.registerEvent(new DocumentationEvent.HouseBOLAmended(
                next.id, bookingId, houseBolNumber, next.revisionNumber, reason, now));

        boolean cargoChanged = weightKg != null || pieces != null || cbm != null
                || cargoDescription != null;
        if (cargoChanged) {
            next.registerEvent(new DocumentationEvent.BOLCargoDetailsChanged(
                    next.id, bookingId, houseBolNumber, now));
        }
        return next;
    }

    /** Points a voided revision at the one that replaced it. */
    public void linkSupersededBy(UUID successorId) {
        this.supersededByHouseBolId = successorId;
    }

    /** Voids this revision without issuing a successor. */
    public void voidRevision(UUID supersededBy, Instant now) {
        this.active = false;
        this.status = HouseBOLStatus.VOIDED;
        this.voidedAt = now;
        this.supersededByHouseBolId = supersededBy;
    }

    // ----------------------------------------------------------------- queries

    public Optional<OriginalBOLTracking> originals() {
        return Optional.ofNullable(originalBolTracking);
    }

    public List<HouseBOLDistribution> getDistributions() {
        return Collections.unmodifiableList(distributions);
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

    private void requireActive(String action) {
        if (!active) {
            throw new DomainRuleViolationException(
                    "Cannot " + action + " a superseded revision of " + houseBolNumber);
        }
    }
}
