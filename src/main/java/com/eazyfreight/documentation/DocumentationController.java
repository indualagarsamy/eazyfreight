package com.eazyfreight.documentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Documentation endpoints — the twenty-one commands from the specification. */
@RestController
@RequestMapping("/api/documentation")
@RequiredArgsConstructor
public class DocumentationController {

    private static final String ACTOR_HEADER = "X-Actor";
    private static final String DEFAULT_ACTOR = "operations";

    private final DocumentationService documentationService;

    // ------------------------------------------------------------ preconditions

    /** 1. CheckDocumentationPreconditions */
    @GetMapping("/bookings/{bookingId}/preconditions")
    public DocumentationResponses.Preconditions preconditions(@PathVariable UUID bookingId) {
        return documentationService.checkPreconditions(bookingId);
    }

    @GetMapping("/bookings/{bookingId}/instructions")
    public List<DocumentationResponses.Instructions> instructions(@PathVariable UUID bookingId) {
        return documentationService.instructionsForBooking(bookingId);
    }

    @GetMapping("/bookings/{bookingId}/master-bols")
    public List<DocumentationResponses.Master> masterBols(@PathVariable UUID bookingId) {
        return documentationService.masterBolsForBooking(bookingId);
    }

    @GetMapping("/bookings/{bookingId}/house-bols")
    public List<DocumentationResponses.House> houseBols(@PathVariable UUID bookingId) {
        return documentationService.houseBolsForBooking(bookingId);
    }

    @GetMapping("/house-bols")
    public List<DocumentationResponses.House> activeHouseBols() {
        return documentationService.activeHouseBols();
    }

    @GetMapping("/house-bols/{id}")
    public DocumentationResponses.House houseBol(@PathVariable UUID id) {
        return documentationService.houseBol(id);
    }

    /** Every revision of a BOL number, oldest first. */
    @GetMapping("/house-bols/by-number/{houseBolNumber}/revisions")
    public List<DocumentationResponses.House> revisions(@PathVariable String houseBolNumber) {
        return documentationService.revisions(houseBolNumber);
    }

    // ------------------------------------------------------------- instructions

    /** 2. CompileMasterBOLInstructions */
    @PostMapping("/bookings/{bookingId}/instructions")
    public ResponseEntity<DocumentationResponses.Instructions> compile(
            @PathVariable UUID bookingId,
            @RequestBody DocumentationRequests.CompileInstructions request,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentationService.compileInstructions(bookingId, request, actor));
    }

    /** 3. ApproveMasterBOLInstructions */
    @PostMapping("/instructions/{id}/approve")
    public DocumentationResponses.Instructions approve(
            @PathVariable UUID id,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return documentationService.approveInstructions(id, actor);
    }

    /** 4. SendMasterBOLInstructionsToCarrier */
    @PostMapping("/instructions/{id}/send")
    public DocumentationResponses.Instructions send(@PathVariable UUID id) {
        return documentationService.sendInstructions(id);
    }

    /** 5. RecordCarrierQuery */
    @PostMapping("/instructions/{id}/carrier-query")
    public DocumentationResponses.Instructions carrierQuery(
            @PathVariable UUID id,
            @Valid @RequestBody DocumentationRequests.CarrierQuery request) {
        return documentationService.recordCarrierQuery(id, request);
    }

    /** 6. ResendCorrectedInstructions */
    @PostMapping("/instructions/{id}/resend-corrected")
    public ResponseEntity<DocumentationResponses.Instructions> resendCorrected(
            @PathVariable UUID id,
            @RequestBody DocumentationRequests.CompileInstructions request,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentationService.resendCorrectedInstructions(id, request, actor));
    }

    // ---------------------------------------------------------------- Master BOL

    /** 7. RecordMasterBOLReceived */
    @PostMapping("/instructions/{id}/master-bol")
    public ResponseEntity<DocumentationResponses.Master> masterBolReceived(
            @PathVariable UUID id,
            @Valid @RequestBody DocumentationRequests.MasterBOLReceived request,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentationService.recordMasterBolReceived(id, request, actor));
    }

    /** 8. VerifyMasterBOL */
    @PostMapping("/master-bols/{id}/verify")
    public DocumentationResponses.Master verify(
            @PathVariable UUID id,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return documentationService.verifyMasterBol(id, actor);
    }

    /** 9. RaiseMasterBOLDiscrepancy */
    @PostMapping("/master-bols/{id}/discrepancy")
    public DocumentationResponses.Master discrepancy(
            @PathVariable UUID id,
            @Valid @RequestBody DocumentationRequests.Discrepancy request) {
        return documentationService.raiseDiscrepancy(id, request);
    }

    /** 10. RecordMasterBOLCorrection */
    @PostMapping("/master-bols/{id}/correction")
    public DocumentationResponses.Master correction(
            @PathVariable UUID id,
            @RequestBody DocumentationRequests.MasterBOLCorrection request) {
        return documentationService.recordMasterBolCorrection(id, request);
    }

    // ----------------------------------------------------------------- House BOL

    /** 11-12. ConfirmReleaseType and GenerateHouseBOL */
    @PostMapping("/master-bols/{id}/house-bol")
    public ResponseEntity<DocumentationResponses.House> generateHouseBol(
            @PathVariable UUID id,
            @Valid @RequestBody DocumentationRequests.GenerateHouseBOL request,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentationService.generateHouseBol(id, request, actor));
    }

    /**
     * 13. GenerateHouseBOLPDF — renders the document and returns it as a download.
     *
     * <p>The command and the file are one round trip. Splitting them into "generate"
     * and "then fetch" would leave the caller holding a reference to a document the
     * server did not keep.
     */
    @PostMapping(value = "/house-bols/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<Resource> generatePdf(@PathVariable UUID id) {
        return documentationService.generateHouseBolPdf(id).asAttachment();
    }

    /** Re-download of a document already produced. Renders again; records nothing. */
    @GetMapping(value = "/house-bols/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<Resource> downloadPdf(@PathVariable UUID id) {
        return documentationService.houseBolPdf(id).asAttachment();
    }

    /** 14-16. SendHouseBOLToShipper / Consignee / NotifyParty */
    @PostMapping("/house-bols/{id}/distribute")
    public DocumentationResponses.House distribute(
            @PathVariable UUID id,
            @Valid @RequestBody DocumentationRequests.Distribute request,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return documentationService.distribute(id, request, actor);
    }

    /** 17. ReleaseOriginalBOLs */
    @PostMapping("/house-bols/{id}/originals/release")
    public DocumentationResponses.House releaseOriginals(
            @PathVariable UUID id,
            @Valid @RequestBody DocumentationRequests.ReleaseOriginals request) {
        return documentationService.releaseOriginals(id, request);
    }

    /** 18. RecordOriginalBOLsSurrendered */
    @PostMapping("/house-bols/{id}/originals/surrender")
    public DocumentationResponses.House surrenderOriginals(
            @PathVariable UUID id,
            @Valid @RequestBody DocumentationRequests.SurrenderOriginals request) {
        return documentationService.surrenderOriginals(id, request);
    }

    /** 19-20. RequestHouseBOLAmendment and AmendHouseBOL */
    @PostMapping("/house-bols/{id}/amend")
    public ResponseEntity<DocumentationResponses.House> amend(
            @PathVariable UUID id,
            @Valid @RequestBody DocumentationRequests.AmendHouseBOL request,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentationService.amendHouseBol(id, request, actor));
    }

    /** 21. VoidHouseBOL */
    @PostMapping("/house-bols/{id}/void")
    public DocumentationResponses.House voidBol(
            @PathVariable UUID id,
            @Valid @RequestBody DocumentationRequests.VoidHouseBOL request) {
        return documentationService.voidHouseBol(id, request);
    }
}
