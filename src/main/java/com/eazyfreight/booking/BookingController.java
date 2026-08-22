package com.eazyfreight.booking;

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
import org.springframework.web.bind.annotation.RequestParam;
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
public class BookingController {

    private static final String ACTOR_HEADER = "X-Actor";
    private static final String DEFAULT_ACTOR = "operations";

    private final BookingService bookingService;

    @GetMapping
    public List<BookingResponse> getAll() {
        return bookingService.findAll();
    }

    @GetMapping("/{id}")
    public BookingResponse getById(@PathVariable UUID id) {
        return bookingService.findById(id);
    }

    @GetMapping("/by-reference/{bookingReference}")
    public BookingResponse getByReference(@PathVariable String bookingReference) {
        return bookingService.findByReference(bookingReference);
    }

    @GetMapping("/{id}/status-history")
    public List<BookingStatusHistoryResponse> getStatusHistory(@PathVariable UUID id) {
        return bookingService.findStatusHistory(id);
    }

    @GetMapping("/by-customer/{customerId}")
    public List<BookingResponse> getByCustomer(
            @PathVariable UUID customerId,
            @RequestParam(required = false) BookingStatus status) {
        return bookingService.findByCustomer(customerId, status);
    }

    @GetMapping("/by-carrier/{carrierId}")
    public List<BookingResponse> getByCarrier(@PathVariable UUID carrierId) {
        return bookingService.findByCarrier(carrierId);
    }

    @GetMapping("/pending-carrier-confirmation")
    public List<BookingResponse> getPendingCarrierConfirmation() {
        return bookingService.findPendingCarrierConfirmation();
    }

    @GetMapping("/approaching-etd")
    public List<BookingResponse> getApproachingEtd(@RequestParam(defaultValue = "7") int withinDays) {
        return bookingService.findApproachingEtd(withinDays);
    }

    @GetMapping("/awaiting-truck-dispatch")
    public List<BookingResponse> getAwaitingTruckDispatch() {
        return bookingService.findAwaitingTruckDispatch();
    }

    @PostMapping
    public ResponseEntity<BookingResponse> create(
            @Valid @RequestBody CreateBookingRequest request,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        BookingResponse response = bookingService.createBookingRequest(request, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/submit")
    public BookingResponse submit(
            @PathVariable UUID id,
            @Valid @RequestBody SubmitBookingRequest request,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return bookingService.submitToCarrier(id, request, actor);
    }

    @PostMapping("/{id}/carrier-confirmation")
    public BookingResponse recordCarrierConfirmation(
            @PathVariable UUID id,
            @Valid @RequestBody RecordCarrierConfirmationRequest request,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return bookingService.recordCarrierConfirmation(id, request, actor);
    }

    @PostMapping("/{id}/carrier-rejection")
    public BookingResponse recordCarrierRejection(
            @PathVariable UUID id,
            @Valid @RequestBody RecordCarrierRejectionRequest request,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return bookingService.recordCarrierRejection(id, request, actor);
    }

    @PostMapping("/{id}/counter-offer")
    public BookingResponse recordCounterOffer(
            @PathVariable UUID id,
            @Valid @RequestBody RecordCounterOfferRequest request,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return bookingService.recordCounterOffer(id, request, actor);
    }

    @PostMapping("/{id}/counter-offer/accept")
    public BookingResponse acceptCounterOffer(
            @PathVariable UUID id,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return bookingService.acceptCounterOffer(id, actor);
    }

    @PostMapping("/{id}/counter-offer/reject")
    public BookingResponse rejectCounterOffer(
            @PathVariable UUID id,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return bookingService.rejectCounterOffer(id, actor);
    }

    @PostMapping("/{id}/acknowledge-etd-variance")
    public BookingResponse acknowledgeEtdVariance(
            @PathVariable UUID id,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return bookingService.acknowledgeEtdVariance(id, actor);
    }

    @PostMapping("/{id}/send-confirmation")
    public BookingResponse sendConfirmation(
            @PathVariable UUID id,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return bookingService.sendConfirmationToCustomer(id, actor);
    }

    @PostMapping("/{id}/truck-delivery-order")
    public BookingResponse generateTruckDeliveryOrder(
            @PathVariable UUID id,
            @Valid @RequestBody GenerateTruckDeliveryOrderRequest request,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return bookingService.generateTruckDeliveryOrder(id, request, actor);
    }

    @PostMapping("/{id}/truck-delivery-order/dispatch")
    public BookingResponse dispatchTruckDeliveryOrder(
            @PathVariable UUID id,
            @RequestBody DispatchTruckDeliveryOrderRequest request,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return bookingService.dispatchTruckDeliveryOrder(id, request, actor);
    }

    @PostMapping("/{id}/vessel-overbooking")
    public BookingResponse recordVesselOverbooking(
            @PathVariable UUID id,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return bookingService.recordVesselOverbooking(id, actor);
    }

    @PostMapping("/{id}/reinstate")
    public BookingResponse reinstate(
            @PathVariable UUID id,
            @Valid @RequestBody ReinstateBookingRequest request,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return bookingService.reinstate(id, request, actor);
    }

    @PostMapping("/{id}/cancel")
    public BookingResponse cancel(
            @PathVariable UUID id,
            @Valid @RequestBody CancelBookingRequest request,
            @RequestHeader(value = ACTOR_HEADER, defaultValue = DEFAULT_ACTOR) String actor) {
        return bookingService.cancel(id, request, actor);
    }
}
