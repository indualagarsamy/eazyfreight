package com.eazyfreight.compliance.controller;

import com.eazyfreight.compliance.client.AesFilingClient;
import com.eazyfreight.compliance.dto.AmendFilingRequest;
import com.eazyfreight.compliance.dto.CancelFilingRequest;
import com.eazyfreight.compliance.dto.CompileEEIDataRequest;
import com.eazyfreight.compliance.dto.EEIFilingResponse;
import com.eazyfreight.compliance.dto.RecordAcceptanceRequest;
import com.eazyfreight.compliance.dto.RecordExportLicenseRequest;
import com.eazyfreight.compliance.dto.RecordRejectionRequest;
import com.eazyfreight.compliance.service.ComplianceService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
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
public class ComplianceController {

    private static final String ACTOR_HEADER = "X-Actor";
    private static final String DEFAULT_ACTOR = "compliance";

    private final ComplianceService complianceService;
    private final AesFilingClient aesClient;

    @GetMapping("/filing-system")
    public Map<String, Object> filingSystem() {
        return Map.of(
                "simulated", aesClient.isSimulated(),
                "notice", aesClient.isSimulated()
                        ? "Filings are simulated. No Electronic Export Information is transmitted "
                          + "to CBP and every ITN shown here is fabricated."
                        : "Filings are transmitted to CBP.");
    }

    @GetMapping("/filings")
    public List<EEIFilingResponse> getAll() {
        return complianceService.findAll();
    }

    @GetMapping("/filings/awaiting-cbp")
    public List<EEIFilingResponse> getAwaitingCbp() {
        return complianceService.findAwaitingCbp();
    }

    @GetMapping("/filings/{id}")
    public EEIFilingResponse getById(@PathVariable UUID id) {
        return complianceService.findById(id);
    }

    @GetMapping("/filings/by-reference/{filingReference}")
    public EEIFilingResponse getByReference(@PathVariable String filingReference) {
        return complianceService.findByReference(filingReference);
    }

    @GetMapping("/bookings/{bookingId}/filings")
    public List<EEIFilingResponse> getByBooking(@PathVariable UUID bookingId) {
        return complianceService.findByBooking(bookingId);
    }

    /** 1. InitiateEEIFiling */
    @PostMapping("/bookings/{bookingId}/filings")
    public ResponseEntity<EEIFilingResponse> initiate(
            @PathVariable UUID bookingId,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(complianceService.initiateFiling(bookingId, actor));
    }

    /** 2. CompileEEIData */
    @PostMapping("/filings/{id}/compile")
    public EEIFilingResponse compile(
            @PathVariable UUID id,
            @Valid @RequestBody CompileEEIDataRequest request,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return complianceService.compile(id, request, actor);
    }

    /** 3. SubmitEEIToCBP — and 8. SubmitEEIAmendment, which is the same transition. */
    @PostMapping("/filings/{id}/submit")
    public EEIFilingResponse submit(
            @PathVariable UUID id,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return complianceService.submitToCbp(id, actor);
    }

    /** 4. RecordCBPAcceptance — and 9. RecordAmendmentAcceptance. */
    @PostMapping("/filings/{id}/acceptance")
    public EEIFilingResponse recordAcceptance(
            @PathVariable UUID id,
            @Valid @RequestBody RecordAcceptanceRequest request,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return complianceService.recordAcceptance(id, request, actor);
    }

    /** 5. RecordCBPRejection */
    @PostMapping("/filings/{id}/rejection")
    public EEIFilingResponse recordRejection(
            @PathVariable UUID id,
            @Valid @RequestBody RecordRejectionRequest request,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return complianceService.recordRejection(id, request, actor);
    }

    /** 6. SubmitCorrectedEEI */
    @PostMapping("/filings/{id}/correct")
    public ResponseEntity<EEIFilingResponse> correct(
            @PathVariable UUID id,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(complianceService.submitCorrection(id, actor));
    }

    /** 7. InitiateEEIAmendment */
    @PostMapping("/filings/{id}/amend")
    public ResponseEntity<EEIFilingResponse> amend(
            @PathVariable UUID id,
            @Valid @RequestBody AmendFilingRequest request,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(complianceService.initiateAmendment(id, request, actor));
    }

    /** 10. CancelEEIFiling */
    @PostMapping("/filings/{id}/cancel")
    public EEIFilingResponse cancel(
            @PathVariable UUID id,
            @Valid @RequestBody CancelFilingRequest request,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return complianceService.cancel(id, request, actor);
    }

    /** 11. RecordExportLicense */
    @PostMapping("/filings/{id}/export-license")
    public EEIFilingResponse recordExportLicense(
            @PathVariable UUID id,
            @Valid @RequestBody RecordExportLicenseRequest request,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return complianceService.recordExportLicense(id, request, actor);
    }
}
