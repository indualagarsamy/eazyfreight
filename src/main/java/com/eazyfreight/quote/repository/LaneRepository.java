package com.eazyfreight.quote.repository;

import com.eazyfreight.quote.domain.Lane;
import com.eazyfreight.quote.domain.ShippingMode;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LaneRepository extends JpaRepository<Lane, UUID> {

    Optional<Lane> findByOriginPortCodeAndDestinationPortCodeAndMode(
            String originPortCode, String destinationPortCode, ShippingMode mode);
}
