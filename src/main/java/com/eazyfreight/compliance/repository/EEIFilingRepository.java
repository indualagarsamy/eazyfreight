package com.eazyfreight.compliance.repository;

import com.eazyfreight.compliance.domain.EEIFiling;
import com.eazyfreight.compliance.domain.FilingStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EEIFilingRepository extends JpaRepository<EEIFiling, UUID> {

    Optional<EEIFiling> findByFilingReference(String filingReference);

    List<EEIFiling> findByBookingId(UUID bookingId);

    List<EEIFiling> findByStatus(FilingStatus status);

    List<EEIFiling> findByStatusIn(List<FilingStatus> statuses);

    /** The accepted filing currently holding the active ITN for a booking. */
    Optional<EEIFiling> findFirstByBookingIdAndStatusOrderByAcceptedAtDesc(
            UUID bookingId, FilingStatus status);
}
