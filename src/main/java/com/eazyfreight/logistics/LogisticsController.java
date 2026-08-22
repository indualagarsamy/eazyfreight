package com.eazyfreight.logistics;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Container and equipment endpoints, keyed by booking. */
@RestController
@RequestMapping("/api/logistics")
@RequiredArgsConstructor
public class LogisticsController {

    private static final String ACTOR_HEADER = "X-Actor";
    private static final String DEFAULT_ACTOR = "operations";

    private final LogisticsService logisticsService;

    @GetMapping
    public List<LogisticsResponses.Logistics> getAll() {
        return logisticsService.findAll();
    }

    @GetMapping("/blocked-on-itn")
    public List<LogisticsResponses.Logistics> getBlockedOnItn() {
        return logisticsService.findBlockedOnItn();
    }

    @GetMapping("/under-examination")
    public List<LogisticsResponses.Logistics> getUnderExamination() {
        return logisticsService.findUnderExamination();
    }

    @GetMapping("/bookings/{bookingId}")
    public LogisticsResponses.Logistics getByBooking(@PathVariable UUID bookingId) {
        return logisticsService.findByBooking(bookingId);
    }

    /** 6. CheckITNGate — reports whether the inbound movement may be authorised. */
    @GetMapping("/bookings/{bookingId}/itn-gate")
    public Map<String, Object> checkItnGate(@PathVariable UUID bookingId) {
        String blocked = logisticsService.checkItnGate(bookingId);
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("clear", blocked == null);
        body.put("reason", blocked);
        return Collections.unmodifiableMap(body);
    }

    /** 1. DispatchOutboundTruck */
    @PostMapping("/bookings/{bookingId}/outbound-dispatch")
    public ResponseEntity<LogisticsResponses.Logistics> dispatchOutbound(
            @PathVariable UUID bookingId,
            @Valid @RequestBody LogisticsRequests.DispatchTruck request,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(logisticsService.dispatchOutbound(bookingId, request, actor));
    }

    /** 2. RecordContainerNumber */
    @PostMapping("/bookings/{bookingId}/container-number")
    public LogisticsResponses.Logistics recordContainerNumber(
            @PathVariable UUID bookingId,
            @Valid @RequestBody LogisticsRequests.RecordContainerNumber request,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return logisticsService.recordContainerNumber(bookingId, request, actor);
    }

    /** 3. RecordContainerDeliveredToCustomer */
    @PostMapping("/bookings/{bookingId}/delivered-to-customer")
    public LogisticsResponses.Logistics recordDeliveredToCustomer(@PathVariable UUID bookingId) {
        return logisticsService.recordDeliveredToCustomer(bookingId);
    }

    /** 4. RecordCustomerLoadingComplete */
    @PostMapping("/bookings/{bookingId}/loading-complete")
    public LogisticsResponses.Logistics recordLoadingComplete(@PathVariable UUID bookingId) {
        return logisticsService.recordLoadingComplete(bookingId);
    }

    /** 5. RecordSealNumber */
    @PostMapping("/bookings/{bookingId}/seal")
    public LogisticsResponses.Logistics recordSeal(
            @PathVariable UUID bookingId,
            @Valid @RequestBody LogisticsRequests.RecordSeal request,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return logisticsService.recordSeal(bookingId, request, actor);
    }

    /** 13. RecordSealReplacement — outside a CBP examination, e.g. a damaged seal. */
    @PostMapping("/bookings/{bookingId}/seal/replace")
    public LogisticsResponses.Logistics replaceSeal(
            @PathVariable UUID bookingId,
            @Valid @RequestBody LogisticsRequests.RecordSeal request,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return logisticsService.replaceSeal(bookingId, request, actor);
    }

    /** 7. DispatchInboundTruck — refused until the ITN gate is clear. */
    @PostMapping("/bookings/{bookingId}/inbound-dispatch")
    public ResponseEntity<LogisticsResponses.Logistics> dispatchInbound(
            @PathVariable UUID bookingId,
            @Valid @RequestBody LogisticsRequests.DispatchTruck request,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(logisticsService.dispatchInbound(bookingId, request, actor));
    }

    /** 8. RecordLoadedContainerPickedUp */
    @PostMapping("/bookings/{bookingId}/loaded-container-picked-up")
    public LogisticsResponses.Logistics recordLoadedContainerPickedUp(@PathVariable UUID bookingId) {
        return logisticsService.recordLoadedContainerPickedUp(bookingId);
    }

    /** 9. RecordContainerDeliveredToPort */
    @PostMapping("/bookings/{bookingId}/delivered-to-port")
    public LogisticsResponses.Logistics recordDeliveredToPort(@PathVariable UUID bookingId) {
        return logisticsService.recordDeliveredToPort(bookingId);
    }

    /** 10. RecordTerminalGateReceipt */
    @PostMapping("/bookings/{bookingId}/terminal-receipt")
    public LogisticsResponses.Logistics recordTerminalGateReceipt(
            @PathVariable UUID bookingId,
            @Valid @RequestBody LogisticsRequests.TerminalGateReceipt request) {
        return logisticsService.recordTerminalGateReceipt(bookingId, request);
    }

    /** 11. RecordTerminalGateRejection */
    @PostMapping("/bookings/{bookingId}/terminal-rejection")
    public LogisticsResponses.Logistics recordTerminalGateRejection(
            @PathVariable UUID bookingId,
            @Valid @RequestBody LogisticsRequests.TerminalGateRejection request) {
        return logisticsService.recordTerminalGateRejection(bookingId, request);
    }

    /** 12. RecordCBPExaminationHold */
    @PostMapping("/bookings/{bookingId}/examination-hold")
    public LogisticsResponses.Logistics recordExaminationHold(
            @PathVariable UUID bookingId,
            @RequestBody LogisticsRequests.ExaminationHold request) {
        return logisticsService.recordExaminationHold(bookingId, request);
    }

    /** 14. RecordCBPExaminationRelease — always issues a replacement seal. */
    @PostMapping("/bookings/{bookingId}/examination-release")
    public LogisticsResponses.Logistics recordExaminationRelease(
            @PathVariable UUID bookingId,
            @Valid @RequestBody LogisticsRequests.ExaminationRelease request,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return logisticsService.recordExaminationRelease(bookingId, request, actor);
    }

    /** 15. RecordContainerLoadedOnVessel */
    @PostMapping("/bookings/{bookingId}/loaded-on-vessel")
    public LogisticsResponses.Logistics recordLoadedOnVessel(
            @PathVariable UUID bookingId,
            @RequestBody LogisticsRequests.LoadedOnVessel request) {
        return logisticsService.recordLoadedOnVessel(bookingId, request);
    }

    /** 16. RecordVesselDeparture */
    @PostMapping("/bookings/{bookingId}/vessel-departed")
    public LogisticsResponses.Logistics recordVesselDeparted(@PathVariable UUID bookingId) {
        return logisticsService.recordVesselDeparted(bookingId);
    }

    /** 17. RecordActualCargoDetails */
    @PostMapping("/bookings/{bookingId}/actual-cargo")
    public LogisticsResponses.Logistics recordActualCargo(
            @PathVariable UUID bookingId,
            @Valid @RequestBody LogisticsRequests.ActualCargo request,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return logisticsService.recordActualCargo(bookingId, request, actor);
    }
}
