package com.eazyfreight.booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    Optional<Booking> findByBookingReference(String bookingReference);

    List<Booking> findByCustomerId(UUID customerId);

    List<Booking> findByCustomerIdAndStatus(UUID customerId, BookingStatus status);

    List<Booking> findByStatus(BookingStatus status);

    List<Booking> findByStatusIn(List<BookingStatus> statuses);

    List<Booking> findByCarrierBooking_CarrierId(UUID carrierId);

    /** Confirmed bookings sailing on or before the given date. */
    List<Booking> findByStatusInAndCarrierBooking_ConfirmedEtdLessThanEqual(
            List<BookingStatus> statuses, LocalDate etd);

    /** Confirmed bookings where Eazy Freight arranges the trucking. */
    List<Booking> findByStatusInAndTransportRequiredTrue(List<BookingStatus> statuses);

    List<Booking> findByStatusAndCancelledAtIsNotNull(BookingStatus status);
}
