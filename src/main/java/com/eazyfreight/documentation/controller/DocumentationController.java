package com.eazyfreight.documentation.controller;

import com.eazyfreight.documentation.api.DocumentationApi;
import com.eazyfreight.documentation.model.AmendHouseBOL;
import com.eazyfreight.documentation.model.CarrierQuery;
import com.eazyfreight.documentation.model.CompileInstructions;
import com.eazyfreight.documentation.model.Discrepancy;
import com.eazyfreight.documentation.model.Distribute;
import com.eazyfreight.documentation.model.GenerateHouseBOL;
import com.eazyfreight.documentation.model.House;
import com.eazyfreight.documentation.model.Instructions;
import com.eazyfreight.documentation.model.Master;
import com.eazyfreight.documentation.model.MasterBOLCorrection;
import com.eazyfreight.documentation.model.MasterBOLReceived;
import com.eazyfreight.documentation.model.Preconditions;
import com.eazyfreight.documentation.model.ReleaseOriginals;
import com.eazyfreight.documentation.model.SurrenderOriginals;
import com.eazyfreight.documentation.model.VoidHouseBOL;
import com.eazyfreight.documentation.service.DocumentationService;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Documentation endpoints — the twenty-one commands from the specification. */
@RestController
@RequestMapping("/api/documentation")
@RequiredArgsConstructor
public class DocumentationController implements DocumentationApi {

    private final DocumentationService documentationService;

    // ------------------------------------------------------------ preconditions

    /** 1. CheckDocumentationPreconditions */
    @Override
    public ResponseEntity<Preconditions> preconditions(UUID bookingId) {
        return ResponseEntity.ok(DocumentationApiMapper.toApi(documentationService.checkPreconditions(bookingId)));
    }

    @Override
    public ResponseEntity<List<Instructions>> instructionsForBooking(UUID bookingId) {
        return ResponseEntity.ok(documentationService.instructionsForBooking(bookingId).stream()
                .map(DocumentationApiMapper::toApi).toList());
    }

    @Override
    public ResponseEntity<List<Master>> masterBolsForBooking(UUID bookingId) {
        return ResponseEntity.ok(documentationService.masterBolsForBooking(bookingId).stream()
                .map(DocumentationApiMapper::toApi).toList());
    }

    @Override
    public ResponseEntity<List<House>> houseBolsForBooking(UUID bookingId) {
        return ResponseEntity.ok(documentationService.houseBolsForBooking(bookingId).stream()
                .map(DocumentationApiMapper::toApi).toList());
    }

    @Override
    public ResponseEntity<List<House>> activeHouseBols() {
        return ResponseEntity.ok(documentationService.activeHouseBols().stream()
                .map(DocumentationApiMapper::toApi).toList());
    }

    @Override
    public ResponseEntity<House> houseBol(UUID id) {
        return ResponseEntity.ok(DocumentationApiMapper.toApi(documentationService.houseBol(id)));
    }

    /** Every revision of a BOL number, oldest first. */
    @Override
    public ResponseEntity<List<House>> revisions(String houseBolNumber) {
        return ResponseEntity.ok(documentationService.revisions(houseBolNumber).stream()
                .map(DocumentationApiMapper::toApi).toList());
    }

    // ------------------------------------------------------------- instructions

    /** 2. CompileMasterBOLInstructions */
    @Override
    public ResponseEntity<Instructions> compile(UUID bookingId, CompileInstructions compileInstructions, String xActor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(DocumentationApiMapper.toApi(
                documentationService.compileInstructions(bookingId, DocumentationApiMapper.toDomain(compileInstructions), xActor)));
    }

    /** 3. ApproveMasterBOLInstructions */
    @Override
    public ResponseEntity<Instructions> approve(UUID id, String xActor) {
        return ResponseEntity.ok(DocumentationApiMapper.toApi(documentationService.approveInstructions(id, xActor)));
    }

    /** 4. SendMasterBOLInstructionsToCarrier */
    @Override
    public ResponseEntity<Instructions> send(UUID id) {
        return ResponseEntity.ok(DocumentationApiMapper.toApi(documentationService.sendInstructions(id)));
    }

    /** 5. RecordCarrierQuery */
    @Override
    public ResponseEntity<Instructions> carrierQuery(UUID id, CarrierQuery carrierQuery) {
        return ResponseEntity.ok(DocumentationApiMapper.toApi(
                documentationService.recordCarrierQuery(id, DocumentationApiMapper.toDomain(carrierQuery))));
    }

    /** 6. ResendCorrectedInstructions */
    @Override
    public ResponseEntity<Instructions> resendCorrected(UUID id, CompileInstructions compileInstructions, String xActor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(DocumentationApiMapper.toApi(
                documentationService.resendCorrectedInstructions(id, DocumentationApiMapper.toDomain(compileInstructions), xActor)));
    }

    // ---------------------------------------------------------------- Master BOL

    /** 7. RecordMasterBOLReceived */
    @Override
    public ResponseEntity<Master> masterBolReceived(UUID id, MasterBOLReceived masterBOLReceived, String xActor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(DocumentationApiMapper.toApi(
                documentationService.recordMasterBolReceived(id, DocumentationApiMapper.toDomain(masterBOLReceived), xActor)));
    }

    /** 8. VerifyMasterBOL */
    @Override
    public ResponseEntity<Master> verify(UUID id, String xActor) {
        return ResponseEntity.ok(DocumentationApiMapper.toApi(documentationService.verifyMasterBol(id, xActor)));
    }

    /** 9. RaiseMasterBOLDiscrepancy */
    @Override
    public ResponseEntity<Master> discrepancy(UUID id, Discrepancy discrepancy) {
        return ResponseEntity.ok(DocumentationApiMapper.toApi(
                documentationService.raiseDiscrepancy(id, DocumentationApiMapper.toDomain(discrepancy))));
    }

    /** 10. RecordMasterBOLCorrection */
    @Override
    public ResponseEntity<Master> correction(UUID id, MasterBOLCorrection masterBOLCorrection) {
        return ResponseEntity.ok(DocumentationApiMapper.toApi(
                documentationService.recordMasterBolCorrection(id, DocumentationApiMapper.toDomain(masterBOLCorrection))));
    }

    // ----------------------------------------------------------------- House BOL

    /** 11-12. ConfirmReleaseType and GenerateHouseBOL */
    @Override
    public ResponseEntity<House> generateHouseBol(UUID id, GenerateHouseBOL generateHouseBOL, String xActor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(DocumentationApiMapper.toApi(
                documentationService.generateHouseBol(id, DocumentationApiMapper.toDomain(generateHouseBOL), xActor)));
    }

    /**
     * 13. GenerateHouseBOLPDF — renders the document and returns it as a download.
     *
     * <p>The command and the file are one round trip. Splitting them into "generate"
     * and "then fetch" would leave the caller holding a reference to a document the
     * server did not keep.
     */
    @Override
    public ResponseEntity<Resource> generatePdf(UUID id) {
        return documentationService.generateHouseBolPdf(id).asAttachment();
    }

    /** Re-download of a document already produced. Renders again; records nothing. */
    @Override
    public ResponseEntity<Resource> downloadPdf(UUID id) {
        return documentationService.houseBolPdf(id).asAttachment();
    }

    /** 14-16. SendHouseBOLToShipper / Consignee / NotifyParty */
    @Override
    public ResponseEntity<House> distribute(UUID id, Distribute distribute, String xActor) {
        return ResponseEntity.ok(DocumentationApiMapper.toApi(
                documentationService.distribute(id, DocumentationApiMapper.toDomain(distribute), xActor)));
    }

    /** 17. ReleaseOriginalBOLs */
    @Override
    public ResponseEntity<House> releaseOriginals(UUID id, ReleaseOriginals releaseOriginals) {
        return ResponseEntity.ok(DocumentationApiMapper.toApi(
                documentationService.releaseOriginals(id, DocumentationApiMapper.toDomain(releaseOriginals))));
    }

    /** 18. RecordOriginalBOLsSurrendered */
    @Override
    public ResponseEntity<House> surrenderOriginals(UUID id, SurrenderOriginals surrenderOriginals) {
        return ResponseEntity.ok(DocumentationApiMapper.toApi(
                documentationService.surrenderOriginals(id, DocumentationApiMapper.toDomain(surrenderOriginals))));
    }

    /** 19-20. RequestHouseBOLAmendment and AmendHouseBOL */
    @Override
    public ResponseEntity<House> amend(UUID id, AmendHouseBOL amendHouseBOL, String xActor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(DocumentationApiMapper.toApi(
                documentationService.amendHouseBol(id, DocumentationApiMapper.toDomain(amendHouseBOL), xActor)));
    }

    /** 21. VoidHouseBOL */
    @Override
    public ResponseEntity<House> voidBol(UUID id, VoidHouseBOL voidHouseBOL) {
        return ResponseEntity.ok(DocumentationApiMapper.toApi(
                documentationService.voidHouseBol(id, DocumentationApiMapper.toDomain(voidHouseBOL))));
    }
}
