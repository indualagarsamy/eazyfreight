package com.eazyfreight.quote;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RateRepository extends JpaRepository<Rate, UUID> {

    List<Rate> findByLaneId(UUID laneId);

    List<Rate> findByLaneIdAndCarrierId(UUID laneId, UUID carrierId);
}
