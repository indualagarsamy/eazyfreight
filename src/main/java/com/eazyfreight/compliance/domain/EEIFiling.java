package com.eazyfreight.compliance.domain;

import com.eazyfreight.compliance.client.AesFilingClient;
import com.eazyfreight.compliance.event.ComplianceEvent;

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

/**
 * EEI compliance aggregate root — one Electronic Export Information filing.
 *
 * <p>The rule that shapes this class is that <em>a filing is never edited once CBP
 * has seen it</em>. Every command that would change submitted data instead produces
 * a new filing pointing back at this one. That is why there are no setters for the
 * declared fields and why {@link #requireDraft} guards the only method that writes
 * them.
 *
 * <p>The second rule is ITN supersession. Accepting an amendment does not overwrite
 * the previous ITN: it flips the old record inactive, points it at its replacement,
 * and issues a new active one. Both stay on file for the five-year retention
 * period, because the question "what was reported to CBP, and when" has to remain
 * answerable.
 */
@Entity
@Table(name = "eei_filings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EEIFiling extends AbstractAggregateRoot<EEIFiling> implements Persistable<UUID> {

    /** Value at or below which no filing is required, absent an export licence. */
    public static final BigDecimal FILING_THRESHOLD_USD = new BigDecimal("2500");

    private static final int COMMODITY_DESCRIPTION_MAX = 150;

    @Id
    private UUID id;

    @Transient
    private boolean isNew = true;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "filing_reference", nullable = false, unique = true, length = 32)
    private String filingReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "filing_type", nullable = false, length = 16)
    private FilingType filingType;

    @Column(name = "parent_filing_id")
    private UUID parentFilingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private FilingStatus status;

    // --- declared party data, captured at filing time -----------------------
    // Shipper EIN, carrier SCAC and consignee country are entered by compliance
    // staff rather than resolved from a Party or Carrier context, because neither
    // context exists yet. The booking holds only opaque UUIDs for those parties.

    @Column(name = "shipper_name", length = 128)
    private String shipperName;

    @Column(name = "shipper_ein", length = 32)
    private String shipperEin;

    @Column(name = "shipper_address")
    private String shipperAddress;

    @Column(name = "consignee_name", length = 128)
    private String consigneeName;

    @Column(name = "consignee_address")
    private String consigneeAddress;

    @Column(name = "consignee_country", length = 2)
    private String consigneeCountry;

    // --- commodity ----------------------------------------------------------

    @Column(name = "schedule_b_number", length = 16)
    private String scheduleBNumber;

    /** False when the Schedule B number is an untranslated HS code. */
    @Column(name = "schedule_b_translated", nullable = false)
    private boolean scheduleBTranslated;

    @Column(name = "commodity_description", length = COMMODITY_DESCRIPTION_MAX)
    private String commodityDescription;

    @Column(name = "quantity_value", precision = 14, scale = 3)
    private BigDecimal quantityValue;

    @Column(name = "quantity_unit", length = 16)
    private String quantityUnit;

    @Column(name = "value_usd", precision = 14, scale = 2)
    private BigDecimal valueUsd;

    // --- carriage -----------------------------------------------------------

    @Column(name = "carrier_scac", length = 8)
    private String carrierScac;

    @Column(name = "vessel_name", length = 128)
    private String vesselName;

    @Column(name = "voyage_number", length = 64)
    private String voyageNumber;

    @Column(name = "port_of_export_code", length = 8)
    private String portOfExportCode;

    @Column(name = "country_of_destination", length = 2)
    private String countryOfDestination;

    @Column(name = "estimated_etd")
    private LocalDate estimatedEtd;

    // --- lifecycle timestamps ------------------------------------------------

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "submitted_by", length = 64)
    private String submittedBy;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "rejection_reason_code", length = 16)
    private String rejectionReasonCode;

    @Column(name = "rejection_reason_description")
    private String rejectionReasonDescription;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "aes_submission_reference", length = 64)
    private String aesSubmissionReference;

    /** True when the accepted ITN came from the simulator rather than CBP. */
    @Column(name = "simulated", nullable = false)
    private boolean simulated;

    @Column(name = "amendment_reason")
    private String amendmentReason;

    @Column(name = "license_required", nullable = false)
    private boolean licenseRequired;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "export_license_id")
    private ExportLicense exportLicense;

    @OneToMany(mappedBy = "filing", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("issuedAt")
    private List<ItnRecord> itnRecords = new ArrayList<>();

    @OneToMany(mappedBy = "filing", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sequenceNumber")
    private List<EEIFilingHistory> history = new ArrayList<>();

    // ------------------------------------------------------------- 1. initiate

    /** Opens a Draft original filing for a booking. */
    public static EEIFiling initiate(String filingReference, UUID bookingId, Instant now, String actor) {
        return open(filingReference, bookingId, FilingType.ORIGINAL, null, null, now, actor);
    }

    private static EEIFiling open(
            String filingReference, UUID bookingId, FilingType filingType,
            UUID parentFilingId, String amendmentReason, Instant now, String actor
    ) {
        EEIFiling filing = new EEIFiling();
        filing.id = UUID.randomUUID();
        filing.filingReference = filingReference;
        filing.bookingId = bookingId;
        filing.filingType = filingType;
        filing.parentFilingId = parentFilingId;
        filing.amendmentReason = amendmentReason;
        filing.status = FilingStatus.DRAFT;
        filing.createdAt = now;
        filing.recordHistory(null, FilingStatus.DRAFT, now, actor,
                filingType == FilingType.ORIGINAL ? "Filing opened" : amendmentReason);
        filing.registerEvent(new ComplianceEvent.EEIFilingInitiated(
                filing.id, bookingId, filingReference, filingType, now));
        return filing;
    }

    // -------------------------------------------------------------- 2. compile

    /**
     * Fills in the EEI data. Permitted only while Draft — once CBP has the filing,
     * a change means a new filing, not an edit.
     */
    public void compile(
            String shipperName, String shipperEin, String shipperAddress,
            String consigneeName, String consigneeAddress, String consigneeCountry,
            ScheduleBCode scheduleBCode, String commodityDescription,
            BigDecimal quantityValue, String quantityUnit, BigDecimal valueUsd,
            String carrierScac, String vesselName, String voyageNumber,
            String portOfExportCode, String countryOfDestination, LocalDate estimatedEtd,
            boolean licenseRequired, Instant now, String actor
    ) {
        requireDraft("EEI data can only be compiled while the filing is a draft");
        if (commodityDescription != null && commodityDescription.length() > COMMODITY_DESCRIPTION_MAX) {
            throw new DomainRuleViolationException(
                    "Commodity description exceeds CBP's " + COMMODITY_DESCRIPTION_MAX + " character limit");
        }

        this.shipperName = shipperName;
        this.shipperEin = shipperEin;
        this.shipperAddress = shipperAddress;
        this.consigneeName = consigneeName;
        this.consigneeAddress = consigneeAddress;
        this.consigneeCountry = consigneeCountry;
        this.scheduleBNumber = scheduleBCode.value();
        this.scheduleBTranslated = scheduleBCode.translated();
        this.commodityDescription = commodityDescription;
        this.quantityValue = quantityValue;
        this.quantityUnit = quantityUnit;
        this.valueUsd = valueUsd;
        this.carrierScac = carrierScac;
        this.vesselName = vesselName;
        this.voyageNumber = voyageNumber;
        this.portOfExportCode = portOfExportCode;
        this.countryOfDestination = countryOfDestination;
        this.estimatedEtd = estimatedEtd;
        this.licenseRequired = licenseRequired;

        recordHistory(FilingStatus.DRAFT, FilingStatus.DRAFT, now, actor, "EEI data compiled");

        if (licenseRequired && exportLicense == null) {
            registerEvent(new ComplianceEvent.ExportLicenseRequired(
                    id, bookingId, scheduleBNumber, now));
        }
    }

    // ------------------------------------------------------- 11. export licence

    public void recordExportLicense(ExportLicense license, Instant now, String actor) {
        requireDraft("An export licence can only be added while the filing is a draft");
        this.exportLicense = license;
        this.licenseRequired = true;
        recordHistory(FilingStatus.DRAFT, FilingStatus.DRAFT, now, actor,
                "Export licence " + license.getLicenseNumber() + " recorded");
        registerEvent(new ComplianceEvent.ExportLicenseRecorded(
                id, bookingId, license.getLicenseNumber(), now));
    }

    // ---------------------------------------------------------------- 3. submit

    /**
     * Marks the filing as sent to CBP. Blocks when a licence is owed, and refuses a
     * filing that is missing data CBP requires.
     */
    public void markSubmitted(Instant now, String actor) {
        requireDraft("Only a draft filing can be submitted");
        if (licenseRequired && exportLicense == null) {
            throw new DomainRuleViolationException(
                    "Filing cites a licensable commodity — record the export licence before submitting");
        }
        List<String> missing = missingRequiredFields();
        if (!missing.isEmpty()) {
            throw new DomainRuleViolationException(
                    "Filing is incomplete, CBP requires: " + String.join(", ", missing));
        }

        this.status = FilingStatus.SUBMITTED;
        this.submittedAt = now;
        this.submittedBy = actor;
        recordHistory(FilingStatus.DRAFT, FilingStatus.SUBMITTED, now, actor, "Submitted to CBP");

        if (filingType == FilingType.AMENDMENT) {
            registerEvent(new ComplianceEvent.EEIAmendmentSubmitted(
                    id, bookingId, parentFilingId, filingReference, now));
        }
        registerEvent(new ComplianceEvent.EEISubmittedToCBP(
                id, bookingId, filingReference, filingType, now));
    }

    // ------------------------------------------------- 4 & 9. record acceptance

    /**
     * Records CBP acceptance and the ITN issued. Also used for amendment
     * acceptance, where {@code supersededItn} carries the ITN being replaced.
     */
    public ItnRecord recordAcceptance(
            ItnNumber itnNumber,
            String aesSubmissionReference,
            Instant acceptedAt,
            ItnRecord supersededItn,
            Instant now,
            String actor
    ) {
        requireStatus("Only a submitted filing can be accepted", FilingStatus.SUBMITTED);

        this.status = FilingStatus.ACCEPTED;
        this.acceptedAt = acceptedAt;
        this.aesSubmissionReference = aesSubmissionReference;
        this.simulated = itnNumber.isSimulated();

        ItnRecord record = ItnRecord.issued(this, bookingId, itnNumber, acceptedAt, now, actor);
        itnRecords.add(record);

        if (supersededItn != null) {
            supersededItn.supersedeBy(record.getId());
            registerEvent(new ComplianceEvent.ItnSuperseded(
                    id, bookingId, supersededItn.getItnNumber(), itnNumber.value(), now));
        }

        recordHistory(FilingStatus.SUBMITTED, FilingStatus.ACCEPTED, now, actor,
                "CBP accepted, ITN " + itnNumber.value());

        registerEvent(new ComplianceEvent.ItnNumberReceived(
                id, bookingId, filingReference, itnNumber.value(), itnNumber.isSimulated(), now));
        registerEvent(new ComplianceEvent.ItnGateCheckPassed(
                id, bookingId, itnNumber.value(), now));
        return record;
    }

    // ------------------------------------------------------ 5. record rejection

    public void recordRejection(String code, String description, Instant rejectedAt, Instant now, String actor) {
        requireStatus("Only a submitted filing can be rejected", FilingStatus.SUBMITTED);
        this.status = FilingStatus.REJECTED;
        this.rejectedAt = rejectedAt;
        this.rejectionReasonCode = code;
        this.rejectionReasonDescription = description;
        recordHistory(FilingStatus.SUBMITTED, FilingStatus.REJECTED, now, actor,
                "CBP rejected " + code + ": " + description);
        registerEvent(new ComplianceEvent.EEIFilingRejected(
                id, bookingId, filingReference, code, description, now));
    }

    // ---------------------------------------------- 6. correction, 7. amendment

    /**
     * A correction replaces a filing CBP never accepted, so no ITN exists yet. The
     * replacement is an Original, not an Amendment — there is nothing to amend.
     */
    public EEIFiling correctInto(String newFilingReference, Instant now, String actor) {
        requireStatus("Only a rejected filing can be corrected", FilingStatus.REJECTED);
        EEIFiling corrected = open(newFilingReference, bookingId, FilingType.ORIGINAL,
                id, "Correction of rejected filing " + filingReference, now, actor);
        corrected.copyDeclaredDataFrom(this);
        return corrected;
    }

    /**
     * An amendment changes a filing CBP already accepted, so an ITN exists and will
     * be superseded when the amendment is accepted.
     */
    public EEIFiling amendInto(String newFilingReference, String reason, Instant now, String actor) {
        requireStatus("Only an accepted filing can be amended", FilingStatus.ACCEPTED);
        EEIFiling amendment = open(newFilingReference, bookingId, FilingType.AMENDMENT,
                id, reason, now, actor);
        amendment.copyDeclaredDataFrom(this);
        return amendment;
    }

    /** Flags that facts have changed and an amendment is owed to CBP. */
    public void flagAmendmentRequired(String reason, LocalDate etdAtRisk, Instant now) {
        if (status != FilingStatus.ACCEPTED) {
            return;
        }
        registerEvent(new ComplianceEvent.EEIAmendmentRequired(
                id, bookingId, filingReference, reason, etdAtRisk, now));
    }

    // --------------------------------------------------------------- 10. cancel

    /**
     * Cancels the filing with CBP and voids any active ITN. Leaving an accepted
     * filing uncancelled after the shipment is called off leaves an export reported
     * to the US government that never happened.
     */
    public void cancel(String reason, Instant now, String actor) {
        if (status == FilingStatus.CANCELLED) {
            throw new DomainRuleViolationException("Filing " + filingReference + " is already cancelled");
        }
        FilingStatus from = status;
        String voided = activeItn().map(ItnRecord::getItnNumber).orElse(null);
        activeItn().ifPresent(ItnRecord::void_);

        this.status = FilingStatus.CANCELLED;
        this.cancelledAt = now;
        this.cancellationReason = reason;
        recordHistory(from, FilingStatus.CANCELLED, now, actor, reason);
        registerEvent(new ComplianceEvent.EEIFilingCancelled(
                id, bookingId, filingReference, voided, reason, now));
    }

    // ----------------------------------------------------------------- queries

    public Optional<ItnRecord> activeItn() {
        return itnRecords.stream().filter(ItnRecord::isActive).findFirst();
    }

    public List<ItnRecord> getItnRecords() {
        return Collections.unmodifiableList(itnRecords);
    }

    public List<EEIFilingHistory> getHistory() {
        return Collections.unmodifiableList(history);
    }

    /** Whether a filing is legally required — value over the threshold, or licensable. */
    public boolean isFilingRequired() {
        if (licenseRequired) {
            return true;
        }
        return valueUsd != null && valueUsd.compareTo(FILING_THRESHOLD_USD) > 0;
    }

    /** CBP-mandated fields that are still blank. */
    public List<String> missingRequiredFields() {
        List<String> missing = new ArrayList<>();
        if (isBlank(shipperName)) missing.add("shipperName");
        if (isBlank(shipperEin)) missing.add("shipperEin");
        if (isBlank(consigneeName)) missing.add("consigneeName");
        if (isBlank(consigneeCountry)) missing.add("consigneeCountry");
        if (isBlank(scheduleBNumber)) missing.add("scheduleBNumber");
        if (isBlank(commodityDescription)) missing.add("commodityDescription");
        if (valueUsd == null) missing.add("valueUsd");
        if (isBlank(carrierScac)) missing.add("carrierScac");
        if (isBlank(portOfExportCode)) missing.add("portOfExportCode");
        if (isBlank(countryOfDestination)) missing.add("countryOfDestination");
        if (estimatedEtd == null) missing.add("estimatedEtd");
        return missing;
    }

    public AesFilingClient.AesSubmission toSubmission(String supersedesItnNumber) {
        return new AesFilingClient.AesSubmission(
                filingReference, filingType, supersedesItnNumber,
                shipperEin, shipperName, shipperAddress,
                consigneeName, consigneeAddress, consigneeCountry,
                scheduleBNumber, commodityDescription, quantityValue, quantityUnit, valueUsd,
                carrierScac, vesselName, voyageNumber, portOfExportCode,
                countryOfDestination, estimatedEtd,
                exportLicense == null ? null : exportLicense.getLicenseNumber(),
                exportLicense == null ? null : exportLicense.getValueAuthorized());
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

    /** Events registered but not yet published; exposed for testing in isolation. */
    public java.util.Collection<Object> pendingEvents() {
        return domainEvents();
    }

    // ----------------------------------------------------------------- helpers

    private void copyDeclaredDataFrom(EEIFiling source) {
        this.shipperName = source.shipperName;
        this.shipperEin = source.shipperEin;
        this.shipperAddress = source.shipperAddress;
        this.consigneeName = source.consigneeName;
        this.consigneeAddress = source.consigneeAddress;
        this.consigneeCountry = source.consigneeCountry;
        this.scheduleBNumber = source.scheduleBNumber;
        this.scheduleBTranslated = source.scheduleBTranslated;
        this.commodityDescription = source.commodityDescription;
        this.quantityValue = source.quantityValue;
        this.quantityUnit = source.quantityUnit;
        this.valueUsd = source.valueUsd;
        this.carrierScac = source.carrierScac;
        this.vesselName = source.vesselName;
        this.voyageNumber = source.voyageNumber;
        this.portOfExportCode = source.portOfExportCode;
        this.countryOfDestination = source.countryOfDestination;
        this.estimatedEtd = source.estimatedEtd;
        this.licenseRequired = source.licenseRequired;
    }

    private void recordHistory(
            FilingStatus from, FilingStatus to, Instant at, String actor, String detail) {
        history.add(EEIFilingHistory.record(this, history.size() + 1, from, to, at, actor, detail));
    }

    private void requireDraft(String message) {
        requireStatus(message, FilingStatus.DRAFT);
    }

    private void requireStatus(String message, FilingStatus expected) {
        if (status != expected) {
            throw new DomainRuleViolationException(message + " (status was " + status + ")");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
