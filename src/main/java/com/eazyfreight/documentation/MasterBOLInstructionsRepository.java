package com.eazyfreight.documentation;

import com.eazyfreight.documentation.DocumentationEnums.InstructionsStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MasterBOLInstructionsRepository extends JpaRepository<MasterBOLInstructions, UUID> {

    List<MasterBOLInstructions> findByBookingId(UUID bookingId);

    Optional<MasterBOLInstructions> findFirstByBookingIdAndStatusOrderBySentAtDesc(
            UUID bookingId, InstructionsStatus status);
}
