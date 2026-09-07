package com.eazyfreight.finance.controller;

import com.eazyfreight.finance.dto.FinanceRequests;
import com.eazyfreight.finance.dto.FinanceResponses;
import com.eazyfreight.finance.model.AssignResponsibility;
import com.eazyfreight.finance.model.CalculateStorageFee;
import com.eazyfreight.finance.model.CarrierInvoice;
import com.eazyfreight.finance.model.CarrierPaymentMade;
import com.eazyfreight.finance.model.CreditHoldRequest;
import com.eazyfreight.finance.model.CreditHoldView;
import com.eazyfreight.finance.model.CreditNote;
import com.eazyfreight.finance.model.InvoiceLineView;
import com.eazyfreight.finance.model.InvoiceView;
import com.eazyfreight.finance.model.Line;
import com.eazyfreight.finance.model.PayableView;
import com.eazyfreight.finance.model.PaymentView;
import com.eazyfreight.finance.model.PrepareInvoice;
import com.eazyfreight.finance.model.RecordPayment;
import com.eazyfreight.finance.model.StorageFeeView;
import com.eazyfreight.finance.model.UpdateLines;
import com.eazyfreight.finance.model.VoidInvoice;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Converts between Finance response/request DTOs and the generated OpenAPI models. */
final class FinanceApiMapper {

    private FinanceApiMapper() {
    }

    static InvoiceView toView(FinanceResponses.InvoiceView i) {
        InvoiceView view = new InvoiceView();
        view.setId(i.id());
        view.setInvoiceNumber(i.invoiceNumber());
        view.setBookingId(i.bookingId());
        view.setHouseBolId(i.houseBolId());
        view.setCustomerId(i.customerId());
        view.setStatus(mapEnum(i.status(), com.eazyfreight.finance.model.InvoiceStatus.class));
        view.setInvoiceType(mapEnum(i.invoiceType(), com.eazyfreight.finance.model.InvoiceType.class));
        view.setPaymentTermsType(mapEnum(i.paymentTermsType(), com.eazyfreight.finance.model.PaymentTermsType.class));
        view.setInvoiceDate(i.invoiceDate());
        view.setPaymentDueDate(i.paymentDueDate());
        view.setConfirmedEta(i.confirmedEta());
        view.setTotalAmount(i.totalAmount());
        view.setTotalBuyAmount(i.totalBuyAmount());
        view.setMargin(i.margin());
        view.setCurrency(i.currency());
        view.setPaidAmount(i.paidAmount());
        view.setOutstandingAmount(i.outstandingAmount());
        view.setActualsConfirmed(i.actualsConfirmed());
        view.setIssueBlockedReason(i.issueBlockedReason());
        view.setOverdue(i.overdue());
        view.setDaysOverdue(i.daysOverdue());
        view.setIssuedAt(toOffsetDateTime(i.issuedAt()));
        view.setIssuedBy(i.issuedBy());
        view.setPdfSentAt(toOffsetDateTime(i.pdfSentAt()));
        view.setVoidedAt(toOffsetDateTime(i.voidedAt()));
        view.setVoidedBy(i.voidedBy());
        view.setVoidReason(i.voidReason());
        view.setCreditNoteAgainstInvoiceId(i.creditNoteAgainstInvoiceId());
        view.setNotes(i.notes());
        view.setCreatedAt(toOffsetDateTime(i.createdAt()));
        view.setLines(i.lines().stream().map(FinanceApiMapper::toLineView).toList());
        view.setPayments(i.payments().stream().map(FinanceApiMapper::toPaymentView).toList());
        return view;
    }

    private static InvoiceLineView toLineView(FinanceResponses.Line l) {
        InvoiceLineView view = new InvoiceLineView();
        view.setId(l.id());
        view.setLineNumber(l.lineNumber());
        view.setDescription(l.description());
        view.setBuyAmount(l.buyAmount());
        view.setSellAmount(l.sellAmount());
        view.setQuantity(l.quantity());
        view.setUnit(l.unit());
        view.setExtendedBuy(l.extendedBuy());
        view.setExtendedSell(l.extendedSell());
        view.setMargin(l.margin());
        return view;
    }

    private static PaymentView toPaymentView(FinanceResponses.Payment p) {
        PaymentView view = new PaymentView();
        view.setId(p.id());
        view.setAmount(p.amount());
        view.setCurrency(p.currency());
        view.setPaymentDate(p.paymentDate());
        view.setPaymentMethod(mapEnum(p.paymentMethod(), com.eazyfreight.finance.model.PaymentMethod.class));
        view.setReference(p.reference());
        view.setRecordedAt(toOffsetDateTime(p.recordedAt()));
        view.setRecordedBy(p.recordedBy());
        return view;
    }

    static PayableView toView(FinanceResponses.PayableView p) {
        PayableView view = new PayableView();
        view.setId(p.id());
        view.setBookingId(p.bookingId());
        view.setInvoiceId(p.invoiceId());
        view.setCustomerPaymentId(p.customerPaymentId());
        view.setCarrierId(p.carrierId());
        view.setAmount(p.amount());
        view.setCurrency(p.currency());
        view.setCustomerPaymentDate(p.customerPaymentDate());
        view.setDueDate(p.dueDate());
        view.setStatus(mapEnum(p.status(), com.eazyfreight.finance.model.PayableStatus.class));
        view.setCarrierInvoiceReference(p.carrierInvoiceReference());
        view.setCarrierInvoiceAmount(p.carrierInvoiceAmount());
        view.setInvoiceVariance(p.invoiceVariance());
        view.setCarrierInvoiceReceivedAt(toOffsetDateTime(p.carrierInvoiceReceivedAt()));
        view.setApprovedAt(toOffsetDateTime(p.approvedAt()));
        view.setApprovedBy(p.approvedBy());
        view.setPaidOn(p.paidOn());
        view.setPaymentReference(p.paymentReference());
        view.setOverdue(p.overdue());
        view.setCreatedAt(toOffsetDateTime(p.createdAt()));
        return view;
    }

    static StorageFeeView toView(FinanceResponses.StorageFeeView f) {
        StorageFeeView view = new StorageFeeView();
        view.setId(f.id());
        view.setBookingId(f.bookingId());
        view.setCause(mapEnum(f.cause(), com.eazyfreight.finance.model.StorageFeeCause.class));
        view.setResponsibility(mapEnum(f.responsibility(), com.eazyfreight.finance.model.StorageFeeResponsibility.class));
        view.setDailyRate(f.dailyRate());
        view.setDays(f.days());
        view.setAmount(f.amount());
        view.setCurrency(f.currency());
        view.setPeriodFrom(f.periodFrom());
        view.setPeriodTo(f.periodTo());
        view.setInvoiceId(f.invoiceId());
        view.setInvoiceBlockedReason(f.invoiceBlockedReason());
        view.setNotes(f.notes());
        view.setCreatedAt(toOffsetDateTime(f.createdAt()));
        return view;
    }

    static CreditHoldView toView(FinanceResponses.CreditHoldView h) {
        CreditHoldView view = new CreditHoldView();
        view.setId(h.id());
        view.setCustomerId(h.customerId());
        view.setTriggeringBookingId(h.triggeringBookingId());
        view.setReason(h.reason());
        view.setActive(h.active());
        view.setPlacedAt(toOffsetDateTime(h.placedAt()));
        view.setPlacedBy(h.placedBy());
        view.setLiftedAt(toOffsetDateTime(h.liftedAt()));
        view.setLiftedBy(h.liftedBy());
        return view;
    }

    static FinanceRequests.PrepareInvoice toDomain(PrepareInvoice request) {
        if (request == null) {
            return null;
        }
        return new FinanceRequests.PrepareInvoice(
                mapEnum(request.getPaymentTermsType(), com.eazyfreight.finance.domain.FinanceEnums.PaymentTermsType.class),
                request.getCurrency());
    }

    static FinanceRequests.UpdateLines toDomain(UpdateLines request) {
        return new FinanceRequests.UpdateLines(request.getLines().stream()
                .map(FinanceApiMapper::toDomain)
                .toList());
    }

    private static FinanceRequests.Line toDomain(Line line) {
        return new FinanceRequests.Line(
                line.getDescription(), line.getBuyAmount(), line.getSellAmount(),
                line.getQuantity(), line.getUnit());
    }

    static FinanceRequests.RecordPayment toDomain(RecordPayment request) {
        return new FinanceRequests.RecordPayment(
                request.getAmount(), request.getPaymentDate(),
                mapEnum(request.getPaymentMethod(), com.eazyfreight.finance.domain.FinanceEnums.PaymentMethod.class),
                request.getReference());
    }

    static FinanceRequests.CarrierInvoice toDomain(CarrierInvoice request) {
        return new FinanceRequests.CarrierInvoice(request.getReference(), request.getAmount());
    }

    static FinanceRequests.CarrierPaymentMade toDomain(CarrierPaymentMade request) {
        return new FinanceRequests.CarrierPaymentMade(request.getPaidOn(), request.getReference());
    }

    static FinanceRequests.VoidInvoice toDomain(VoidInvoice request) {
        return new FinanceRequests.VoidInvoice(request.getReason());
    }

    static FinanceRequests.CreditNote toDomain(CreditNote request) {
        return new FinanceRequests.CreditNote(request.getAmount(), request.getReason());
    }

    static FinanceRequests.CalculateStorageFee toDomain(CalculateStorageFee request) {
        return new FinanceRequests.CalculateStorageFee(
                mapEnum(request.getCause(), com.eazyfreight.finance.domain.FinanceEnums.StorageFeeCause.class),
                request.getDailyRate(), request.getDays(),
                request.getPeriodFrom(), request.getPeriodTo(), request.getCurrency());
    }

    static FinanceRequests.AssignResponsibility toDomain(AssignResponsibility request) {
        return new FinanceRequests.AssignResponsibility(
                mapEnum(request.getResponsibility(), com.eazyfreight.finance.domain.FinanceEnums.StorageFeeResponsibility.class),
                request.getNotes());
    }

    static FinanceRequests.CreditHoldRequest toDomain(CreditHoldRequest request) {
        return new FinanceRequests.CreditHoldRequest(request.getCustomerId(), request.getReason());
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static <S extends Enum<S>, T extends Enum<T>> T mapEnum(S source, Class<T> targetType) {
        return source == null ? null : Enum.valueOf(targetType, source.name());
    }
}
