package com.eazyfreight.logistics;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContainerAssignmentRepository extends JpaRepository<ContainerAssignment, UUID> {

    Optional<ContainerAssignment> findByBookingId(UUID bookingId);

    Optional<ContainerAssignment> findByContainerNumber(String containerNumber);

    List<ContainerAssignment> findByStage(LogisticsStage stage);

    List<ContainerAssignment> findByStageIn(List<LogisticsStage> stages);

    /** Sealed and waiting on the ITN — the queue that stalls a shipment silently. */
    List<ContainerAssignment> findByStageAndItnReceivedFalse(LogisticsStage stage);
}
