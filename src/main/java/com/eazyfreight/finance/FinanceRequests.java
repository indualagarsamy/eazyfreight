package com.eazyfreight.finance;

import com.eazyfreight.finance.FinanceEnums.PaymentMethod;
import com.eazyfreight.finance.FinanceEnums.PaymentTermsType;
import com.eazyfreight.finance.FinanceEnums.StorageFeeCause;
import com.eazyfreight.finance.FinanceEnums.StorageFeeResponsibility;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Request payloads for the Finance commands. */
public final class FinanceRequests {

    private FinanceRequests() {
    }

    public record PrepareInvoice(
            PaymentTermsType paymentTermsType,
            String currency
    ) {
    }

    public record Line(
            @NotBlank(message = "description is required") String description,
            @NotNull(message = "buyAmount is required")
            @DecimalMin(value = "0.00", message = "buyAmount cannot be negative") BigDecimal buyAmount,
            @NotNull(message = "sellAmount is required")
            @DecimalMin(value = "0.00", message = "sellAmount cannot be negative") BigDecimal sellAmount,
            @NotNull(message = "quantity is required")
            @DecimalMin(value = "0.0001", message = "quantity must be greater than 0") BigDecimal quantity,
            String unit
    ) {
    }

    public record UpdateLines(
            @NotEmpty(message = "lines is required") List<Line> lines
    ) {
    }

    public record RecordPayment(
            @NotNull(message = "amount is required")
            @DecimalMin(value = "0.01", message = "amount must be positive") BigDecimal amount,
            @NotNull(message = "paymentDate is required") LocalDate paymentDate,
            @NotNull(message = "paymentMethod is required") PaymentMethod paymentMethod,
            String reference
    ) {
    }

    public record CarrierInvoice(
            @NotBlank(message = "reference is required") String reference,
            @NotNull(message = "amount is required") BigDecimal amount
    ) {
    }

    public record CarrierPaymentMade(
            @NotNull(message = "paidOn is required") LocalDate paidOn,
            String reference
    ) {
    }

    public record VoidInvoice(
            @NotBlank(message = "reason is required") String reason
    ) {
    }

    public record CreditNote(
            @NotNull(message = "amount is required")
            @DecimalMin(value = "0.01", message = "amount must be positive") BigDecimal amount,
            @NotBlank(message = "reason is required") String reason
    ) {
    }

    public record CalculateStorageFee(
            @NotNull(message = "cause is required") StorageFeeCause cause,
            @NotNull(message = "dailyRate is required") BigDecimal dailyRate,
            @Min(value = 1, message = "days must be at least 1") int days,
            LocalDate periodFrom,
            LocalDate periodTo,
            String currency
    ) {
    }

    public record AssignResponsibility(
            @NotNull(message = "responsibility is required") StorageFeeResponsibility responsibility,
            String notes
    ) {
    }

    public record CreditHoldRequest(
            @NotNull(message = "customerId is required") java.util.UUID customerId,
            @NotBlank(message = "reason is required") String reason
    ) {
    }
}
