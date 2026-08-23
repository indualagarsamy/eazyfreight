package com.eazyfreight.finance.repository;

import com.eazyfreight.finance.domain.FinanceEnums.StorageFeeResponsibility;

import com.eazyfreight.finance.domain.StorageFee;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StorageFeeRepository extends JpaRepository<StorageFee, UUID> {

    Optional<StorageFee> findByBookingId(UUID bookingId);

    List<StorageFee> findByResponsibility(StorageFeeResponsibility responsibility);

    List<StorageFee> findByInvoiceIdIsNull();
}
