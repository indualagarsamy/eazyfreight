package com.eazyfreight.booking.controller;

import com.eazyfreight.booking.api.BookingApi;
import com.eazyfreight.booking.model.BookingResponse;
import com.eazyfreight.booking.model.BookingStatus;
import com.eazyfreight.booking.model.BookingStatusHistoryResponse;
import com.eazyfreight.booking.model.CancelBookingRequest;
import com.eazyfreight.booking.model.CreateBookingRequest;
import com.eazyfreight.booking.model.RecordCarrierConfirmationRequest;
import com.eazyfreight.booking.model.RecordCarrierRejectionRequest;
import com.eazyfreight.booking.model.RecordCounterOfferRequest;
import com.eazyfreight.booking.model.ReinstateBookingRequest;
import com.eazyfreight.booking.model.SubmitBookingRequest;
import com.eazyfreight.booking.service.BookingService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Booking endpoints.
 *
 * <p>There is no PUT. Every change is a named command against the aggregate, which
 * is what makes the status history meaningful — a caller cannot set
 * {@code status = "CONFIRMED"} directly, only ask the booking to record a carrier
 * confirmation and let it decide whether that is legal.
 *
 * <p>The {@code X-Actor} header names who is acting; it is written into the audit
 * trail. A real deployment would take this from the authenticated principal.
 */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController implements BookingApi {

    private final BookingService bookingService;

    @Override
    @RequestMapping(method = RequestMethod.GET, value = {"", "/"}, produces = "application/json")
    public ResponseEntity<List<BookingResponse>> getAllBookings() {
        return ResponseEntity.ok(bookingService.findAll().stream().map(BookingApiMapper::toModel).toList());
    }

    @Override
    public ResponseEntity<BookingResponse> getBookingById(UUID id) {
        return ResponseEntity.ok(BookingApiMapper.toModel(bookingService.findById(id)));
    }

    @Override
    public ResponseEntity<BookingResponse> getBookingByReference(String bookingReference) {
        return ResponseEntity.ok(BookingApiMapper.toModel(bookingService.findByReference(bookingReference)));
    }

    @Override
    public ResponseEntity<List<BookingStatusHistoryResponse>> getBookingStatusHistory(UUID id) {
        return ResponseEntity.ok(bookingService.findStatusHistory(id).stream().map(BookingApiMapper::toModel).toList());
    }

    @Override
    public ResponseEntity<List<BookingResponse>> getBookingsByCustomer(UUID customerId, BookingStatus status) {
        return ResponseEntity.ok(bookingService.findByCustomer(customerId, BookingApiMapper.toDomain(status)).stream()
                .map(BookingApiMapper::toModel).toList());
    }

    @Override
    public ResponseEntity<List<BookingResponse>> getBookingsByCarrier(UUID carrierId) {
        return ResponseEntity.ok(bookingService.findByCarrier(carrierId).stream().map(BookingApiMapper::toModel).toList());
    }

    @Override
    public ResponseEntity<List<BookingResponse>> getPendingCarrierConfirmation() {
        return ResponseEntity.ok(bookingService.findPendingCarrierConfirmation().stream()
                .map(BookingApiMapper::toModel).toList());
    }

    @Override
    public ResponseEntity<List<BookingResponse>> getApproachingEtd(Integer withinDays) {
        return ResponseEntity.ok(bookingService.findApproachingEtd(withinDays).stream()
                .map(BookingApiMapper::toModel).toList());
    }

    @Override
    @RequestMapping(method = RequestMethod.POST, value = {"", "/"}, consumes = "application/json", produces = "application/json")
    public ResponseEntity<BookingResponse> createBooking(CreateBookingRequest createBookingRequest, String xActor) {
        BookingResponse response = BookingApiMapper.toModel(
                bookingService.createBookingRequest(BookingApiMapper.toDto(createBookingRequest), xActor));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<BookingResponse> submitBooking(UUID id, SubmitBookingRequest submitBookingRequest, String xActor) {
        return ResponseEntity.ok(BookingApiMapper.toModel(
                bookingService.submitToCarrier(id, BookingApiMapper.toDto(submitBookingRequest), xActor)));
    }

    @Override
    public ResponseEntity<BookingResponse> recordCarrierConfirmation(
            UUID id, RecordCarrierConfirmationRequest recordCarrierConfirmationRequest, String xActor) {
        return ResponseEntity.ok(BookingApiMapper.toModel(bookingService.recordCarrierConfirmation(
                id, BookingApiMapper.toDto(recordCarrierConfirmationRequest), xActor)));
    }

    @Override
    public ResponseEntity<BookingResponse> recordCarrierRejection(
            UUID id, RecordCarrierRejectionRequest recordCarrierRejectionRequest, String xActor) {
        return ResponseEntity.ok(BookingApiMapper.toModel(bookingService.recordCarrierRejection(
                id, BookingApiMapper.toDto(recordCarrierRejectionRequest), xActor)));
    }

    @Override
    public ResponseEntity<BookingResponse> recordCounterOffer(
            UUID id, RecordCounterOfferRequest recordCounterOfferRequest, String xActor) {
        return ResponseEntity.ok(BookingApiMapper.toModel(bookingService.recordCounterOffer(
                id, BookingApiMapper.toDto(recordCounterOfferRequest), xActor)));
    }

    @Override
    public ResponseEntity<BookingResponse> acceptCounterOffer(UUID id, String xActor) {
        return ResponseEntity.ok(BookingApiMapper.toModel(bookingService.acceptCounterOffer(id, xActor)));
    }

    @Override
    public ResponseEntity<BookingResponse> rejectCounterOffer(UUID id, String xActor) {
        return ResponseEntity.ok(BookingApiMapper.toModel(bookingService.rejectCounterOffer(id, xActor)));
    }

    @Override
    public ResponseEntity<BookingResponse> acknowledgeEtdVariance(UUID id, String xActor) {
        return ResponseEntity.ok(BookingApiMapper.toModel(bookingService.acknowledgeEtdVariance(id, xActor)));
    }

    @Override
    public ResponseEntity<BookingResponse> sendConfirmation(UUID id, String xActor) {
        return ResponseEntity.ok(BookingApiMapper.toModel(bookingService.sendConfirmationToCustomer(id, xActor)));
    }

    @Override
    public ResponseEntity<BookingResponse> recordVesselOverbooking(UUID id, String xActor) {
        return ResponseEntity.ok(BookingApiMapper.toModel(bookingService.recordVesselOverbooking(id, xActor)));
    }

    @Override
    public ResponseEntity<BookingResponse> reinstateBooking(UUID id, ReinstateBookingRequest reinstateBookingRequest, String xActor) {
        return ResponseEntity.ok(BookingApiMapper.toModel(
                bookingService.reinstate(id, BookingApiMapper.toDto(reinstateBookingRequest), xActor)));
    }

    @Override
    public ResponseEntity<BookingResponse> cancelBooking(UUID id, CancelBookingRequest cancelBookingRequest, String xActor) {
        return ResponseEntity.ok(BookingApiMapper.toModel(
                bookingService.cancel(id, BookingApiMapper.toDto(cancelBookingRequest), xActor)));
    }
}
