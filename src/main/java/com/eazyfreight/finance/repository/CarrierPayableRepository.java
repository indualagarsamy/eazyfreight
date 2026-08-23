package com.eazyfreight.finance.repository;

import com.eazyfreight.finance.domain.FinanceEnums.PayableStatus;

import com.eazyfreight.finance.domain.CarrierPayable;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CarrierPayableRepository extends JpaRepository<CarrierPayable, UUID> {

    List<CarrierPayable> findByBookingId(UUID bookingId);

    Optional<CarrierPayable> findByCustomerPaymentId(UUID customerPaymentId);

    List<CarrierPayable> findByStatusIn(List<PayableStatus> statuses);

    /** Owed to the carrier and past the T+2 date. */
    List<CarrierPayable> findByStatusNotAndDueDateLessThan(PayableStatus status, LocalDate date);
}
