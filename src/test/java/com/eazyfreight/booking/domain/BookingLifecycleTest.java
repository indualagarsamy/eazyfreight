package com.eazyfreight.booking.domain;

import com.eazyfreight.booking.event.BookingEvent;

import com.eazyfreight.exception.DomainRuleViolationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the Booking state machine — the gates and the audit trail that the
 * monolith's field-update methods had no equivalent of.
 */
class BookingLifecycleTest {

    private static final LocalDate TODAY = LocalDate.of(2024, 3, 15);   // a Friday
    private static final Instant NOW = Instant.parse("2024-03-15T10:00:00Z");
    private static final String ACTOR = "ops.jane";

    @Test
    void aCarrierReferenceCannotBeRecordedBeforeSubmission() {
        Booking booking = requestedBooking(false);

        assertThatThrownBy(() -> booking.recordCarrierConfirmation(
                "MAEU123", null, "MAERSK SEALAND", "024W",
                TODAY.plusDays(20), TODAY.plusDays(45), null, NOW, NOW, ACTOR))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("Only a submitted booking");
    }

    @Test
    void cargoExceedingTheContainerPayloadIsRejectedAtSubmission() {
        Booking booking = requestedBooking(false, new BigDecimal("30000"), ShippingMode.OCEAN_FCL);

        assertThatThrownBy(() -> booking.submitTo(BookingSourceType.DIRECT_CARRIER, UUID.randomUUID(),
                ContainerType.TWENTY_GP, 1, NOW, ACTOR))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("exceeds the 20GP payload limit");
    }

    @Test
    void aCoLoaderBookingMustCarryItsOwnReference() {
        Booking booking = requestedBooking(false);
        booking.submitTo(BookingSourceType.CO_LOADER, UUID.randomUUID(), null, null, NOW, ACTOR);

        assertThatThrownBy(() -> booking.recordCarrierConfirmation(
                "MAEU123", null, "MAERSK SEALAND", "024W",
                TODAY.plusDays(20), TODAY.plusDays(45), null, NOW, NOW, ACTOR))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("coLoaderBookingRef");
    }

    @Test
    void aMaterialEtdChangeBlocksTheConfirmationUntilTheCustomerIsTold() {
        Booking booking = confirmedBooking(TODAY.plusDays(40));   // requested was +20 days

        assertThat(booking.requiresCustomerEtdNotification()).isTrue();
        assertThatThrownBy(() -> booking.sendConfirmationToCustomer(NOW, ACTOR))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("notify the customer");

        booking.acknowledgeEtdVariance(NOW, ACTOR);
        booking.sendConfirmationToCustomer(NOW, ACTOR);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CUSTOMER_CONFIRMED);
    }

    @Test
    void anEtdWithinToleranceSendsWithoutAnAcknowledgement() {
        Booking booking = confirmedBooking(TODAY.plusDays(21));   // one business day out

        assertThat(booking.requiresCustomerEtdNotification()).isFalse();
        booking.sendConfirmationToCustomer(NOW, ACTOR);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CUSTOMER_CONFIRMED);
    }

    @Test
    void reinstatementKeepsTheBookingIdentityAndRemembersTheSailingItLeft() {
        Booking booking = confirmedBooking(TODAY.plusDays(20));
        UUID originalId = booking.getId();
        String originalReference = booking.getBookingReference();

        booking.recordVesselOverbooking(NOW, ACTOR);
        booking.reinstate("MAERSK KOWLOON", "026W", TODAY.plusDays(34), TODAY.plusDays(60),
                "VesselOverbooked", TODAY, NOW, ACTOR);

        assertThat(booking.getId()).isEqualTo(originalId);
        assertThat(booking.getBookingReference()).isEqualTo(originalReference);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED_BY_CARRIER);

        assertThat(booking.getReinstatements()).hasSize(1);
        BookingReinstatement reinstatement = booking.getReinstatements().get(0);
        assertThat(reinstatement.getPreviousVessel()).isEqualTo("MAERSK SEALAND");
        assertThat(reinstatement.getPreviousEtd()).isEqualTo(TODAY.plusDays(20));
        assertThat(reinstatement.getNewVesselName()).isEqualTo("MAERSK KOWLOON");
        assertThat(reinstatement.getNewEtd()).isEqualTo(TODAY.plusDays(34));

        // The carrier booking now points at the new sailing, but the old one survives above.
        assertThat(booking.getCarrierBooking().getVesselName()).isEqualTo("MAERSK KOWLOON");
        assertThat(booking.getCarrierBooking().getCarrierBookingRef()).isEqualTo("MAEU123");
    }

    @Test
    void reinstatementRaisesAnItnAmendmentWhenComplianceHasAlreadyFiled() {
        Booking booking = confirmedBooking(TODAY.plusDays(20));
        booking.markItnFiled(NOW, "compliance.sam");
        booking.recordVesselOverbooking(NOW, ACTOR);

        booking.reinstate("MAERSK KOWLOON", "026W", TODAY.plusDays(34), TODAY.plusDays(60),
                "VesselOverbooked", TODAY, NOW, ACTOR);

        assertThat(booking.pendingEvents())
                .anyMatch(BookingEvent.ItnAmendmentRequired.class::isInstance);
    }

    @Test
    void aBookingThatWasNotOverbookedCannotBeReinstated() {
        Booking booking = confirmedBooking(TODAY.plusDays(20));
        booking.cancel("Customer changed plans", CancellationInitiator.CUSTOMER, NOW, ACTOR);

        assertThatThrownBy(() -> booking.reinstate("MAERSK KOWLOON", "026W",
                TODAY.plusDays(34), TODAY.plusDays(60), "VesselOverbooked", TODAY, NOW, ACTOR))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("overbooked by the carrier");
    }

    @Test
    void everyTransitionIsRecordedWithActorAndReason() {
        Booking booking = confirmedBooking(TODAY.plusDays(20));

        assertThat(booking.getStatusHistory())
                .extracting(BookingStatusHistory::getSequenceNumber)
                .containsExactly(1, 2, 3);

        assertThat(booking.getStatusHistory())
                .extracting(BookingStatusHistory::getToStatus)
                .containsExactly(
                        BookingStatus.BOOKING_REQUESTED,
                        BookingStatus.SUBMITTED_TO_CARRIER,
                        BookingStatus.CONFIRMED_BY_CARRIER);

        assertThat(booking.getStatusHistory())
                .allSatisfy(entry -> {
                    assertThat(entry.getChangedBy()).isNotBlank();
                    assertThat(entry.getChangedAt()).isNotNull();
                    assertThat(entry.getSource()).isNotNull();
                });

        BookingStatusHistory confirmation = booking.getStatusHistory().get(2);
        assertThat(confirmation.getFromStatus()).isEqualTo(BookingStatus.SUBMITTED_TO_CARRIER);
        assertThat(confirmation.getReason()).contains("MAEU123");
    }

    @Test
    void aCancellationRecordsWhoCausedIt() {
        Booking booking = confirmedBooking(TODAY.plusDays(20));

        booking.cancel("Carrier could not accommodate", CancellationInitiator.CARRIER_OVERBOOKING, NOW, ACTOR);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getCancellationInitiatedBy()).isEqualTo(CancellationInitiator.CARRIER_OVERBOOKING);
        assertThat(booking.getCancellationReason()).isEqualTo("Carrier could not accommodate");
        assertThat(booking.getCancelledAt()).isEqualTo(NOW);
    }

    @Test
    void anEtdInsideTheFiveBusinessDayFloorIsRejected() {
        assertThatThrownBy(() -> Booking.request("EF-2024-00001", null, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), null, null, ShippingMode.OCEAN_LCL,
                "INBOM", "SGSIN", "FOB", TODAY.plusDays(2), null, false, null, null, null, null,
                TODAY, NOW, ACTOR))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("5 business days");
    }

    // ----------------------------------------------------------------- fixtures

    private Booking requestedBooking(boolean transportRequired) {
        return requestedBooking(transportRequired, new BigDecimal("500"), ShippingMode.OCEAN_LCL);
    }

    private Booking requestedBooking(boolean transportRequired, BigDecimal weightKg, ShippingMode mode) {
        Booking booking = Booking.request(
                "EF-2024-00287", null, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, null, mode, "INBOM", "SGSIN", "FOB",
                TODAY.plusDays(20), TODAY.plusDays(45), transportRequired,
                transportRequired ? "14 B Sylvan Way, Parsippany NJ" : null,
                null, null, null, TODAY, NOW, ACTOR);

        booking.addCargoDetail(BookingCargoDetail.builder()
                .description("Machine parts")
                .hsCode("8471.30.0100")
                .pieces(10)
                .weightKg(weightKg)
                .valueUsd(new BigDecimal("50000"))
                .lengthCm(new BigDecimal("100"))
                .widthCm(new BigDecimal("100"))
                .heightCm(new BigDecimal("100"))
                .hazmat(false)
                .temperatureControlled(false)
                .oversized(false)
                .build());
        return booking;
    }

    private Booking confirmedBooking(LocalDate confirmedEtd) {
        Booking booking = requestedBooking(true);
        booking.submitTo(BookingSourceType.DIRECT_CARRIER, UUID.randomUUID(), null, null, NOW, ACTOR);
        booking.recordCarrierConfirmation("MAEU123", null, "MAERSK SEALAND", "024W",
                confirmedEtd, confirmedEtd.plusDays(25), null, NOW, NOW, ACTOR);
        return booking;
    }
}
