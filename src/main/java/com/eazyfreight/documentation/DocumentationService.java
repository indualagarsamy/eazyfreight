package com.eazyfreight.documentation;

import com.eazyfreight.booking.Booking;
import com.eazyfreight.booking.BookingCargoDetail;
import com.eazyfreight.booking.BookingRepository;
import com.eazyfreight.common.ReferenceGenerator;
import com.eazyfreight.common.pdf.RenderedDocument;
import com.eazyfreight.compliance.EEIFiling;
import com.eazyfreight.compliance.EEIFilingRepository;
import com.eazyfreight.compliance.FilingStatus;
import com.eazyfreight.compliance.ItnRecord;
import com.eazyfreight.documentation.DocumentationEnums.DistributionChannel;
import com.eazyfreight.documentation.DocumentationEnums.FreightTerms;
import com.eazyfreight.exception.BookingNotFoundException;
import com.eazyfreight.exception.DomainRuleViolationException;
import com.eazyfreight.logistics.ContainerAssignment;
import com.eazyfreight.logistics.ContainerAssignmentRepository;
import com.eazyfreight.logistics.SealRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for the Documentation context — the convergence point.
 *
 * <p>This is the only place in the system that reads from three other contexts at
 * once, because that is what the track is: Logistics supplies the container and
 * seal, Compliance supplies the ITN, Booking supplies the carrier reference, and
 * nothing can be sent to the carrier until all four are present.
 *
 * <p>{@link #checkPreconditions} is the gate. The monolith has no equivalent, which
 * is why a House BOL there can be issued with a stale seal number or no ITN at all.
 */
@Service
@RequiredArgsConstructor
public class DocumentationService {

    private final MasterBOLInstructionsRepository instructionsRepository;
    private final MasterBOLRepository masterRepository;
    private final HouseBOLRepository houseRepository;
    private final BookingRepository bookingRepository;
    private final ContainerAssignmentRepository logisticsRepository;
    private final EEIFilingRepository filingRepository;
    private final HouseBOLPdfRenderer pdfRenderer;
    private final ReferenceGenerator referenceGenerator;
    private final Clock clock;

    // -------------------------------------------------------- 1. preconditions

    @Transactional(readOnly = true)
    public DocumentationResponses.Preconditions checkPreconditions(UUID bookingId) {
        Booking booking = booking(bookingId);
        Optional<ContainerAssignment> logistics = logisticsRepository.findByBookingId(bookingId);

        String containerNumber = logistics.map(ContainerAssignment::getContainerNumber).orElse(null);
        String sealNumber = logistics.flatMap(ContainerAssignment::activeSeal)
                .map(SealRecord::getSealNumber).orElse(null);
        String itnNumber = activeItn(bookingId).orElse(null);
        String carrierBookingRef = booking.carrierBookingRef();

        List<String> missing = new ArrayList<>();
        if (containerNumber == null) missing.add("containerNumber");
        if (sealNumber == null) missing.add("sealNumber");
        if (itnNumber == null) missing.add("itnNumber");
        if (carrierBookingRef == null) missing.add("carrierBookingRef");

        return new DocumentationResponses.Preconditions(bookingId, missing.isEmpty(),
                containerNumber, sealNumber, itnNumber, carrierBookingRef, missing);
    }

    // --------------------------------------------------------- 2-6. instructions

    @Transactional
    public DocumentationResponses.Instructions compileInstructions(
            UUID bookingId, DocumentationRequests.CompileInstructions request, String actor) {
        return compileInstructions(bookingId, request, null, actor);
    }

    private DocumentationResponses.Instructions compileInstructions(
            UUID bookingId, DocumentationRequests.CompileInstructions request,
            UUID supersedes, String actor) {
        DocumentationResponses.Preconditions preconditions = checkPreconditions(bookingId);
        if (!preconditions.met()) {
            throw new DomainRuleViolationException(
                    "Master BOL instructions cannot be compiled — still waiting on: "
                            + String.join(", ", preconditions.missing()));
        }

        Booking booking = booking(bookingId);
        ContainerAssignment logistics = logisticsRepository.findByBookingId(bookingId).orElseThrow();
        BookingCargoDetail firstCargo = booking.getCargoDetails().stream().findFirst().orElse(null);

        MasterBOLInstructions instructions = MasterBOLInstructions.compile(
                referenceGenerator.next(ReferenceGenerator.INSTRUCTIONS_PREFIX),
                bookingId,
                booking.getCarrierBooking() == null ? null : booking.getCarrierBooking().getCarrierId(),
                preconditions.carrierBookingRef(),
                preconditions.containerNumber(), preconditions.sealNumber(), preconditions.itnNumber(),
                request.shipperName(), request.shipperAddress(),
                request.consigneeName(), request.consigneeAddress(),
                request.notifyPartyName(), request.notifyPartyAddress(),
                booking.getOriginPortCode(), booking.getDestinationPortCode(),
                booking.getCarrierBooking() == null ? null : booking.getCarrierBooking().getVesselName(),
                booking.getCarrierBooking() == null ? null : booking.getCarrierBooking().getVoyageNumber(),
                firstCargo == null ? "Cargo" : firstCargo.getDescription(),
                firstCargo == null ? null : firstCargo.getHsCode(),
                actualWeight(logistics, booking), actualPieces(logistics, booking),
                actualCbm(logistics, booking),
                request.marksAndNumbers(),
                request.freightTerms() == null ? FreightTerms.PREPAID : request.freightTerms(),
                request.documentationCutOffDate(), supersedes,
                clock.instant(), actor);

        return DocumentationResponses.Instructions.from(instructionsRepository.save(instructions));
    }

    @Transactional
    public DocumentationResponses.Instructions approveInstructions(UUID instructionsId, String actor) {
        MasterBOLInstructions instructions = instructions(instructionsId);
        instructions.approve(clock.instant(), actor);
        return DocumentationResponses.Instructions.from(instructionsRepository.save(instructions));
    }

    @Transactional
    public DocumentationResponses.Instructions sendInstructions(UUID instructionsId) {
        MasterBOLInstructions instructions = instructions(instructionsId);
        instructions.send(LocalDate.now(clock), clock.instant());
        return DocumentationResponses.Instructions.from(instructionsRepository.save(instructions));
    }

    @Transactional
    public DocumentationResponses.Instructions recordCarrierQuery(
            UUID instructionsId, DocumentationRequests.CarrierQuery request) {
        MasterBOLInstructions instructions = instructions(instructionsId);
        instructions.recordCarrierQuery(request.query(), clock.instant());
        return DocumentationResponses.Instructions.from(instructionsRepository.save(instructions));
    }

    /** A correction is a new instructions record referencing the queried one. */
    @Transactional
    public DocumentationResponses.Instructions resendCorrectedInstructions(
            UUID instructionsId, DocumentationRequests.CompileInstructions request, String actor) {
        MasterBOLInstructions original = instructions(instructionsId);
        DocumentationResponses.Instructions corrected =
                compileInstructions(original.getBookingId(), request, original.getId(), actor);
        original.supersede();
        instructionsRepository.save(original);
        return corrected;
    }

    // ------------------------------------------------------ 7-10. Master BOL

    @Transactional
    public DocumentationResponses.Master recordMasterBolReceived(
            UUID instructionsId, DocumentationRequests.MasterBOLReceived request, String actor) {
        MasterBOLInstructions instructions = instructions(instructionsId);
        if (instructions.getStatus() != DocumentationEnums.InstructionsStatus.SENT) {
            throw new DomainRuleViolationException(
                    "A Master BOL can only arrive against instructions that were sent (status was "
                            + instructions.getStatus() + ")");
        }
        MasterBOL master = MasterBOL.received(instructions.getBookingId(), instructionsId,
                request.masterBolNumber(), instructions.getCarrierId(),
                request.issuedByCarrierAt(), request.documentFileReference(),
                clock.instant(), actor);
        return DocumentationResponses.Master.from(masterRepository.save(master));
    }

    @Transactional
    public DocumentationResponses.Master verifyMasterBol(UUID masterBolId, String actor) {
        MasterBOL master = master(masterBolId);
        master.verify(clock.instant(), actor);
        return DocumentationResponses.Master.from(masterRepository.save(master));
    }

    @Transactional
    public DocumentationResponses.Master raiseDiscrepancy(
            UUID masterBolId, DocumentationRequests.Discrepancy request) {
        MasterBOL master = master(masterBolId);
        master.raiseDiscrepancy(request.discrepancyFields(), clock.instant());
        return DocumentationResponses.Master.from(masterRepository.save(master));
    }

    @Transactional
    public DocumentationResponses.Master recordMasterBolCorrection(
            UUID masterBolId, DocumentationRequests.MasterBOLCorrection request) {
        MasterBOL master = master(masterBolId);
        master.recordCorrection(request.correctedMasterBolNumber(),
                request.documentFileReference(), clock.instant());
        return DocumentationResponses.Master.from(masterRepository.save(master));
    }

    // ------------------------------------------------------ 11-13. House BOL

    /**
     * Issues the House BOL. Refused unless the carrier's Master BOL has been
     * verified against what we instructed.
     */
    @Transactional
    public DocumentationResponses.House generateHouseBol(
            UUID masterBolId, DocumentationRequests.GenerateHouseBOL request, String actor) {
        MasterBOL master = master(masterBolId);
        if (!master.isVerified()) {
            throw new DomainRuleViolationException(
                    "Master BOL " + master.getMasterBolNumber()
                            + " must be verified against the instructions before a House BOL is issued "
                            + "(verification is " + master.getVerificationStatus() + ")");
        }
        if (houseRepository.findByBookingIdAndActiveTrue(master.getBookingId()).isPresent()) {
            throw new DomainRuleViolationException(
                    "An active House BOL already exists for this booking — amend it instead");
        }

        MasterBOLInstructions instructions = instructions(master.getInstructionsId());
        Booking booking = booking(master.getBookingId());

        HouseBOL house = HouseBOL.issue(
                referenceGenerator.next(ReferenceGenerator.HOUSE_BOL_PREFIX),
                master.getBookingId(), masterBolId, request.releaseType(),
                booking.getShipperId(), instructions.getShipperName(), instructions.getShipperAddress(),
                booking.getConsigneeId(), instructions.getConsigneeName(),
                instructions.getConsigneeAddress(),
                instructions.getNotifyPartyName(), instructions.getNotifyPartyAddress(),
                instructions.getPortOfLoadingCode(), instructions.getPortOfDischargeCode(),
                instructions.getVesselName(), instructions.getVoyageNumber(),
                instructions.getContainerNumber(), instructions.getSealNumber(),
                instructions.getCargoDescription(), instructions.getHsCode(),
                instructions.getActualWeightKg(), instructions.getActualPieces(),
                instructions.getActualCbm(), instructions.getMarksAndNumbers(),
                request.freightTerms() == null
                        ? instructions.getFreightTerms() : request.freightTerms(),
                clock.instant(), actor);

        return DocumentationResponses.House.from(houseRepository.save(house));
    }

    /**
     * Renders the House BOL and records that the document was produced.
     *
     * <p>The bytes are not stored. A revision is immutable once issued — an amendment
     * creates a new one rather than editing this — so the document is a pure function of
     * the record and rendering it again produces the same file. What is worth recording
     * is that it was produced at all, because the distribution history refers to it.
     */
    @Transactional
    public RenderedDocument generateHouseBolPdf(UUID houseBolId) {
        HouseBOL house = house(houseBolId);
        byte[] pdf = pdfRenderer.render(house);
        house.recordPdfGenerated(pdfRenderer.fileName(house));
        houseRepository.save(house);
        return new RenderedDocument(pdfRenderer.fileName(house), pdf);
    }

    /** Re-renders without recording anything. Downloading a document is not an event. */
    @Transactional(readOnly = true)
    public RenderedDocument houseBolPdf(UUID houseBolId) {
        HouseBOL house = house(houseBolId);
        return new RenderedDocument(pdfRenderer.fileName(house), pdfRenderer.render(house));
    }

    // ------------------------------------------------- 14-16. distribution

    @Transactional
    public DocumentationResponses.House distribute(
            UUID houseBolId, DocumentationRequests.Distribute request, String actor) {
        HouseBOL house = house(houseBolId);
        house.recordDistribution(request.recipient(), request.recipientName(),
                request.recipientAddress(),
                request.channel() == null ? DistributionChannel.EMAIL : request.channel(),
                request.reference(), clock.instant(), actor);
        return DocumentationResponses.House.from(houseRepository.save(house));
    }

    // ---------------------------------------------------- 17-18. originals

    @Transactional
    public DocumentationResponses.House releaseOriginals(
            UUID houseBolId, DocumentationRequests.ReleaseOriginals request) {
        HouseBOL house = house(houseBolId);
        house.releaseOriginals(request.releasedTo(), request.courierReference(), clock.instant());
        return DocumentationResponses.House.from(houseRepository.save(house));
    }

    @Transactional
    public DocumentationResponses.House surrenderOriginals(
            UUID houseBolId, DocumentationRequests.SurrenderOriginals request) {
        HouseBOL house = house(houseBolId);
        house.recordOriginalsSurrendered(request.count(), clock.instant());
        return DocumentationResponses.House.from(houseRepository.save(house));
    }

    // --------------------------------------------------- 19-21. amendment

    @Transactional
    public DocumentationResponses.House amendHouseBol(
            UUID houseBolId, DocumentationRequests.AmendHouseBOL request, String actor) {
        HouseBOL current = house(houseBolId);
        HouseBOL next = current.amendInto(request.reason(), null,
                request.consigneeName(), request.consigneeAddress(),
                request.notifyPartyName(), request.notifyPartyAddress(),
                request.vesselName(), request.voyageNumber(), request.sealNumber(),
                request.cargoDescription(), request.weightKg(), request.pieces(), request.cbm(),
                request.releaseType(), clock.instant(), actor);

        // Two constraints pull in opposite directions here. The partial unique index
        // wants the predecessor deactivated before the successor is inserted; the
        // superseded_by foreign key wants the successor to exist before anything
        // points at it. So: flush the deactivation with a null pointer, insert the
        // successor, then link them.
        houseRepository.saveAndFlush(current);
        HouseBOL saved = houseRepository.saveAndFlush(next);
        current.linkSupersededBy(saved.getId());
        houseRepository.save(current);
        return DocumentationResponses.House.from(saved);
    }

    @Transactional
    public DocumentationResponses.House voidHouseBol(
            UUID houseBolId, DocumentationRequests.VoidHouseBOL request) {
        HouseBOL house = house(houseBolId);
        house.voidRevision(null, clock.instant());
        return DocumentationResponses.House.from(houseRepository.save(house));
    }

    // ----------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public List<DocumentationResponses.Instructions> instructionsForBooking(UUID bookingId) {
        return instructionsRepository.findByBookingId(bookingId).stream()
                .map(DocumentationResponses.Instructions::from).toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentationResponses.Master> masterBolsForBooking(UUID bookingId) {
        return masterRepository.findByBookingId(bookingId).stream()
                .map(DocumentationResponses.Master::from).toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentationResponses.House> houseBolsForBooking(UUID bookingId) {
        return houseRepository.findByBookingId(bookingId).stream()
                .map(DocumentationResponses.House::from).toList();
    }

    @Transactional(readOnly = true)
    public DocumentationResponses.House houseBol(UUID houseBolId) {
        return DocumentationResponses.House.from(house(houseBolId));
    }

    /** Every live House BOL — one revision per booking. */
    @Transactional(readOnly = true)
    public List<DocumentationResponses.House> activeHouseBols() {
        return houseRepository.findByActiveTrue().stream()
                .map(DocumentationResponses.House::from).toList();
    }

    /** Full revision history for a BOL number, oldest first. */
    @Transactional(readOnly = true)
    public List<DocumentationResponses.House> revisions(String houseBolNumber) {
        return houseRepository.findByHouseBolNumberOrderByRevisionNumber(houseBolNumber).stream()
                .map(DocumentationResponses.House::from).toList();
    }

    // ----------------------------------------------------------------- helpers

    private Optional<String> activeItn(UUID bookingId) {
        return filingRepository.findByBookingId(bookingId).stream()
                .filter(filing -> filing.getStatus() == FilingStatus.ACCEPTED)
                .flatMap(filing -> filing.activeItn().stream())
                .map(ItnRecord::getItnNumber)
                .findFirst();
    }

    private BigDecimal actualWeight(ContainerAssignment logistics, Booking booking) {
        return logistics.getActualCargoDetails() != null
                ? logistics.getActualCargoDetails().getActualWeightKg()
                : booking.totalWeightKg();
    }

    private Integer actualPieces(ContainerAssignment logistics, Booking booking) {
        return logistics.getActualCargoDetails() != null
                ? logistics.getActualCargoDetails().getActualPieces()
                : booking.getCargoDetails().stream().mapToInt(BookingCargoDetail::getPieces).sum();
    }

    private BigDecimal actualCbm(ContainerAssignment logistics, Booking booking) {
        if (logistics.getActualCargoDetails() != null) {
            return logistics.getActualCargoDetails().getActualCbm();
        }
        return booking.getCargoDetails().stream()
                .map(BookingCargoDetail::getCbm)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Booking booking(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
    }

    private MasterBOLInstructions instructions(UUID id) {
        return instructionsRepository.findById(id).orElseThrow(() ->
                new DocumentationNotFoundException("Master BOL instructions not found: " + id));
    }

    private MasterBOL master(UUID id) {
        return masterRepository.findById(id).orElseThrow(() ->
                new DocumentationNotFoundException("Master BOL not found: " + id));
    }

    private HouseBOL house(UUID id) {
        return houseRepository.findById(id).orElseThrow(() ->
                new DocumentationNotFoundException("House BOL not found: " + id));
    }
}
