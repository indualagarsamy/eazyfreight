package com.eazyfreight.compliance.controller;

import com.eazyfreight.compliance.api.ComplianceApi;
import com.eazyfreight.compliance.client.AesFilingClient;
import com.eazyfreight.compliance.model.AmendFilingRequest;
import com.eazyfreight.compliance.model.CancelFilingRequest;
import com.eazyfreight.compliance.model.CompileEEIDataRequest;
import com.eazyfreight.compliance.model.EEIFilingResponse;
import com.eazyfreight.compliance.model.FilingSystemStatus;
import com.eazyfreight.compliance.model.RecordAcceptanceRequest;
import com.eazyfreight.compliance.model.RecordExportLicenseRequest;
import com.eazyfreight.compliance.model.RecordRejectionRequest;
import com.eazyfreight.compliance.service.ComplianceService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Export compliance endpoints — the eleven commands, each a named sub-resource.
 *
 * <p>{@code GET /api/compliance/filing-system} reports whether filings are
 * simulated. The UI reads it to show a standing banner, so no one can look at this
 * screen and believe a report has reached CBP.
 */
@RestController
@RequestMapping("/api/compliance")
@RequiredArgsConstructor
public class ComplianceController implements ComplianceApi {

    private final ComplianceService complianceService;
    private final AesFilingClient aesClient;

    @Override
    public ResponseEntity<FilingSystemStatus> filingSystem() {
        FilingSystemStatus status = new FilingSystemStatus();
        status.setSimulated(aesClient.isSimulated());
        status.setNotice(aesClient.isSimulated()
                ? "Filings are simulated. No Electronic Export Information is transmitted "
                  + "to CBP and every ITN shown here is fabricated."
                : "Filings are transmitted to CBP.");
        return ResponseEntity.ok(status);
    }

    @Override
    public ResponseEntity<List<EEIFilingResponse>> getAllFilings() {
        return ResponseEntity.ok(complianceService.findAll().stream()
                .map(ComplianceApiMapper::toModel).toList());
    }

    @Override
    public ResponseEntity<List<EEIFilingResponse>> getAwaitingCbpFilings() {
        return ResponseEntity.ok(complianceService.findAwaitingCbp().stream()
                .map(ComplianceApiMapper::toModel).toList());
    }

    @Override
    public ResponseEntity<EEIFilingResponse> getFilingById(UUID id) {
        return ResponseEntity.ok(ComplianceApiMapper.toModel(complianceService.findById(id)));
    }

    @Override
    public ResponseEntity<EEIFilingResponse> getFilingByReference(String filingReference) {
        return ResponseEntity.ok(ComplianceApiMapper.toModel(complianceService.findByReference(filingReference)));
    }

    @Override
    public ResponseEntity<List<EEIFilingResponse>> getFilingsByBooking(UUID bookingId) {
        return ResponseEntity.ok(complianceService.findByBooking(bookingId).stream()
                .map(ComplianceApiMapper::toModel).toList());
    }

    /** 1. InitiateEEIFiling */
    @Override
    public ResponseEntity<EEIFilingResponse> initiateFiling(UUID bookingId, String xActor) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ComplianceApiMapper.toModel(complianceService.initiateFiling(bookingId, xActor)));
    }

    /** 2. CompileEEIData */
    @Override
    public ResponseEntity<EEIFilingResponse> compileFiling(UUID id, CompileEEIDataRequest compileEEIDataRequest, String xActor) {
        return ResponseEntity.ok(ComplianceApiMapper.toModel(complianceService.compile(
                id, ComplianceApiMapper.toDto(compileEEIDataRequest), xActor)));
    }

    /** 3. SubmitEEIToCBP — and 8. SubmitEEIAmendment, which is the same transition. */
    @Override
    public ResponseEntity<EEIFilingResponse> submitFiling(UUID id, String xActor) {
        return ResponseEntity.ok(ComplianceApiMapper.toModel(complianceService.submitToCbp(id, xActor)));
    }

    /** 4. RecordCBPAcceptance — and 9. RecordAmendmentAcceptance. */
    @Override
    public ResponseEntity<EEIFilingResponse> recordAcceptance(UUID id, RecordAcceptanceRequest recordAcceptanceRequest, String xActor) {
        return ResponseEntity.ok(ComplianceApiMapper.toModel(complianceService.recordAcceptance(
                id, ComplianceApiMapper.toDto(recordAcceptanceRequest), xActor)));
    }

    /** 5. RecordCBPRejection */
    @Override
    public ResponseEntity<EEIFilingResponse> recordRejection(UUID id, RecordRejectionRequest recordRejectionRequest, String xActor) {
        return ResponseEntity.ok(ComplianceApiMapper.toModel(complianceService.recordRejection(
                id, ComplianceApiMapper.toDto(recordRejectionRequest), xActor)));
    }

    /** 6. SubmitCorrectedEEI */
    @Override
    public ResponseEntity<EEIFilingResponse> correctFiling(UUID id, String xActor) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ComplianceApiMapper.toModel(complianceService.submitCorrection(id, xActor)));
    }

    /** 7. InitiateEEIAmendment */
    @Override
    public ResponseEntity<EEIFilingResponse> amendFiling(UUID id, AmendFilingRequest amendFilingRequest, String xActor) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ComplianceApiMapper.toModel(complianceService.initiateAmendment(
                        id, ComplianceApiMapper.toDto(amendFilingRequest), xActor)));
    }

    /** 10. CancelEEIFiling */
    @Override
    public ResponseEntity<EEIFilingResponse> cancelFiling(UUID id, CancelFilingRequest cancelFilingRequest, String xActor) {
        return ResponseEntity.ok(ComplianceApiMapper.toModel(complianceService.cancel(
                id, ComplianceApiMapper.toDto(cancelFilingRequest), xActor)));
    }

    /** 11. RecordExportLicense */
    @Override
    public ResponseEntity<EEIFilingResponse> recordExportLicense(UUID id, RecordExportLicenseRequest recordExportLicenseRequest, String xActor) {
        return ResponseEntity.ok(ComplianceApiMapper.toModel(complianceService.recordExportLicense(
                id, ComplianceApiMapper.toDto(recordExportLicenseRequest), xActor)));
    }
}
