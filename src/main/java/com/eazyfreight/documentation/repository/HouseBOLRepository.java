package com.eazyfreight.documentation.repository;

import com.eazyfreight.documentation.domain.HouseBOL;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HouseBOLRepository extends JpaRepository<HouseBOL, UUID> {

    List<HouseBOL> findByBookingId(UUID bookingId);

    /** The live revision. Exactly one per booking. */
    Optional<HouseBOL> findByBookingIdAndActiveTrue(UUID bookingId);

    List<HouseBOL> findByHouseBolNumberOrderByRevisionNumber(String houseBolNumber);

    List<HouseBOL> findByActiveTrue();
}
