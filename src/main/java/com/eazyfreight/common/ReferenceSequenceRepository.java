package com.eazyfreight.common;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface ReferenceSequenceRepository extends JpaRepository<ReferenceSequence, ReferenceSequence.Key> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ReferenceSequence> findByPrefixAndSequenceYear(String prefix, int year);
}
