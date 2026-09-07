package com.eazyfreight.alerts.controller;

import com.eazyfreight.alerts.api.AlertsApi;
import com.eazyfreight.alerts.model.AlertCategory;
import com.eazyfreight.alerts.model.AlertTrack;
import com.eazyfreight.alerts.model.AlertType;
import com.eazyfreight.alerts.model.AlertView;
import com.eazyfreight.alerts.model.ConfigurationView;
import com.eazyfreight.alerts.model.Dashboard;
import com.eazyfreight.alerts.model.EvaluateAllResult;
import com.eazyfreight.alerts.model.EvaluateBookingResult;
import com.eazyfreight.alerts.model.Resolve;
import com.eazyfreight.alerts.model.Snooze;
import com.eazyfreight.alerts.model.UpdateConfiguration;
import com.eazyfreight.alerts.service.AlertService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController implements AlertsApi {

    private final AlertService alertService;
    private final Clock clock;

    // ---------------------------------------------------------------- queries

    @Override
    @RequestMapping(method = RequestMethod.GET, value = {"", "/"}, produces = "application/json")
    public ResponseEntity<List<AlertView>> open(AlertCategory category, AlertTrack track) {
        var domainCategory = AlertApiMapper.toDomain(category);
        var domainTrack = AlertApiMapper.toDomain(track);
        return ResponseEntity.ok(alertService.findOpen().stream()
                .filter(alert -> domainCategory == null || alert.getCategory() == domainCategory)
                .filter(alert -> domainTrack == null || alert.getTrack() == domainTrack)
                .map(alert -> AlertApiMapper.toView(alert, clock.instant()))
                .toList());
    }

    @Override
    public ResponseEntity<Dashboard> dashboard() {
        return ResponseEntity.ok(AlertApiMapper.toDashboard(alertService.findOpen(), clock.instant()));
    }

    @Override
    public ResponseEntity<List<AlertView>> overdue() {
        return ResponseEntity.ok(alertService.findOverdue().stream()
                .map(alert -> AlertApiMapper.toView(alert, clock.instant()))
                .toList());
    }

    @Override
    public ResponseEntity<AlertView> byId(UUID id) {
        return ResponseEntity.ok(AlertApiMapper.toView(alertService.get(id), clock.instant()));
    }

    @Override
    public ResponseEntity<List<AlertView>> forBooking(UUID bookingId) {
        return ResponseEntity.ok(alertService.findForBooking(bookingId).stream()
                .map(alert -> AlertApiMapper.toView(alert, clock.instant()))
                .toList());
    }

    @Override
    public ResponseEntity<List<ConfigurationView>> configurations() {
        return ResponseEntity.ok(alertService.configurations().stream()
                .map(AlertApiMapper::toConfigurationView)
                .toList());
    }

    // --------------------------------------------------------------- commands

    /**
     * Forces the sweep the scheduler runs every half hour.
     *
     * <p>Exposed because "wait up to thirty minutes" is not a demonstration, and because
     * an operator who has just fixed something reasonably wants to see the alert go.
     */
    @Override
    public ResponseEntity<EvaluateAllResult> evaluate() {
        EvaluateAllResult result = new EvaluateAllResult();
        result.setChanges(alertService.evaluateAll());
        result.setOpen(alertService.findOpen().size());
        return ResponseEntity.ok(result);
    }

    @Override
    public ResponseEntity<EvaluateBookingResult> evaluateBooking(UUID bookingId) {
        EvaluateBookingResult result = new EvaluateBookingResult();
        result.setChanges(alertService.evaluateBooking(bookingId));
        return ResponseEntity.ok(result);
    }

    @Override
    public ResponseEntity<AlertView> acknowledge(UUID id, String xActor) {
        return ResponseEntity.ok(AlertApiMapper.toView(
                alertService.acknowledge(id, xActor), clock.instant()));
    }

    @Override
    public ResponseEntity<AlertView> snooze(UUID id, Snooze snooze, String xActor) {
        return ResponseEntity.ok(AlertApiMapper.toView(
                alertService.snooze(id, xActor, AlertApiMapper.toInstant(snooze.getUntil())), clock.instant()));
    }

    @Override
    public ResponseEntity<AlertView> resolve(UUID id, Resolve resolve, String xActor) {
        return ResponseEntity.ok(AlertApiMapper.toView(
                alertService.resolve(id, xActor, resolve.getReason()), clock.instant()));
    }

    @Override
    public ResponseEntity<ConfigurationView> updateConfiguration(AlertType alertType, UpdateConfiguration updateConfiguration) {
        return ResponseEntity.ok(AlertApiMapper.toConfigurationView(alertService.updateConfiguration(
                AlertApiMapper.toDomain(alertType),
                updateConfiguration.getEnabled(),
                updateConfiguration.getThresholdDays(),
                updateConfiguration.getEscalationHours(),
                AlertApiMapper.toDomainChannels(updateConfiguration.getChannels()),
                updateConfiguration.getSnoozeMaxHours(),
                updateConfiguration.getCustomMessage())));
    }
}
