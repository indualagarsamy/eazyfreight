package com.eazyfreight.alerts.repository;

import com.eazyfreight.alerts.domain.AlertConfiguration;
import com.eazyfreight.alerts.domain.AlertType;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AlertConfigurationRepository extends JpaRepository<AlertConfiguration, UUID> {

    Optional<AlertConfiguration> findByAlertType(AlertType alertType);
}
