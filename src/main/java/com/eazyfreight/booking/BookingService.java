package com.eazyfreight.booking;

import com.eazyfreight.common.ReferenceGenerator;
import com.eazyfreight.exception.BookingNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Application service for the Booking context.
 *
 * <p>Loads, delegates to a command method on {@link Booking}, saves. No branching on
 * status here — the aggregate owns the state machine.
 */
@Service
@RequiredArgsConstructor
public class BookingService {

    private static final List<BookingStatus> ACTIVE_CONFIRMED = List.of(
            BookingStatus.CONFIRMED_BY_CARRIER, BookingStatus.CUSTOMER_CONFIRMED);

    private final BookingRepository bookingRepository;
    private final ReferenceGenerator referenceGenerator;
    private final Clock clock;

    // ---------------------------------------------------------------- commands

    @Transactional
    public BookingResponse createBookingRequest(CreateBookingRequest request, String actor) {
        Booking booking = Booking.request(
                referenceGenerator.next(ReferenceGenerator.BOOKING_PREFIX),
                request.quoteId(),
                request.customerId(),
                request.shipperId(),
                request.consigneeId(),
                request.notifyPartyId(),
                request.alsoNotifyId(),
                request.shippingMode(),
                request.originPortCode(),
                request.destinationPortCode(),
                request.incoterms(),
                request.requestedEtd(),
                request.requestedEta(),
                request.transportRequired(),
                request.pickupAddress(),
                request.pickupDateTime(),
                request.specialInstructions(),
                request.marksAndNumbers(),
                LocalDate.now(clock),
                clock.instant(),
                actor
        );

        request.cargoDetails().forEach(detail -> booking.addCargoDetail(BookingCargoDetail.builder()
                .description(detail.description())
                .hsCode(detail.hsCode())
                .pieces(detail.pieces())
                .weightKg(detail.weightKg())
                .lengthCm(detail.lengthCm())
                .widthCm(detail.widthCm())
                .heightCm(detail.heightCm())
                .hazmat(detail.hazmat())
                .temperatureControlled(detail.temperatureControlled())
                .oversized(detail.oversized())
                .marksAndNumbers(detail.marksAndNumbers())
                .build()));

        return BookingResponse.fromEntity(bookingRepository.save(booking));
    }

    @Transactional
    public BookingResponse submitToCarrier(UUID bookingId, SubmitBookingRequest request, String actor) {
        Booking booking = getBookingOrThrow(bookingId);
        booking.submitTo(
                request.bookingSourceType(),
                request.carrierId(),
                request.containerType(),
                request.numberOfContainers(),
                clock.instant(),
                actor
        );
        return BookingResponse.fromEntity(bookingRepository.save(booking));
    }

    @Transactional
    public BookingResponse recordCarrierConfirmation(
            UUID bookingId, RecordCarrierConfirmationRequest request, String actor) {
        Booking booking = getBookingOrThrow(bookingId);
        booking.recordCarrierConfirmation(
                request.carrierBookingRef(),
                request.coLoaderBookingRef(),
                request.vesselName(),
                request.voyageNumber(),
                request.confirmedEtd(),
                request.confirmedEta(),
                request.containerType(),
                request.confirmedAt() == null ? clock.instant() : request.confirmedAt(),
                clock.instant(),
                actor
        );
        return BookingResponse.fromEntity(bookingRepository.save(booking));
    }

    @Transactional
    public BookingResponse recordCarrierRejection(
            UUID bookingId, RecordCarrierRejectionRequest request, String actor) {
        Booking booking = getBookingOrThrow(bookingId);
        booking.recordCarrierRejection(request.reason(), clock.instant(), actor);
        return BookingResponse.fromEntity(bookingRepository.save(booking));
    }

    @Transactional
    public BookingResponse recordCounterOffer(UUID bookingId, RecordCounterOfferRequest request, String actor) {
        Booking booking = getBookingOrThrow(bookingId);
        booking.recordCounterOffer(
                request.proposedVessel(),
                request.proposedVoyage(),
                request.proposedEtd(),
                request.proposedEta(),
                clock.instant(),
                actor
        );
        return BookingResponse.fromEntity(bookingRepository.save(booking));
    }

    @Transactional
    public BookingResponse acceptCounterOffer(UUID bookingId, String actor) {
        Booking booking = getBookingOrThrow(bookingId);
        booking.acceptCounterOffer(clock.instant(), actor);
        return BookingResponse.fromEntity(bookingRepository.save(booking));
    }

    @Transactional
    public BookingResponse rejectCounterOffer(UUID bookingId, String actor) {
        Booking booking = getBookingOrThrow(bookingId);
        booking.rejectCounterOffer(clock.instant(), actor);
        return BookingResponse.fromEntity(bookingRepository.save(booking));
    }

    @Transactional
    public BookingResponse acknowledgeEtdVariance(UUID bookingId, String actor) {
        Booking booking = getBookingOrThrow(bookingId);
        booking.acknowledgeEtdVariance(clock.instant(), actor);
        return BookingResponse.fromEntity(bookingRepository.save(booking));
    }

    @Transactional
    public BookingResponse sendConfirmationToCustomer(UUID bookingId, String actor) {
        Booking booking = getBookingOrThrow(bookingId);
        booking.sendConfirmationToCustomer(clock.instant(), actor);
        return BookingResponse.fromEntity(bookingRepository.save(booking));
    }

    @Transactional
    public BookingResponse generateTruckDeliveryOrder(
            UUID bookingId, GenerateTruckDeliveryOrderRequest request, String actor) {
        Booking booking = getBookingOrThrow(bookingId);
        booking.generateTruckDeliveryOrder(
                () -> referenceGenerator.next(ReferenceGenerator.TRUCK_DELIVERY_ORDER_PREFIX),
                request.deliveryAddress(),
                clock.instant(),
                actor
        );
        return BookingResponse.fromEntity(bookingRepository.save(booking));
    }

    @Transactional
    public BookingResponse dispatchTruckDeliveryOrder(
            UUID bookingId, DispatchTruckDeliveryOrderRequest request, String actor) {
        Booking booking = getBookingOrThrow(bookingId);
        booking.dispatchTruckDeliveryOrder(
                request.driverId(), request.truckingVendorId(), clock.instant(), actor);
        return BookingResponse.fromEntity(bookingRepository.save(booking));
    }

    @Transactional
    public BookingResponse recordVesselOverbooking(UUID bookingId, String actor) {
        Booking booking = getBookingOrThrow(bookingId);
        booking.recordVesselOverbooking(clock.instant(), actor);
        return BookingResponse.fromEntity(bookingRepository.save(booking));
    }

    @Transactional
    public BookingResponse reinstate(UUID bookingId, ReinstateBookingRequest request, String actor) {
        Booking booking = getBookingOrThrow(bookingId);
        booking.reinstate(
                request.newVesselName(),
                request.newVoyageNumber(),
                request.newEtd(),
                request.newEta(),
                request.reason() == null ? "VesselOverbooked" : request.reason(),
                LocalDate.now(clock),
                clock.instant(),
                actor
        );
        return BookingResponse.fromEntity(bookingRepository.save(booking));
    }

    @Transactional
    public BookingResponse cancel(UUID bookingId, CancelBookingRequest request, String actor) {
        Booking booking = getBookingOrThrow(bookingId);
        booking.cancel(request.reason(), request.initiatedBy(), clock.instant(), actor);
        return BookingResponse.fromEntity(bookingRepository.save(booking));
    }

    @Transactional
    public BookingResponse markItnFiled(UUID bookingId, String actor) {
        Booking booking = getBookingOrThrow(bookingId);
        booking.markItnFiled(clock.instant(), actor);
        return BookingResponse.fromEntity(bookingRepository.save(booking));
    }

    // ----------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public List<BookingResponse> findAll() {
        return bookingRepository.findAll().stream().map(BookingResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public BookingResponse findById(UUID bookingId) {
        return BookingResponse.fromEntity(getBookingOrThrow(bookingId));
    }

    @Transactional(readOnly = true)
    public BookingResponse findByReference(String bookingReference) {
        return bookingRepository.findByBookingReference(bookingReference)
                .map(BookingResponse::fromEntity)
                .orElseThrow(() -> new BookingNotFoundException(bookingReference));
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> findByCustomer(UUID customerId, BookingStatus status) {
        List<Booking> bookings = status == null
                ? bookingRepository.findByCustomerId(customerId)
                : bookingRepository.findByCustomerIdAndStatus(customerId, status);
        return bookings.stream().map(BookingResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> findByCarrier(UUID carrierId) {
        return bookingRepository.findByCarrierBooking_CarrierId(carrierId).stream()
                .map(BookingResponse::fromEntity)
                .toList();
    }

    /** Submitted to a carrier, still waiting on an answer. */
    @Transactional(readOnly = true)
    public List<BookingResponse> findPendingCarrierConfirmation() {
        return bookingRepository.findByStatusIn(
                        List.of(BookingStatus.SUBMITTED_TO_CARRIER, BookingStatus.COUNTER_OFFER_RECEIVED)).stream()
                .map(BookingResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> findApproachingEtd(int withinDays) {
        LocalDate cutoff = LocalDate.now(clock).plusDays(withinDays);
        return bookingRepository
                .findByStatusInAndCarrierBooking_ConfirmedEtdLessThanEqual(ACTIVE_CONFIRMED, cutoff).stream()
                .map(BookingResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> findAwaitingTruckDispatch() {
        return bookingRepository
                .findByStatusInAndTransportRequiredTrueAndTruckDeliveryOrderIsNull(ACTIVE_CONFIRMED).stream()
                .map(BookingResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BookingStatusHistoryResponse> findStatusHistory(UUID bookingId) {
        return getBookingOrThrow(bookingId).getStatusHistory().stream()
                .map(BookingStatusHistoryResponse::fromEntity)
                .toList();
    }

    private Booking getBookingOrThrow(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
    }
}
