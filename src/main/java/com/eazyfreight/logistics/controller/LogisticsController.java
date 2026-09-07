package com.eazyfreight.logistics.controller;

import com.eazyfreight.logistics.api.LogisticsApi;
import com.eazyfreight.logistics.model.ActualCargo;
import com.eazyfreight.logistics.model.AwaitingDispatch;
import com.eazyfreight.logistics.model.DispatchTruck;
import com.eazyfreight.logistics.model.ExaminationHold;
import com.eazyfreight.logistics.model.ExaminationRelease;
import com.eazyfreight.logistics.model.ItnGateStatus;
import com.eazyfreight.logistics.model.LoadedOnVessel;
import com.eazyfreight.logistics.model.Logistics;
import com.eazyfreight.logistics.model.RecordContainerNumber;
import com.eazyfreight.logistics.model.RecordSeal;
import com.eazyfreight.logistics.model.TerminalGateReceipt;
import com.eazyfreight.logistics.model.TerminalGateRejection;
import com.eazyfreight.logistics.service.LogisticsService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Container and equipment endpoints, keyed by booking. */
@RestController
@RequestMapping("/api/logistics")
@RequiredArgsConstructor
public class LogisticsController implements LogisticsApi {

    private final LogisticsService logisticsService;

    @Override
    public ResponseEntity<List<Logistics>> getAll() {
        return ResponseEntity.ok(logisticsService.findAll().stream()
                .map(LogisticsApiMapper::toView).toList());
    }

    @Override
    public ResponseEntity<List<Logistics>> getBlockedOnItn() {
        return ResponseEntity.ok(logisticsService.findBlockedOnItn().stream()
                .map(LogisticsApiMapper::toView).toList());
    }

    @Override
    public ResponseEntity<List<AwaitingDispatch>> getAwaitingOutboundDispatch() {
        return ResponseEntity.ok(logisticsService.findAwaitingOutboundDispatch().stream()
                .map(LogisticsApiMapper::toAwaitingDispatch).toList());
    }

    @Override
    public ResponseEntity<List<Logistics>> getUnderExamination() {
        return ResponseEntity.ok(logisticsService.findUnderExamination().stream()
                .map(LogisticsApiMapper::toView).toList());
    }

    @Override
    public ResponseEntity<Logistics> getByBooking(UUID bookingId) {
        return ResponseEntity.ok(LogisticsApiMapper.toView(logisticsService.findByBooking(bookingId)));
    }

    /** 6. CheckITNGate — reports whether the inbound movement may be authorised. */
    @Override
    public ResponseEntity<ItnGateStatus> checkItnGate(UUID bookingId) {
        String blocked = logisticsService.checkItnGate(bookingId);
        ItnGateStatus status = new ItnGateStatus();
        status.setClear(blocked == null);
        status.setReason(blocked);
        return ResponseEntity.ok(status);
    }

    /** 1. DispatchOutboundTruck */
    @Override
    public ResponseEntity<Logistics> dispatchOutbound(UUID bookingId, DispatchTruck dispatchTruck, String xActor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(LogisticsApiMapper.toView(
                logisticsService.dispatchOutbound(bookingId, LogisticsApiMapper.toDispatchTruck(dispatchTruck), xActor)));
    }

    /** 2. RecordContainerNumber */
    @Override
    public ResponseEntity<Logistics> recordContainerNumber(
            UUID bookingId, RecordContainerNumber recordContainerNumber, String xActor) {
        return ResponseEntity.ok(LogisticsApiMapper.toView(logisticsService.recordContainerNumber(
                bookingId, LogisticsApiMapper.toRecordContainerNumber(recordContainerNumber), xActor)));
    }

    /** 3. RecordContainerDeliveredToCustomer */
    @Override
    public ResponseEntity<Logistics> recordDeliveredToCustomer(UUID bookingId) {
        return ResponseEntity.ok(LogisticsApiMapper.toView(logisticsService.recordDeliveredToCustomer(bookingId)));
    }

    /** 4. RecordCustomerLoadingComplete */
    @Override
    public ResponseEntity<Logistics> recordLoadingComplete(UUID bookingId) {
        return ResponseEntity.ok(LogisticsApiMapper.toView(logisticsService.recordLoadingComplete(bookingId)));
    }

    /** 5. RecordSealNumber */
    @Override
    public ResponseEntity<Logistics> recordSeal(UUID bookingId, RecordSeal recordSeal, String xActor) {
        return ResponseEntity.ok(LogisticsApiMapper.toView(logisticsService.recordSeal(
                bookingId, LogisticsApiMapper.toRecordSeal(recordSeal), xActor)));
    }

    /** 13. RecordSealReplacement — outside a CBP examination, e.g. a damaged seal. */
    @Override
    public ResponseEntity<Logistics> replaceSeal(UUID bookingId, RecordSeal recordSeal, String xActor) {
        return ResponseEntity.ok(LogisticsApiMapper.toView(logisticsService.replaceSeal(
                bookingId, LogisticsApiMapper.toRecordSeal(recordSeal), xActor)));
    }

    /** 7. DispatchInboundTruck — refused until the ITN gate is clear. */
    @Override
    public ResponseEntity<Logistics> dispatchInbound(UUID bookingId, DispatchTruck dispatchTruck, String xActor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(LogisticsApiMapper.toView(
                logisticsService.dispatchInbound(bookingId, LogisticsApiMapper.toDispatchTruck(dispatchTruck), xActor)));
    }

    /** 8. RecordLoadedContainerPickedUp */
    @Override
    public ResponseEntity<Logistics> recordLoadedContainerPickedUp(UUID bookingId) {
        return ResponseEntity.ok(LogisticsApiMapper.toView(logisticsService.recordLoadedContainerPickedUp(bookingId)));
    }

    /** 9. RecordContainerDeliveredToPort */
    @Override
    public ResponseEntity<Logistics> recordDeliveredToPort(UUID bookingId) {
        return ResponseEntity.ok(LogisticsApiMapper.toView(logisticsService.recordDeliveredToPort(bookingId)));
    }

    /** 10. RecordTerminalGateReceipt */
    @Override
    public ResponseEntity<Logistics> recordTerminalGateReceipt(UUID bookingId, TerminalGateReceipt terminalGateReceipt) {
        return ResponseEntity.ok(LogisticsApiMapper.toView(logisticsService.recordTerminalGateReceipt(
                bookingId, LogisticsApiMapper.toTerminalGateReceipt(terminalGateReceipt))));
    }

    /** 11. RecordTerminalGateRejection */
    @Override
    public ResponseEntity<Logistics> recordTerminalGateRejection(UUID bookingId, TerminalGateRejection terminalGateRejection) {
        return ResponseEntity.ok(LogisticsApiMapper.toView(logisticsService.recordTerminalGateRejection(
                bookingId, LogisticsApiMapper.toTerminalGateRejection(terminalGateRejection))));
    }

    /** 12. RecordCBPExaminationHold */
    @Override
    public ResponseEntity<Logistics> recordExaminationHold(UUID bookingId, ExaminationHold examinationHold) {
        return ResponseEntity.ok(LogisticsApiMapper.toView(logisticsService.recordExaminationHold(
                bookingId, LogisticsApiMapper.toExaminationHold(examinationHold))));
    }

    /** 14. RecordCBPExaminationRelease — always issues a replacement seal. */
    @Override
    public ResponseEntity<Logistics> recordExaminationRelease(
            UUID bookingId, ExaminationRelease examinationRelease, String xActor) {
        return ResponseEntity.ok(LogisticsApiMapper.toView(logisticsService.recordExaminationRelease(
                bookingId, LogisticsApiMapper.toExaminationRelease(examinationRelease), xActor)));
    }

    /** 15. RecordContainerLoadedOnVessel */
    @Override
    public ResponseEntity<Logistics> recordLoadedOnVessel(UUID bookingId, LoadedOnVessel loadedOnVessel) {
        return ResponseEntity.ok(LogisticsApiMapper.toView(logisticsService.recordLoadedOnVessel(
                bookingId, LogisticsApiMapper.toLoadedOnVessel(loadedOnVessel))));
    }

    /** 16. RecordVesselDeparture */
    @Override
    public ResponseEntity<Logistics> recordVesselDeparted(UUID bookingId) {
        return ResponseEntity.ok(LogisticsApiMapper.toView(logisticsService.recordVesselDeparted(bookingId)));
    }

    /** 17. RecordActualCargoDetails */
    @Override
    public ResponseEntity<Logistics> recordActualCargo(UUID bookingId, ActualCargo actualCargo, String xActor) {
        return ResponseEntity.ok(LogisticsApiMapper.toView(logisticsService.recordActualCargo(
                bookingId, LogisticsApiMapper.toActualCargo(actualCargo), xActor)));
    }
}
