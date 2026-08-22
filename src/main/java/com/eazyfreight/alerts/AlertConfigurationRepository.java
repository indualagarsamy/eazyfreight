package com.eazyfreight.alerts;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AlertConfigurationRepository extends JpaRepository<AlertConfiguration, UUID> {

    Optional<AlertConfiguration> findByAlertType(AlertType alertType);
}
