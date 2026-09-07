package com.eazyfreight.finance.controller;

import com.eazyfreight.finance.api.FinanceApi;
import com.eazyfreight.finance.model.AssignResponsibility;
import com.eazyfreight.finance.model.CalculateStorageFee;
import com.eazyfreight.finance.model.CarrierInvoice;
import com.eazyfreight.finance.model.CarrierPaymentMade;
import com.eazyfreight.finance.model.CreditHoldRequest;
import com.eazyfreight.finance.model.CreditHoldView;
import com.eazyfreight.finance.model.CreditNote;
import com.eazyfreight.finance.model.InvoiceView;
import com.eazyfreight.finance.model.PayableView;
import com.eazyfreight.finance.model.PrepareInvoice;
import com.eazyfreight.finance.model.RecordPayment;
import com.eazyfreight.finance.model.StorageFeeView;
import com.eazyfreight.finance.model.UpdateLines;
import com.eazyfreight.finance.model.VoidInvoice;
import com.eazyfreight.finance.service.FinanceService;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Finance endpoints — the fifteen commands from the specification. */
@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
public class FinanceController implements FinanceApi {

    private final FinanceService financeService;

    // ------------------------------------------------------------------ reads

    @Override
    public ResponseEntity<List<InvoiceView>> invoices() {
        return ResponseEntity.ok(financeService.findAllInvoices().stream()
                .map(FinanceApiMapper::toView).toList());
    }

    @Override
    public ResponseEntity<List<InvoiceView>> overdueInvoices() {
        return ResponseEntity.ok(financeService.findOverdueInvoices().stream()
                .map(FinanceApiMapper::toView).toList());
    }

    @Override
    public ResponseEntity<InvoiceView> invoice(UUID id) {
        return ResponseEntity.ok(FinanceApiMapper.toView(financeService.findInvoice(id)));
    }

    @Override
    public ResponseEntity<List<InvoiceView>> invoicesForBooking(UUID bookingId) {
        return ResponseEntity.ok(financeService.findInvoicesForBooking(bookingId).stream()
                .map(FinanceApiMapper::toView).toList());
    }

    @Override
    public ResponseEntity<List<PayableView>> payables() {
        return ResponseEntity.ok(financeService.findAllPayables().stream()
                .map(FinanceApiMapper::toView).toList());
    }

    @Override
    public ResponseEntity<List<PayableView>> payablesForBooking(UUID bookingId) {
        return ResponseEntity.ok(financeService.findPayablesForBooking(bookingId).stream()
                .map(FinanceApiMapper::toView).toList());
    }

    @Override
    public ResponseEntity<List<StorageFeeView>> storageFees() {
        return ResponseEntity.ok(financeService.findStorageFees().stream()
                .map(FinanceApiMapper::toView).toList());
    }

    @Override
    public ResponseEntity<List<CreditHoldView>> creditHolds() {
        return ResponseEntity.ok(financeService.findActiveCreditHolds().stream()
                .map(FinanceApiMapper::toView).toList());
    }

    // ---------------------------------------------------------------- invoice

    /** 1. PrepareInvoice */
    @Override
    public ResponseEntity<InvoiceView> prepare(UUID bookingId, PrepareInvoice prepareInvoice) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(FinanceApiMapper.toView(financeService.prepareInvoice(
                        bookingId, FinanceApiMapper.toDomain(prepareInvoice))));
    }

    @Override
    public ResponseEntity<InvoiceView> updateLines(UUID id, UpdateLines updateLines) {
        return ResponseEntity.ok(FinanceApiMapper.toView(
                financeService.updateLines(id, FinanceApiMapper.toDomain(updateLines), false)));
    }

    /** 2. UpdateInvoiceWithActuals */
    @Override
    public ResponseEntity<InvoiceView> applyActuals(UUID id, UpdateLines updateLines) {
        return ResponseEntity.ok(FinanceApiMapper.toView(
                financeService.updateLines(id, FinanceApiMapper.toDomain(updateLines), true)));
    }

    /** 3. IssueInvoiceToCustomer — refused until the House BOL exists. */
    @Override
    public ResponseEntity<InvoiceView> issue(UUID id, String xActor) {
        return ResponseEntity.ok(FinanceApiMapper.toView(financeService.issue(id, xActor)));
    }

    /** 4. SendInvoicePDF — renders the invoice as a download. Pure read, nothing recorded. */
    @Override
    public ResponseEntity<Resource> invoicePdf(UUID id) {
        return financeService.pdf(id).asAttachment();
    }

    @Override
    public ResponseEntity<InvoiceView> sendPdf(UUID id) {
        return ResponseEntity.ok(FinanceApiMapper.toView(financeService.sendPdf(id)));
    }

    /** 5. RecordCustomerPayment — also creates the carrier payable it funds. */
    @Override
    public ResponseEntity<InvoiceView> recordPayment(UUID id, RecordPayment recordPayment, String xActor) {
        return ResponseEntity.ok(FinanceApiMapper.toView(
                financeService.recordPayment(id, FinanceApiMapper.toDomain(recordPayment), xActor)));
    }

    /** 9. VoidInvoice */
    @Override
    public ResponseEntity<InvoiceView> voidInvoice(UUID id, VoidInvoice voidInvoice, String xActor) {
        return ResponseEntity.ok(FinanceApiMapper.toView(
                financeService.voidInvoice(id, FinanceApiMapper.toDomain(voidInvoice), xActor)));
    }

    /** 10. IssueCreditNote */
    @Override
    public ResponseEntity<InvoiceView> creditNote(UUID id, CreditNote creditNote, String xActor) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(FinanceApiMapper.toView(
                        financeService.issueCreditNote(id, FinanceApiMapper.toDomain(creditNote), xActor)));
    }

    // --------------------------------------------------------------- payables

    /** 6. RecordCarrierInvoiceReceived */
    @Override
    public ResponseEntity<PayableView> carrierInvoice(UUID id, CarrierInvoice carrierInvoice) {
        return ResponseEntity.ok(FinanceApiMapper.toView(
                financeService.recordCarrierInvoice(id, FinanceApiMapper.toDomain(carrierInvoice))));
    }

    /** 7. ApproveCarrierPayment */
    @Override
    public ResponseEntity<PayableView> approve(UUID id, String xActor) {
        return ResponseEntity.ok(FinanceApiMapper.toView(financeService.approvePayable(id, xActor)));
    }

    /** 8. RecordCarrierPaymentMade */
    @Override
    public ResponseEntity<PayableView> paid(UUID id, CarrierPaymentMade carrierPaymentMade, String xActor) {
        return ResponseEntity.ok(FinanceApiMapper.toView(
                financeService.recordCarrierPaid(id, FinanceApiMapper.toDomain(carrierPaymentMade), xActor)));
    }

    // ----------------------------------------------------------- storage fees

    /** 11. CalculateStorageFee */
    @Override
    public ResponseEntity<StorageFeeView> calculateStorageFee(UUID bookingId, CalculateStorageFee calculateStorageFee) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(FinanceApiMapper.toView(financeService.calculateStorageFee(
                        bookingId, FinanceApiMapper.toDomain(calculateStorageFee))));
    }

    @Override
    public ResponseEntity<StorageFeeView> assignResponsibility(UUID id, AssignResponsibility assignResponsibility) {
        return ResponseEntity.ok(FinanceApiMapper.toView(
                financeService.assignResponsibility(id, FinanceApiMapper.toDomain(assignResponsibility))));
    }

    /** 12. IssueStorageFeeInvoice */
    @Override
    public ResponseEntity<InvoiceView> storageFeeInvoice(UUID id, String xActor) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(FinanceApiMapper.toView(financeService.issueStorageFeeInvoice(id, xActor)));
    }

    // ----------------------------------------------------------- credit hold

    /** 14. PlaceCreditHold */
    @Override
    public ResponseEntity<CreditHoldView> placeCreditHold(UUID bookingId, CreditHoldRequest creditHoldRequest, String xActor) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(FinanceApiMapper.toView(financeService.placeCreditHold(
                        bookingId, FinanceApiMapper.toDomain(creditHoldRequest), xActor)));
    }

    /** 15. LiftCreditHold */
    @Override
    public ResponseEntity<CreditHoldView> liftCreditHold(UUID customerId, String xActor) {
        return ResponseEntity.ok(FinanceApiMapper.toView(financeService.liftCreditHold(customerId, xActor)));
    }
}
