package com.eazyfreight.finance;

import com.eazyfreight.finance.FinanceEnums.StorageFeeResponsibility;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StorageFeeRepository extends JpaRepository<StorageFee, UUID> {

    Optional<StorageFee> findByBookingId(UUID bookingId);

    List<StorageFee> findByResponsibility(StorageFeeResponsibility responsibility);

    List<StorageFee> findByInvoiceIdIsNull();
}
