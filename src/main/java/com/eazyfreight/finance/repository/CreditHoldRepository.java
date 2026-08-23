package com.eazyfreight.finance.repository;

import com.eazyfreight.finance.domain.CreditHold;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CreditHoldRepository extends JpaRepository<CreditHold, UUID> {

    Optional<CreditHold> findByCustomerIdAndActiveTrue(UUID customerId);

    List<CreditHold> findByActiveTrue();
}
