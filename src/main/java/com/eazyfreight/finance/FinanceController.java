package com.eazyfreight.finance;

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
import java.util.UUID;

/** Finance endpoints — the fifteen commands from the specification. */
@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
public class FinanceController {

    private static final String ACTOR_HEADER = "X-Actor";
    private static final String DEFAULT_ACTOR = "accounting";

    private final FinanceService financeService;

    // ------------------------------------------------------------------ reads

    @GetMapping("/invoices")
    public List<FinanceResponses.InvoiceView> invoices() {
        return financeService.findAllInvoices();
    }

    @GetMapping("/invoices/overdue")
    public List<FinanceResponses.InvoiceView> overdueInvoices() {
        return financeService.findOverdueInvoices();
    }

    @GetMapping("/invoices/{id}")
    public FinanceResponses.InvoiceView invoice(@PathVariable UUID id) {
        return financeService.findInvoice(id);
    }

    @GetMapping("/bookings/{bookingId}/invoices")
    public List<FinanceResponses.InvoiceView> invoicesForBooking(@PathVariable UUID bookingId) {
        return financeService.findInvoicesForBooking(bookingId);
    }

    @GetMapping("/payables")
    public List<FinanceResponses.PayableView> payables() {
        return financeService.findAllPayables();
    }

    @GetMapping("/bookings/{bookingId}/payables")
    public List<FinanceResponses.PayableView> payablesForBooking(@PathVariable UUID bookingId) {
        return financeService.findPayablesForBooking(bookingId);
    }

    @GetMapping("/storage-fees")
    public List<FinanceResponses.StorageFeeView> storageFees() {
        return financeService.findStorageFees();
    }

    @GetMapping("/credit-holds")
    public List<FinanceResponses.CreditHoldView> creditHolds() {
        return financeService.findActiveCreditHolds();
    }

    // ---------------------------------------------------------------- invoice

    /** 1. PrepareInvoice */
    @PostMapping("/bookings/{bookingId}/invoice")
    public ResponseEntity<FinanceResponses.InvoiceView> prepare(
            @PathVariable UUID bookingId,
            @RequestBody(required = false) FinanceRequests.PrepareInvoice request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(financeService.prepareInvoice(bookingId, request));
    }

    @PostMapping("/invoices/{id}/lines")
    public FinanceResponses.InvoiceView updateLines(
            @PathVariable UUID id, @Valid @RequestBody FinanceRequests.UpdateLines request) {
        return financeService.updateLines(id, request, false);
    }

    /** 2. UpdateInvoiceWithActuals */
    @PostMapping("/invoices/{id}/actuals")
    public FinanceResponses.InvoiceView applyActuals(
            @PathVariable UUID id, @Valid @RequestBody FinanceRequests.UpdateLines request) {
        return financeService.updateLines(id, request, true);
    }

    /** 3. IssueInvoiceToCustomer — refused until the House BOL exists. */
    @PostMapping("/invoices/{id}/issue")
    public FinanceResponses.InvoiceView issue(
            @PathVariable UUID id,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return financeService.issue(id, actor);
    }

    /** 4. SendInvoicePDF */
    @PostMapping("/invoices/{id}/send")
    public FinanceResponses.InvoiceView sendPdf(@PathVariable UUID id) {
        return financeService.sendPdf(id);
    }

    /** 5. RecordCustomerPayment — also creates the carrier payable it funds. */
    @PostMapping("/invoices/{id}/payments")
    public FinanceResponses.InvoiceView recordPayment(
            @PathVariable UUID id,
            @Valid @RequestBody FinanceRequests.RecordPayment request,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return financeService.recordPayment(id, request, actor);
    }

    /** 9. VoidInvoice */
    @PostMapping("/invoices/{id}/void")
    public FinanceResponses.InvoiceView voidInvoice(
            @PathVariable UUID id,
            @Valid @RequestBody FinanceRequests.VoidInvoice request,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return financeService.voidInvoice(id, request, actor);
    }

    /** 10. IssueCreditNote */
    @PostMapping("/invoices/{id}/credit-note")
    public ResponseEntity<FinanceResponses.InvoiceView> creditNote(
            @PathVariable UUID id,
            @Valid @RequestBody FinanceRequests.CreditNote request,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(financeService.issueCreditNote(id, request, actor));
    }

    // --------------------------------------------------------------- payables

    /** 6. RecordCarrierInvoiceReceived */
    @PostMapping("/payables/{id}/carrier-invoice")
    public FinanceResponses.PayableView carrierInvoice(
            @PathVariable UUID id, @Valid @RequestBody FinanceRequests.CarrierInvoice request) {
        return financeService.recordCarrierInvoice(id, request);
    }

    /** 7. ApproveCarrierPayment */
    @PostMapping("/payables/{id}/approve")
    public FinanceResponses.PayableView approve(
            @PathVariable UUID id,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return financeService.approvePayable(id, actor);
    }

    /** 8. RecordCarrierPaymentMade */
    @PostMapping("/payables/{id}/paid")
    public FinanceResponses.PayableView paid(
            @PathVariable UUID id,
            @Valid @RequestBody FinanceRequests.CarrierPaymentMade request,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return financeService.recordCarrierPaid(id, request, actor);
    }

    // ----------------------------------------------------------- storage fees

    /** 11. CalculateStorageFee */
    @PostMapping("/bookings/{bookingId}/storage-fee")
    public ResponseEntity<FinanceResponses.StorageFeeView> calculateStorageFee(
            @PathVariable UUID bookingId,
            @Valid @RequestBody FinanceRequests.CalculateStorageFee request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(financeService.calculateStorageFee(bookingId, request));
    }

    @PostMapping("/storage-fees/{id}/responsibility")
    public FinanceResponses.StorageFeeView assignResponsibility(
            @PathVariable UUID id,
            @Valid @RequestBody FinanceRequests.AssignResponsibility request) {
        return financeService.assignResponsibility(id, request);
    }

    /** 12. IssueStorageFeeInvoice */
    @PostMapping("/storage-fees/{id}/invoice")
    public ResponseEntity<FinanceResponses.InvoiceView> storageFeeInvoice(
            @PathVariable UUID id,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(financeService.issueStorageFeeInvoice(id, actor));
    }

    // ----------------------------------------------------------- credit hold

    /** 14. PlaceCreditHold */
    @PostMapping("/bookings/{bookingId}/credit-hold")
    public ResponseEntity<FinanceResponses.CreditHoldView> placeCreditHold(
            @PathVariable UUID bookingId,
            @Valid @RequestBody FinanceRequests.CreditHoldRequest request,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(financeService.placeCreditHold(bookingId, request, actor));
    }

    /** 15. LiftCreditHold */
    @PostMapping("/credit-holds/{customerId}/lift")
    public FinanceResponses.CreditHoldView liftCreditHold(
            @PathVariable UUID customerId,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return financeService.liftCreditHold(customerId, actor);
    }
}
