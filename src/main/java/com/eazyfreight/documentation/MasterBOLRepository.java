package com.eazyfreight.documentation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MasterBOLRepository extends JpaRepository<MasterBOL, UUID> {

    List<MasterBOL> findByBookingId(UUID bookingId);

    Optional<MasterBOL> findFirstByBookingIdOrderByReceivedAtDesc(UUID bookingId);
}
