package com.eazyfreight.finance.service;

import com.eazyfreight.finance.domain.FinanceEnums.InvoiceStatus;
import com.eazyfreight.finance.domain.FinanceEnums.InvoiceType;

import com.eazyfreight.booking.domain.Booking;
import com.eazyfreight.booking.repository.BookingRepository;
import com.eazyfreight.finance.domain.CarrierPayable;
import com.eazyfreight.finance.domain.CreditHold;
import com.eazyfreight.finance.domain.CustomerPayment;
import com.eazyfreight.finance.domain.Invoice;
import com.eazyfreight.finance.domain.StorageFee;
import com.eazyfreight.finance.dto.FinanceRequests;
import com.eazyfreight.finance.dto.FinanceResponses;
import com.eazyfreight.finance.exception.FinanceNotFoundException;
import com.eazyfreight.finance.repository.CarrierPayableRepository;
import com.eazyfreight.finance.repository.CreditHoldRepository;
import com.eazyfreight.finance.repository.InvoiceRepository;
import com.eazyfreight.finance.repository.StorageFeeRepository;

import com.eazyfreight.common.ReferenceGenerator;
import com.eazyfreight.common.pdf.RenderedDocument;
import com.eazyfreight.exception.BookingNotFoundException;
import com.eazyfreight.exception.DomainRuleViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for the Finance context — the fifteen commands from the
 * specification.
 *
 * <p>The causal payment chain is enforced in one place: {@link #recordPayment}
 * creates the carrier payable from the customer payment, and nothing else can.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FinanceService {

    private final InvoiceRepository invoiceRepository;
    private final CarrierPayableRepository payableRepository;
    private final StorageFeeRepository storageFeeRepository;
    private final CreditHoldRepository creditHoldRepository;
    private final BookingRepository bookingRepository;
    private final InvoicePdfRenderer pdfRenderer;
    private final ReferenceGenerator referenceGenerator;
    private final Clock clock;

    // ----------------------------------------------------------- 1. prepare

    @Transactional
    public FinanceResponses.InvoiceView prepareInvoice(
            UUID bookingId, FinanceRequests.PrepareInvoice request) {
        Booking booking = booking(bookingId);
        if (invoiceRepository.findFirstByBookingIdAndInvoiceType(
                bookingId, InvoiceType.FREIGHT).isPresent()) {
            throw new DomainRuleViolationException(
                    "Booking " + booking.getBookingReference() + " already has a freight invoice");
        }

        Invoice invoice = Invoice.prepare(
                referenceGenerator.next(ReferenceGenerator.INVOICE_PREFIX),
                bookingId, booking.getCustomerId(),
                request == null ? null : request.paymentTermsType(),
                booking.getCarrierBooking() == null
                        ? null : booking.getCarrierBooking().getConfirmedEta(),
                request == null || request.currency() == null ? "USD" : request.currency(),
                clock.instant());
        return view(invoiceRepository.save(invoice));
    }

    // ------------------------------------------------- 2. actuals, 3. issue

    @Transactional
    public FinanceResponses.InvoiceView updateLines(
            UUID invoiceId, FinanceRequests.UpdateLines request, boolean actuals) {
        Invoice invoice = invoice(invoiceId);
        List<Invoice.LineDraft> drafts = request.lines().stream()
                .map(line -> new Invoice.LineDraft(line.description(), line.buyAmount(),
                        line.sellAmount(), line.quantity(), line.unit()))
                .toList();
        if (actuals) {
            invoice.applyActuals(drafts, clock.instant());
        } else {
            invoice.replaceLines(drafts);
        }
        return view(invoiceRepository.save(invoice));
    }

    @Transactional
    public FinanceResponses.InvoiceView issue(UUID invoiceId, String actor) {
        Invoice invoice = invoice(invoiceId);
        invoice.issue(LocalDate.now(clock), clock.instant(), actor);
        return view(invoiceRepository.save(invoice));
    }

    /**
     * Renders the invoice for download. Records nothing — reading a document is not a
     * business event, and an invoice's lines are frozen once it is issued, so the same
     * record renders the same file every time.
     */
    @Transactional(readOnly = true)
    public RenderedDocument pdf(UUID invoiceId) {
        Invoice invoice = invoice(invoiceId);
        return new RenderedDocument(pdfRenderer.fileName(invoice), pdfRenderer.render(invoice));
    }

    /** 4. SendInvoicePDF */
    @Transactional
    public FinanceResponses.InvoiceView sendPdf(UUID invoiceId) {
        Invoice invoice = invoice(invoiceId);
        if (!invoice.getStatus().isIssued()) {
            throw new DomainRuleViolationException("Issue the invoice before sending it");
        }
        invoice.recordPdfSent(clock.instant());
        return view(invoiceRepository.save(invoice));
    }

    // -------------------------------------------- 5. payment, causal chain

    /**
     * Records the customer payment and creates the carrier payable it funds.
     *
     * <p>These are one transaction on purpose. The carrier is paid out of the
     * customer's money, so the payable comes into being with the payment and its
     * due date is two business days later, fixed.
     */
    @Transactional
    public FinanceResponses.InvoiceView recordPayment(
            UUID invoiceId, FinanceRequests.RecordPayment request, String actor) {
        Invoice invoice = invoice(invoiceId);
        CustomerPayment payment = invoice.recordPayment(
                request.amount(), request.paymentDate(), request.paymentMethod(),
                request.reference(), clock.instant(), actor);
        invoiceRepository.save(invoice);

        Booking booking = bookingRepository.findById(invoice.getBookingId()).orElse(null);
        CarrierPayable payable = CarrierPayable.fundedBy(
                payment.getId(), invoice.getId(), invoice.getBookingId(),
                booking == null || booking.getCarrierBooking() == null
                        ? null : booking.getCarrierBooking().getCarrierId(),
                // We remit what we bought the space for, not what we charged.
                carrierShare(invoice, request.amount()),
                // Override Invoice currency because we like Euro bills
                "EUR", request.paymentDate(), clock.instant());
        payableRepository.save(payable);

        log.info("Customer paid {} on invoice {} — carrier payable of {} now due {}",
                request.amount(), invoice.getInvoiceNumber(),
                payable.getAmount(), payable.getDueDate());
        return view(invoice);
    }

    // ---------------------------------------------- 6-8. carrier payables

    @Transactional
    public FinanceResponses.PayableView recordCarrierInvoice(
            UUID payableId, FinanceRequests.CarrierInvoice request) {
        CarrierPayable payable = payable(payableId);
        payable.recordCarrierInvoice(request.reference(), request.amount(), clock.instant());
        return FinanceResponses.PayableView.from(
                payableRepository.save(payable), LocalDate.now(clock));
    }

    @Transactional
    public FinanceResponses.PayableView approvePayable(UUID payableId, String actor) {
        CarrierPayable payable = payable(payableId);
        payable.approve(clock.instant(), actor);
        return FinanceResponses.PayableView.from(
                payableRepository.save(payable), LocalDate.now(clock));
    }

    @Transactional
    public FinanceResponses.PayableView recordCarrierPaid(
            UUID payableId, FinanceRequests.CarrierPaymentMade request, String actor) {
        CarrierPayable payable = payable(payableId);
        payable.recordPaid(request.paidOn(), request.reference(), clock.instant(), actor);
        return FinanceResponses.PayableView.from(
                payableRepository.save(payable), LocalDate.now(clock));
    }

    // ------------------------------------------- 9-10. void, credit note

    @Transactional
    public FinanceResponses.InvoiceView voidInvoice(
            UUID invoiceId, FinanceRequests.VoidInvoice request, String actor) {
        Invoice invoice = invoice(invoiceId);
        invoice.voidInvoice(request.reason(), clock.instant(), actor);
        return view(invoiceRepository.save(invoice));
    }

    @Transactional
    public FinanceResponses.InvoiceView issueCreditNote(
            UUID invoiceId, FinanceRequests.CreditNote request, String actor) {
        Invoice against = invoice(invoiceId);
        Invoice note = Invoice.creditNote(
                referenceGenerator.next(ReferenceGenerator.CREDIT_NOTE_PREFIX),
                against, request.amount(), request.reason(), clock.instant(), actor);
        return view(invoiceRepository.save(note));
    }

    // -------------------------------------------------- 11-13. storage fees

    @Transactional
    public FinanceResponses.StorageFeeView calculateStorageFee(
            UUID bookingId, FinanceRequests.CalculateStorageFee request) {
        if (storageFeeRepository.findByBookingId(bookingId).isPresent()) {
            throw new DomainRuleViolationException(
                    "A storage fee already exists for this booking");
        }
        StorageFee fee = StorageFee.calculate(bookingId, request.cause(), request.dailyRate(),
                request.days(), request.periodFrom(), request.periodTo(),
                request.currency() == null ? "USD" : request.currency(), clock.instant());
        return FinanceResponses.StorageFeeView.from(storageFeeRepository.save(fee));
    }

    @Transactional
    public FinanceResponses.StorageFeeView assignResponsibility(
            UUID storageFeeId, FinanceRequests.AssignResponsibility request) {
        StorageFee fee = storageFee(storageFeeId);
        fee.assignResponsibility(request.responsibility(), request.notes());
        return FinanceResponses.StorageFeeView.from(storageFeeRepository.save(fee));
    }

    /** 12. IssueStorageFeeInvoice — a separate document from the freight invoice. */
    @Transactional
    public FinanceResponses.InvoiceView issueStorageFeeInvoice(UUID storageFeeId, String actor) {
        StorageFee fee = storageFee(storageFeeId);
        String blocked = fee.invoiceBlockedReason();
        if (blocked != null) {
            throw new DomainRuleViolationException("Storage fee cannot be invoiced: " + blocked);
        }
        Booking booking = booking(fee.getBookingId());

        Invoice invoice = Invoice.forStorageFee(
                referenceGenerator.next(ReferenceGenerator.INVOICE_PREFIX),
                fee.getBookingId(), booking.getCustomerId(), fee.getCurrency(), clock.instant());
        invoice.replaceLines(List.of(new Invoice.LineDraft(
                "Terminal storage, %d days at %s per day".formatted(fee.getDays(), fee.getDailyRate()),
                BigDecimal.ZERO, fee.getDailyRate(),
                BigDecimal.valueOf(fee.getDays()), "DAY")));
        // A storage fee has no BOL and no cargo actuals to wait on.
        invoice.linkHouseBol(fee.getId());
        invoice.applyActuals(List.of(new Invoice.LineDraft(
                "Terminal storage, %d days at %s per day".formatted(fee.getDays(), fee.getDailyRate()),
                BigDecimal.ZERO, fee.getDailyRate(),
                BigDecimal.valueOf(fee.getDays()), "DAY")), clock.instant());
        invoice.issue(LocalDate.now(clock), clock.instant(), actor);
        invoiceRepository.save(invoice);

        fee.linkInvoice(invoice.getId());
        storageFeeRepository.save(fee);
        return view(invoice);
    }

    // ------------------------------------------------- 14-15. credit hold

    @Transactional
    public FinanceResponses.CreditHoldView placeCreditHold(
            UUID bookingId, FinanceRequests.CreditHoldRequest request, String actor) {
        creditHoldRepository.findByCustomerIdAndActiveTrue(request.customerId())
                .ifPresent(existing -> {
                    throw new DomainRuleViolationException(
                            "This customer is already on credit hold");
                });
        CreditHold hold = CreditHold.place(request.customerId(), bookingId,
                request.reason(), clock.instant(), actor);
        return FinanceResponses.CreditHoldView.from(creditHoldRepository.save(hold));
    }

    @Transactional
    public FinanceResponses.CreditHoldView liftCreditHold(UUID customerId, String actor) {
        CreditHold hold = creditHoldRepository.findByCustomerIdAndActiveTrue(customerId)
                .orElseThrow(() -> new FinanceNotFoundException(
                        "No active credit hold for customer " + customerId));
        hold.lift(clock.instant(), actor);
        return FinanceResponses.CreditHoldView.from(creditHoldRepository.save(hold));
    }

    // ------------------------------------------------ cross-context hooks

    /** The Documentation track issued the House BOL, which unblocks issuance. */
    @Transactional
    public void linkHouseBol(UUID bookingId, UUID houseBolId) {
        freightInvoice(bookingId).ifPresent(invoice -> {
            invoice.linkHouseBol(houseBolId);
            invoiceRepository.save(invoice);
        });
    }

    /** A cancelled booking voids an unpaid invoice rather than leaving it open. */
    @Transactional
    public void voidForCancelledBooking(UUID bookingId, String reason) {
        freightInvoice(bookingId).ifPresent(invoice -> {
            if (invoice.getStatus() == InvoiceStatus.PREPARED
                    || invoice.getStatus() == InvoiceStatus.ISSUED) {
                invoice.voidInvoice("Booking cancelled: " + reason, clock.instant(), "system");
                invoiceRepository.save(invoice);
            }
        });
    }

    // ----------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public List<FinanceResponses.InvoiceView> findAllInvoices() {
        LocalDate today = LocalDate.now(clock);
        return invoiceRepository.findAll().stream()
                .map(invoice -> FinanceResponses.InvoiceView.from(invoice, today)).toList();
    }

    @Transactional(readOnly = true)
    public FinanceResponses.InvoiceView findInvoice(UUID invoiceId) {
        return view(invoice(invoiceId));
    }

    @Transactional(readOnly = true)
    public List<FinanceResponses.InvoiceView> findInvoicesForBooking(UUID bookingId) {
        LocalDate today = LocalDate.now(clock);
        return invoiceRepository.findByBookingId(bookingId).stream()
                .map(invoice -> FinanceResponses.InvoiceView.from(invoice, today)).toList();
    }

    /** Issued, unsettled and past due. */
    @Transactional(readOnly = true)
    public List<FinanceResponses.InvoiceView> findOverdueInvoices() {
        LocalDate today = LocalDate.now(clock);
        return invoiceRepository.findByStatusInAndPaymentDueDateLessThan(
                        List.of(InvoiceStatus.ISSUED, InvoiceStatus.PARTIALLY_PAID), today).stream()
                .map(invoice -> FinanceResponses.InvoiceView.from(invoice, today)).toList();
    }

    @Transactional(readOnly = true)
    public List<FinanceResponses.PayableView> findAllPayables() {
        LocalDate today = LocalDate.now(clock);
        return payableRepository.findAll().stream()
                .map(payable -> FinanceResponses.PayableView.from(payable, today)).toList();
    }

    @Transactional(readOnly = true)
    public List<FinanceResponses.PayableView> findPayablesForBooking(UUID bookingId) {
        LocalDate today = LocalDate.now(clock);
        return payableRepository.findByBookingId(bookingId).stream()
                .map(payable -> FinanceResponses.PayableView.from(payable, today)).toList();
    }

    @Transactional(readOnly = true)
    public List<FinanceResponses.StorageFeeView> findStorageFees() {
        return storageFeeRepository.findAll().stream()
                .map(FinanceResponses.StorageFeeView::from).toList();
    }

    @Transactional(readOnly = true)
    public List<FinanceResponses.CreditHoldView> findActiveCreditHolds() {
        return creditHoldRepository.findByActiveTrue().stream()
                .map(FinanceResponses.CreditHoldView::from).toList();
    }

    // ----------------------------------------------------------------- helpers

    /**
     * What we owe the carrier out of this receipt: the buy side, in proportion to
     * how much of the invoice has just been paid.
     */
    private BigDecimal carrierShare(Invoice invoice, BigDecimal received) {
        if (invoice.getTotalAmount().signum() == 0) {
            return BigDecimal.ZERO;
        }
        return invoice.getTotalBuyAmount()
                .multiply(received)
                .divide(invoice.getTotalAmount(), 2, java.math.RoundingMode.HALF_UP);
    }

    private Optional<Invoice> freightInvoice(UUID bookingId) {
        return invoiceRepository.findFirstByBookingIdAndInvoiceType(bookingId, InvoiceType.FREIGHT);
    }

    private FinanceResponses.InvoiceView view(Invoice invoice) {
        return FinanceResponses.InvoiceView.from(invoice, LocalDate.now(clock));
    }

    private Booking booking(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
    }

    private Invoice invoice(UUID id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new FinanceNotFoundException("Invoice not found: " + id));
    }

    private CarrierPayable payable(UUID id) {
        return payableRepository.findById(id)
                .orElseThrow(() -> new FinanceNotFoundException("Carrier payable not found: " + id));
    }

    private StorageFee storageFee(UUID id) {
        return storageFeeRepository.findById(id)
                .orElseThrow(() -> new FinanceNotFoundException("Storage fee not found: " + id));
    }
}
