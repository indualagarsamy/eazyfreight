package com.eazyfreight.alerts;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private static final String ACTOR_HEADER = "X-Actor";
    private static final String DEFAULT_ACTOR = "operations";

    private final AlertService alertService;
    private final Clock clock;

    // ---------------------------------------------------------------- queries

    @GetMapping
    public List<AlertResponses.AlertView> open(
            @RequestParam(required = false) AlertCategory category,
            @RequestParam(required = false) AlertTrack track) {
        return alertService.findOpen().stream()
                .filter(alert -> category == null || alert.getCategory() == category)
                .filter(alert -> track == null || alert.getTrack() == track)
                .map(alert -> AlertResponses.AlertView.from(alert, clock.instant()))
                .toList();
    }

    @GetMapping("/dashboard")
    public AlertResponses.Dashboard dashboard() {
        return AlertResponses.Dashboard.of(alertService.findOpen(), clock.instant());
    }

    @GetMapping("/overdue")
    public List<AlertResponses.AlertView> overdue() {
        return alertService.findOverdue().stream()
                .map(alert -> AlertResponses.AlertView.from(alert, clock.instant()))
                .toList();
    }

    @GetMapping("/{id}")
    public AlertResponses.AlertView byId(@PathVariable UUID id) {
        return AlertResponses.AlertView.from(alertService.get(id), clock.instant());
    }

    @GetMapping("/bookings/{bookingId}")
    public List<AlertResponses.AlertView> forBooking(@PathVariable UUID bookingId) {
        return alertService.findForBooking(bookingId).stream()
                .map(alert -> AlertResponses.AlertView.from(alert, clock.instant()))
                .toList();
    }

    @GetMapping("/configurations")
    public List<AlertResponses.ConfigurationView> configurations() {
        return alertService.configurations().stream()
                .map(AlertResponses.ConfigurationView::from)
                .toList();
    }

    // --------------------------------------------------------------- commands

    /**
     * Forces the sweep the scheduler runs every half hour.
     *
     * <p>Exposed because "wait up to thirty minutes" is not a demonstration, and because
     * an operator who has just fixed something reasonably wants to see the alert go.
     */
    @PostMapping("/evaluate")
    public Map<String, Object> evaluate() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("changes", alertService.evaluateAll());
        body.put("open", alertService.findOpen().size());
        return body;
    }

    @PostMapping("/bookings/{bookingId}/evaluate")
    public Map<String, Object> evaluateBooking(@PathVariable UUID bookingId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("changes", alertService.evaluateBooking(bookingId));
        return body;
    }

    @PostMapping("/{id}/acknowledge")
    public AlertResponses.AlertView acknowledge(
            @PathVariable UUID id,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return AlertResponses.AlertView.from(
                alertService.acknowledge(id, actor), clock.instant());
    }

    @PostMapping("/{id}/snooze")
    public AlertResponses.AlertView snooze(
            @PathVariable UUID id,
            @Valid @RequestBody AlertRequests.Snooze request,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return AlertResponses.AlertView.from(
                alertService.snooze(id, actor, request.until()), clock.instant());
    }

    @PostMapping("/{id}/resolve")
    public AlertResponses.AlertView resolve(
            @PathVariable UUID id,
            @Valid @RequestBody AlertRequests.Resolve request,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return AlertResponses.AlertView.from(
                alertService.resolve(id, actor, request.reason()), clock.instant());
    }

    @PutMapping("/configurations/{alertType}")
    public AlertResponses.ConfigurationView updateConfiguration(
            @PathVariable AlertType alertType,
            @Valid @RequestBody AlertRequests.UpdateConfiguration request) {
        return AlertResponses.ConfigurationView.from(alertService.updateConfiguration(
                alertType, request.enabled(), request.thresholdDays(),
                request.escalationHours(), request.channels(),
                request.snoozeMaxHours(), request.customMessage()));
    }
}
