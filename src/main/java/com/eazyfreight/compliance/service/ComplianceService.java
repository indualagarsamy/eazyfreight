package com.eazyfreight.compliance.service;

import com.eazyfreight.booking.domain.Booking;
import com.eazyfreight.booking.domain.BookingCargoDetail;
import com.eazyfreight.booking.repository.BookingRepository;
import com.eazyfreight.compliance.client.AesFilingClient;
import com.eazyfreight.compliance.domain.EEIFiling;
import com.eazyfreight.compliance.domain.ExportLicense;
import com.eazyfreight.compliance.domain.FilingStatus;
import com.eazyfreight.compliance.domain.ItnNumber;
import com.eazyfreight.compliance.domain.ItnRecord;
import com.eazyfreight.compliance.domain.ScheduleBCode;
import com.eazyfreight.compliance.dto.AmendFilingRequest;
import com.eazyfreight.compliance.dto.CancelFilingRequest;
import com.eazyfreight.compliance.dto.CompileEEIDataRequest;
import com.eazyfreight.compliance.dto.EEIFilingResponse;
import com.eazyfreight.compliance.dto.RecordAcceptanceRequest;
import com.eazyfreight.compliance.dto.RecordExportLicenseRequest;
import com.eazyfreight.compliance.dto.RecordRejectionRequest;
import com.eazyfreight.compliance.exception.FilingNotFoundException;
import com.eazyfreight.compliance.repository.EEIFilingRepository;

import com.eazyfreight.common.ReferenceGenerator;
import com.eazyfreight.exception.BookingNotFoundException;
import com.eazyfreight.exception.DomainRuleViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for the Export Compliance context — the eleven commands from
 * the specification.
 *
 * <p>Submission goes through {@link AesFilingClient}, which is simulated. Nothing
 * here transmits to CBP.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ComplianceService {

    private final EEIFilingRepository filingRepository;
    private final BookingRepository bookingRepository;
    private final AesFilingClient aesClient;
    private final ReferenceGenerator referenceGenerator;
    private final Clock clock;

    // ------------------------------------------------------------- 1. initiate

    @Transactional
    public EEIFilingResponse initiateFiling(UUID bookingId, String actor) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        boolean alreadyOpen = filingRepository.findByBookingId(bookingId).stream()
                .anyMatch(existing -> existing.getStatus() == FilingStatus.DRAFT
                        || existing.getStatus() == FilingStatus.SUBMITTED);
        if (alreadyOpen) {
            throw new DomainRuleViolationException(
                    "Booking " + booking.getBookingReference() + " already has an open EEI filing");
        }

        EEIFiling filing = EEIFiling.initiate(
                referenceGenerator.next(ReferenceGenerator.EEI_FILING_PREFIX),
                bookingId, clock.instant(), actor);

        prefillFromBooking(filing, booking, actor);
        return EEIFilingResponse.fromEntity(filingRepository.save(filing));
    }

    // -------------------------------------------------------------- 2. compile

    @Transactional
    public EEIFilingResponse compile(UUID filingId, CompileEEIDataRequest request, String actor) {
        EEIFiling filing = getFilingOrThrow(filingId);
        ScheduleBCode scheduleB = request.scheduleBNumber() == null || request.scheduleBNumber().isBlank()
                ? scheduleBFromBooking(filing.getBookingId())
                : ScheduleBCode.declared(request.scheduleBNumber());

        log.info("Compiling EEI filing {} for shipper {} (EIN {}) at {}",
                filingId, request.shipperName(), request.shipperEin(), request.shipperAddress());

        filing.compile(
                request.shipperName(), request.shipperEin(), request.shipperAddress(),
                request.consigneeName(), request.consigneeAddress(), request.consigneeCountry(),
                scheduleB, request.commodityDescription(),
                request.quantityValue(), request.quantityUnit(), request.valueUsd(),
                request.carrierScac(), request.vesselName(), request.voyageNumber(),
                request.portOfExportCode(), request.countryOfDestination(), request.estimatedEtd(),
                request.licenseRequired(), clock.instant(), actor);

        return EEIFilingResponse.fromEntity(filingRepository.save(filing));
    }

    // ---------------------------------------------------------------- 3. submit

    /**
     * Submits to the (simulated) filing system and records whatever comes back. An
     * outage leaves the filing Submitted for retry rather than guessing at an
     * outcome — never infer acceptance from silence.
     */
    @Transactional
    public EEIFilingResponse submitToCbp(UUID filingId, String actor) {
        EEIFiling filing = getFilingOrThrow(filingId);
        Instant now = clock.instant();

        String supersedes = activeItnFor(filing.getBookingId())
                .map(ItnRecord::getItnNumber)
                .orElse(null);

        filing.markSubmitted(now, actor);

        AesFilingClient.AesSubmission submission = filing.toSubmission(supersedes);
        log.info("Submitting AES filing {} — shipper {} (EIN {}) at {}, consignee at {}",
                filing.getFilingReference(), submission.shipperName(), submission.shipperEin(),
                submission.shipperAddress(), submission.consigneeAddress());

        try {
            AesFilingClient.AesResponse response = aesClient.submit(submission);
            if (response.accepted()) {
                applyAcceptance(filing, new ItnNumber(response.itnNumber()),
                        response.aesSubmissionReference(), response.respondedAt(), now, actor);
            } else {
                filing.recordRejection(response.rejectionCode(), response.rejectionDescription(),
                        response.respondedAt(), now, actor);
            }
        } catch (AesFilingClient.AesUnavailableException ex) {
            log.warn("AES unavailable for filing {} — left submitted for retry: {}",
                    filing.getFilingReference(), ex.getMessage());
        }

        return EEIFilingResponse.fromEntity(filingRepository.save(filing));
    }

    // ------------------------------------------------- 4 & 9. record acceptance

    /** Manual entry of an acceptance keyed from the ACE portal. */
    @Transactional
    public EEIFilingResponse recordAcceptance(UUID filingId, RecordAcceptanceRequest request, String actor) {
        EEIFiling filing = getFilingOrThrow(filingId);
        Instant now = clock.instant();
        applyAcceptance(filing, new ItnNumber(request.itnNumber()),
                request.aesSubmissionReference(),
                request.acceptedAt() == null ? now : request.acceptedAt(), now, actor);
        return EEIFilingResponse.fromEntity(filingRepository.save(filing));
    }

    // ------------------------------------------------------ 5. record rejection

    @Transactional
    public EEIFilingResponse recordRejection(UUID filingId, RecordRejectionRequest request, String actor) {
        EEIFiling filing = getFilingOrThrow(filingId);
        Instant now = clock.instant();
        filing.recordRejection(request.rejectionCode(), request.rejectionDescription(),
                request.rejectedAt() == null ? now : request.rejectedAt(), now, actor);
        return EEIFilingResponse.fromEntity(filingRepository.save(filing));
    }

    // ------------------------------------------------------------ 6. correction

    @Transactional
    public EEIFilingResponse submitCorrection(UUID rejectedFilingId, String actor) {
        EEIFiling rejected = getFilingOrThrow(rejectedFilingId);
        EEIFiling corrected = rejected.correctInto(
                referenceGenerator.next(ReferenceGenerator.EEI_FILING_PREFIX), clock.instant(), actor);
        filingRepository.save(rejected);
        return EEIFilingResponse.fromEntity(filingRepository.save(corrected));
    }

    // ------------------------------------------------------- 7 & 8. amendment

    @Transactional
    public EEIFilingResponse initiateAmendment(UUID acceptedFilingId, AmendFilingRequest request, String actor) {
        EEIFiling accepted = getFilingOrThrow(acceptedFilingId);
        EEIFiling amendment = accepted.amendInto(
                referenceGenerator.next(ReferenceGenerator.EEI_FILING_PREFIX),
                request.reason(), clock.instant(), actor);
        filingRepository.save(accepted);
        return EEIFilingResponse.fromEntity(filingRepository.save(amendment));
    }

    // --------------------------------------------------------------- 10. cancel

    @Transactional
    public EEIFilingResponse cancel(UUID filingId, CancelFilingRequest request, String actor) {
        EEIFiling filing = getFilingOrThrow(filingId);
        filing.cancel(request.reason(), clock.instant(), actor);
        return EEIFilingResponse.fromEntity(filingRepository.save(filing));
    }

    // ------------------------------------------------------ 11. export licence

    @Transactional
    public EEIFilingResponse recordExportLicense(
            UUID filingId, RecordExportLicenseRequest request, String actor) {
        EEIFiling filing = getFilingOrThrow(filingId);
        if (request.validUntil().isBefore(request.validFrom())) {
            throw new DomainRuleViolationException("Licence validUntil cannot precede validFrom");
        }
        filing.recordExportLicense(ExportLicense.of(
                request.licenseNumber(), request.issuingAuthority(), request.licenseType(),
                request.commodityEccn(), request.validFrom(), request.validUntil(),
                request.valueAuthorized()), clock.instant(), actor);
        return EEIFilingResponse.fromEntity(filingRepository.save(filing));
    }

    /** Flags that an accepted filing is now out of date and owes CBP an amendment. */
    @Transactional
    public void flagAmendmentRequired(UUID bookingId, String reason) {
        acceptedFilingFor(bookingId).ifPresent(filing -> {
            filing.flagAmendmentRequired(reason, filing.getEstimatedEtd(), clock.instant());
            filingRepository.save(filing);
            log.info("Filing {} needs an amendment: {}", filing.getFilingReference(), reason);
        });
    }

    // ----------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public List<EEIFilingResponse> findAll() {
        return filingRepository.findAll().stream().map(EEIFilingResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public EEIFilingResponse findById(UUID filingId) {
        return EEIFilingResponse.fromEntity(getFilingOrThrow(filingId));
    }

    @Transactional(readOnly = true)
    public EEIFilingResponse findByReference(String filingReference) {
        return filingRepository.findByFilingReference(filingReference)
                .map(EEIFilingResponse::fromEntity)
                .orElseThrow(() -> new FilingNotFoundException(filingReference));
    }

    @Transactional(readOnly = true)
    public List<EEIFilingResponse> findByBooking(UUID bookingId) {
        return filingRepository.findByBookingId(bookingId).stream()
                .map(EEIFilingResponse::fromEntity)
                .toList();
    }

    /** Filings awaiting a CBP answer — the queue compliance staff works from. */
    @Transactional(readOnly = true)
    public List<EEIFilingResponse> findAwaitingCbp() {
        return filingRepository.findByStatusIn(List.of(FilingStatus.DRAFT, FilingStatus.SUBMITTED)).stream()
                .map(EEIFilingResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean hasActiveItn(UUID bookingId) {
        return activeItnFor(bookingId).isPresent();
    }

    // ----------------------------------------------------------------- helpers

    /**
     * Records an acceptance, standing down any ITN it replaces first.
     *
     * <p>The deactivation is flushed before the replacement is written. PostgreSQL
     * enforces one active ITN per booking with a partial unique index, and Hibernate
     * orders inserts ahead of updates within a flush — so without the explicit flush
     * the new row is inserted while the old one is still active and the index
     * rejects it. H2 has no partial indexes, so only a run against PostgreSQL
     * surfaces this.
     */
    private void applyAcceptance(
            EEIFiling filing, ItnNumber itn, String submissionRef,
            Instant acceptedAt, Instant now, String actor) {
        ItnRecord superseded = activeItnFor(filing.getBookingId()).orElse(null);

        if (superseded != null) {
            superseded.deactivate();
            filingRepository.saveAndFlush(superseded.getFiling());
        }

        filing.recordAcceptance(itn, submissionRef, acceptedAt, superseded, now, actor);

        if (superseded != null) {
            filingRepository.save(superseded.getFiling());
        }
    }

    private Optional<ItnRecord> activeItnFor(UUID bookingId) {
        return filingRepository.findByBookingId(bookingId).stream()
                .flatMap(filing -> filing.activeItn().stream())
                .findFirst();
    }

    private Optional<EEIFiling> acceptedFilingFor(UUID bookingId) {
        return filingRepository.findFirstByBookingIdAndStatusOrderByAcceptedAtDesc(
                bookingId, FilingStatus.ACCEPTED);
    }

    /** Seeds the draft with what the booking already knows, to save re-keying. */
    private void prefillFromBooking(EEIFiling filing, Booking booking, String actor) {
        BigDecimal declaredValue = booking.totalValueUsd();
        BigDecimal totalPieces = BigDecimal.valueOf(
                booking.getCargoDetails().stream().mapToInt(BookingCargoDetail::getPieces).sum());
        String description = booking.getCargoDetails().stream()
                .map(BookingCargoDetail::getDescription)
                .findFirst()
                .orElse(null);

        filing.compile(
                null, null, null, null, null, null,
                scheduleBFromBooking(booking.getId()),
                description,
                totalPieces.signum() == 0 ? null : totalPieces,
                "PCS",
                declaredValue.signum() == 0 ? null : declaredValue,
                null,
                booking.getCarrierBooking() == null ? null : booking.getCarrierBooking().getVesselName(),
                booking.getCarrierBooking() == null ? null : booking.getCarrierBooking().getVoyageNumber(),
                booking.getOriginPortCode(),
                null,
                booking.getCarrierBooking() == null
                        ? booking.getRequestedEtd() : booking.getCarrierBooking().getConfirmedEtd(),
                false, clock.instant(), actor);
    }

    private ScheduleBCode scheduleBFromBooking(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .flatMap(booking -> booking.getCargoDetails().stream().findFirst())
                .map(cargo -> ScheduleBCode.fromHsCode(cargo.getHsCode()))
                .orElseThrow(() -> new DomainRuleViolationException(
                        "Booking has no cargo to derive a Schedule B number from"));
    }

    private EEIFiling getFilingOrThrow(UUID filingId) {
        return filingRepository.findById(filingId)
                .orElseThrow(() -> new FilingNotFoundException(filingId));
    }
}
