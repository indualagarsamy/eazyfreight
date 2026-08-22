package com.eazyfreight.finance;

import com.eazyfreight.exception.DomainRuleViolationException;
import com.eazyfreight.finance.FinanceEnums.InvoiceStatus;
import com.eazyfreight.finance.FinanceEnums.PaymentMethod;
import com.eazyfreight.finance.FinanceEnums.PaymentTermsType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests for the invoice state machine and the payment-terms arithmetic. */
class InvoiceLifecycleTest {

    private static final Instant NOW = Instant.parse("2026-08-22T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 22);
    private static final LocalDate ETA = LocalDate.of(2026, 11, 9);
    private static final UUID BOOKING = UUID.randomUUID();
    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final String ACTOR = "acct.pat";

    @Test
    void anInvoiceCannotBeIssuedUntilTheHouseBolExists() {
        Invoice invoice = prepared(PaymentTermsType.NET_30);
        withLines(invoice);
        invoice.applyActuals(lines(), NOW);

        assertThat(invoice.issueBlockedReason()).contains("House BOL has not been issued");
        assertThatThrownBy(() -> invoice.issue(TODAY, NOW, ACTOR))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("House BOL");

        invoice.linkHouseBol(UUID.randomUUID());

        assertThat(invoice.issueBlockedReason()).isNull();
        invoice.issue(TODAY, NOW, ACTOR);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
    }

    @Test
    void anInvoiceCannotBeIssuedUntilActualsAreConfirmed() {
        Invoice invoice = prepared(PaymentTermsType.NET_30);
        withLines(invoice);
        invoice.linkHouseBol(UUID.randomUUID());

        assertThat(invoice.issueBlockedReason()).contains("Actual cargo figures");
    }

    @Test
    void netThirtyIsThirtyDaysFromTheInvoiceDate() {
        Invoice invoice = issued(PaymentTermsType.NET_30);
        assertThat(invoice.getPaymentDueDate()).isEqualTo(TODAY.plusDays(30));
    }

    @Test
    void twoWeeksBeforeArrivalIsFourteenDaysBeforeTheEta() {
        Invoice invoice = issued(PaymentTermsType.TWO_WEEKS_BEFORE_ARRIVAL);
        // The customer pays before the cargo lands, which is the point of the term.
        assertThat(invoice.getPaymentDueDate()).isEqualTo(ETA.minusDays(14));
        assertThat(invoice.getPaymentDueDate()).isBefore(ETA);
    }

    @Test
    void noTermsOnFileDefaultsToTheConservativeOption() {
        Invoice invoice = Invoice.prepare("INV-1", BOOKING, CUSTOMER, null, ETA, "USD", NOW);
        assertThat(invoice.getPaymentTermsType())
                .isEqualTo(PaymentTermsType.TWO_WEEKS_BEFORE_ARRIVAL);
    }

    @Test
    void linesCannotChangeOnceIssued() {
        Invoice invoice = issued(PaymentTermsType.NET_30);
        assertThatThrownBy(() -> invoice.replaceLines(lines()))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("cannot be changed once the invoice is issued");
    }

    @Test
    void marginIsAPropertyOfTheInvoice() {
        Invoice invoice = issued(PaymentTermsType.NET_30);
        // 1100 + 120 sell against 850 + 100 buy.
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("1220.00");
        assertThat(invoice.getTotalBuyAmount()).isEqualByComparingTo("950.00");
        assertThat(invoice.margin()).isEqualByComparingTo("270.00");
    }

    @Test
    void partialPaymentLeavesTheInvoiceOutstanding() {
        Invoice invoice = issued(PaymentTermsType.NET_30);

        invoice.recordPayment(new BigDecimal("500.00"), TODAY, PaymentMethod.WIRE, "W1", NOW, ACTOR);

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
        assertThat(invoice.outstandingAmount()).isEqualByComparingTo("720.00");

        invoice.recordPayment(new BigDecimal("720.00"), TODAY, PaymentMethod.WIRE, "W2", NOW, ACTOR);

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoice.outstandingAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void overpaymentIsRefused() {
        Invoice invoice = issued(PaymentTermsType.NET_30);
        assertThatThrownBy(() -> invoice.recordPayment(
                new BigDecimal("2000.00"), TODAY, PaymentMethod.WIRE, null, NOW, ACTOR))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("exceeds the outstanding balance");
    }

    @Test
    void paymentIsRefusedBeforeTheInvoiceIsIssued() {
        Invoice invoice = prepared(PaymentTermsType.NET_30);
        withLines(invoice);
        assertThatThrownBy(() -> invoice.recordPayment(
                new BigDecimal("100.00"), TODAY, PaymentMethod.WIRE, null, NOW, ACTOR))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("has not been issued");
    }

    @Test
    void anInvoiceWithPaymentsAgainstItCannotBeVoided() {
        Invoice invoice = issued(PaymentTermsType.NET_30);
        invoice.recordPayment(new BigDecimal("500.00"), TODAY, PaymentMethod.WIRE, null, NOW, ACTOR);

        assertThatThrownBy(() -> invoice.voidInvoice("Cancelled", NOW, ACTOR))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("credit note instead");
    }

    @Test
    void aCreditNoteIsASeparateNegativeDocument() {
        Invoice invoice = issued(PaymentTermsType.NET_30);

        Invoice note = Invoice.creditNote("CN-1", invoice,
                new BigDecimal("120.00"), "THC billed in error", NOW, ACTOR);

        assertThat(note.getTotalAmount()).isEqualByComparingTo("-120.00");
        assertThat(note.getCreditNoteAgainstInvoiceId()).isEqualTo(invoice.getId());
        // The original is untouched.
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("1220.00");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
    }

    @Test
    void overdueIsMeasuredAgainstTheDueDate() {
        Invoice invoice = issued(PaymentTermsType.NET_30);
        LocalDate dueDate = invoice.getPaymentDueDate();

        assertThat(invoice.isOverdueAsOf(dueDate)).isFalse();
        assertThat(invoice.isOverdueAsOf(dueDate.plusDays(5))).isTrue();
        assertThat(invoice.daysOverdue(dueDate.plusDays(5))).isEqualTo(5);
    }

    // ----------------------------------------------------------------- fixtures

    private Invoice prepared(PaymentTermsType terms) {
        return Invoice.prepare("INV-2026-00001", BOOKING, CUSTOMER, terms, ETA, "USD", NOW);
    }

    private Invoice issued(PaymentTermsType terms) {
        Invoice invoice = prepared(terms);
        invoice.applyActuals(lines(), NOW);
        invoice.linkHouseBol(UUID.randomUUID());
        invoice.issue(TODAY, NOW, ACTOR);
        return invoice;
    }

    private void withLines(Invoice invoice) {
        invoice.replaceLines(lines());
    }

    private List<Invoice.LineDraft> lines() {
        return List.of(
                new Invoice.LineDraft("Ocean Freight", new BigDecimal("850.00"),
                        new BigDecimal("1100.00"), BigDecimal.ONE, "CBM"),
                new Invoice.LineDraft("THC Origin", new BigDecimal("100.00"),
                        new BigDecimal("120.00"), BigDecimal.ONE, "FLAT"));
    }
}
