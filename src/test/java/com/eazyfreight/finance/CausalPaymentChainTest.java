package com.eazyfreight.finance;

import com.eazyfreight.exception.DomainRuleViolationException;
import com.eazyfreight.finance.FinanceEnums.PayableStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The causal payment chain — the most important financial rule in the system.
 *
 * <p>Eazy Freight pays the carrier out of the customer's money. So the payable is
 * created <em>from</em> the payment, and its due date is two business days after
 * it. A late customer makes a late carrier payment, and that should be visible
 * rather than smoothed over.
 */
class CausalPaymentChainTest {

    private static final Instant NOW = Instant.parse("2026-08-22T10:00:00Z");
    private static final UUID BOOKING = UUID.randomUUID();
    private static final UUID INVOICE = UUID.randomUUID();
    private static final UUID PAYMENT = UUID.randomUUID();
    private static final String ACTOR = "acct.pat";

    @Test
    void aPayableCannotExistWithoutTheCustomerPaymentThatFundsIt() {
        assertThatThrownBy(() -> CarrierPayable.fundedBy(
                null, INVOICE, BOOKING, UUID.randomUUID(),
                new BigDecimal("950.00"), "USD", LocalDate.of(2026, 8, 20), NOW))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("must be funded by a customer payment");
    }

    @Test
    void theDueDateIsTwoBusinessDaysAfterTheCustomerPaid() {
        // Tuesday 18 August 2026 + 2 business days = Thursday 20 August.
        CarrierPayable payable = payableFundedOn(LocalDate.of(2026, 8, 18));
        assertThat(payable.getDueDate()).isEqualTo(LocalDate.of(2026, 8, 20));
    }

    @Test
    void theTwoDayWindowSkipsTheWeekend() {
        // Thursday 20 August + 2 business days = Monday 24 August, not Saturday.
        CarrierPayable payable = payableFundedOn(LocalDate.of(2026, 8, 20));
        assertThat(payable.getDueDate()).isEqualTo(LocalDate.of(2026, 8, 24));
        assertThat(payable.getDueDate().getDayOfWeek())
                .isEqualTo(java.time.DayOfWeek.MONDAY);
    }

    @Test
    void aLateCustomerPaymentMakesTheCarrierPaymentLateByTheSameAmount() {
        CarrierPayable onTime = payableFundedOn(LocalDate.of(2026, 8, 18));
        CarrierPayable late = payableFundedOn(LocalDate.of(2026, 9, 18));

        // The window is constant; the whole chain simply shifts.
        assertThat(java.time.temporal.ChronoUnit.DAYS.between(
                onTime.getCustomerPaymentDate(), onTime.getDueDate())).isEqualTo(2);
        assertThat(late.getDueDate()).isAfter(onTime.getDueDate());
    }

    @Test
    void theCarrierInvoiceMustBeMatchedBeforePaymentIsApproved() {
        CarrierPayable payable = payableFundedOn(LocalDate.of(2026, 8, 18));
        assertThat(payable.getStatus()).isEqualTo(PayableStatus.PENDING);

        assertThatThrownBy(() -> payable.approve(NOW, ACTOR))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("must be received and matched");

        payable.recordCarrierInvoice("MAEU-INV-5567", new BigDecimal("950.00"), NOW);
        assertThat(payable.getStatus()).isEqualTo(PayableStatus.AWAITING_INVOICE_MATCH);

        payable.approve(NOW, ACTOR);
        assertThat(payable.getStatus()).isEqualTo(PayableStatus.APPROVED);
    }

    @Test
    void moneyDoesNotMoveBeforeApproval() {
        CarrierPayable payable = payableFundedOn(LocalDate.of(2026, 8, 18));
        payable.recordCarrierInvoice("MAEU-INV-5567", new BigDecimal("950.00"), NOW);

        assertThatThrownBy(() -> payable.recordPaid(
                LocalDate.of(2026, 8, 20), "WIRE-1", NOW, ACTOR))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("Only an approved payable can be paid");
    }

    @Test
    void aCarrierInvoiceDifferingFromWhatWeExpectedShowsAsVariance() {
        CarrierPayable payable = payableFundedOn(LocalDate.of(2026, 8, 18));
        payable.recordCarrierInvoice("MAEU-INV-5567", new BigDecimal("995.00"), NOW);

        assertThat(payable.invoiceVariance()).isEqualByComparingTo("45.00");
    }

    @Test
    void payingAfterTheDueDateIsRecordedAsLate() {
        CarrierPayable payable = approved(LocalDate.of(2026, 8, 18));

        payable.recordPaid(LocalDate.of(2026, 8, 25), "WIRE-1", NOW, ACTOR);

        assertThat(payable.getStatus()).isEqualTo(PayableStatus.PAID);
        assertThat(payable.pendingEvents())
                .filteredOn(FinanceEvent.CarrierPaymentMade.class::isInstance)
                .allSatisfy(event ->
                        assertThat(((FinanceEvent.CarrierPaymentMade) event).late()).isTrue());
    }

    @Test
    void payingWithinTheWindowIsNotLate() {
        CarrierPayable payable = approved(LocalDate.of(2026, 8, 18));

        payable.recordPaid(LocalDate.of(2026, 8, 20), "WIRE-1", NOW, ACTOR);

        assertThat(payable.pendingEvents())
                .filteredOn(FinanceEvent.CarrierPaymentMade.class::isInstance)
                .allSatisfy(event ->
                        assertThat(((FinanceEvent.CarrierPaymentMade) event).late()).isFalse());
    }

    // ----------------------------------------------------------------- fixtures

    private CarrierPayable payableFundedOn(LocalDate paymentDate) {
        return CarrierPayable.fundedBy(PAYMENT, INVOICE, BOOKING, UUID.randomUUID(),
                new BigDecimal("950.00"), "USD", paymentDate, NOW);
    }

    private CarrierPayable approved(LocalDate paymentDate) {
        CarrierPayable payable = payableFundedOn(paymentDate);
        payable.recordCarrierInvoice("MAEU-INV-5567", new BigDecimal("950.00"), NOW);
        payable.approve(NOW, ACTOR);
        return payable;
    }
}
