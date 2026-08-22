package com.eazyfreight.quote;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LaneRepository extends JpaRepository<Lane, UUID> {

    Optional<Lane> findByOriginPortCodeAndDestinationPortCodeAndMode(
            String originPortCode, String destinationPortCode, ShippingMode mode);
}
