package com.eazyfreight.finance;

import com.eazyfreight.finance.FinanceEnums.InvoiceStatus;
import com.eazyfreight.finance.FinanceEnums.InvoiceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    List<Invoice> findByBookingId(UUID bookingId);

    Optional<Invoice> findFirstByBookingIdAndInvoiceType(UUID bookingId, InvoiceType invoiceType);

    List<Invoice> findByCustomerId(UUID customerId);

    List<Invoice> findByStatusIn(List<InvoiceStatus> statuses);

    /** Issued, not settled, past due — the chase list. */
    List<Invoice> findByStatusInAndPaymentDueDateLessThan(
            List<InvoiceStatus> statuses, LocalDate date);
}
