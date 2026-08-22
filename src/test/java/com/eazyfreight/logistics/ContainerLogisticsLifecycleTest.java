package com.eazyfreight.logistics;

import com.eazyfreight.booking.ContainerType;
import com.eazyfreight.exception.DomainRuleViolationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests for the container logistics state machine. */
class ContainerLogisticsLifecycleTest {

    private static final Instant NOW = Instant.parse("2026-08-22T10:00:00Z");
    private static final UUID BOOKING = UUID.randomUUID();
    private static final UUID DRIVER = UUID.randomUUID();
    private static final String ACTOR = "ops.jane";

    @Test
    void theTwoTruckMovementsAreSeparateRecords() {
        ContainerAssignment assignment = sealed();
        assignment.recordItnReceived("X99999999000001", NOW);
        assignment.dispatchInbound("TDO-2-IN", DRIVER, null, null,
                "Customer premises", "Port terminal", NOW, NOW, NOW, ACTOR);

        assertThat(assignment.getDispatches()).hasSize(2);
        assertThat(assignment.getDispatches())
                .extracting(TruckDispatch::getMovementType)
                .containsExactly(MovementType.OUTBOUND, MovementType.INBOUND);
        // Distinct references, so the two movements are individually traceable.
        assertThat(assignment.outboundDispatch().orElseThrow().getTdoReference())
                .isNotEqualTo(assignment.inboundDispatch().orElseThrow().getTdoReference());
    }

    @Test
    void theInboundTruckIsRefusedUntilTheItnArrives() {
        ContainerAssignment assignment = sealed();

        assertThat(assignment.inboundBlockedReason()).contains("ITN not yet received");
        assertThatThrownBy(() -> assignment.dispatchInbound("TDO-2-IN", DRIVER, null, null,
                "Customer premises", "Port terminal", NOW, NOW, NOW, ACTOR))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("ITN not yet received");
        assertThat(assignment.pendingEvents())
                .anyMatch(LogisticsEvent.InboundDispatchBlocked.class::isInstance);

        assignment.recordItnReceived("X99999999000001", NOW);

        assertThat(assignment.inboundBlockedReason()).isNull();
        assignment.dispatchInbound("TDO-2-IN", DRIVER, null, null,
                "Customer premises", "Port terminal", NOW, NOW, NOW, ACTOR);
        assertThat(assignment.getStage()).isEqualTo(LogisticsStage.INBOUND_DISPATCHED);
    }

    @Test
    void aContainerNumberIsAssignedOnceAndNeverChanged() {
        ContainerAssignment assignment = dispatched();
        assignment.recordContainerNumber("MSCU1234567", ContainerType.FORTY_HC,
                ContainerSource.CARRIER_YARD, NOW, ACTOR);

        assertThat(assignment.getContainerNumber()).isEqualTo("MSCU1234567");
        assertThatThrownBy(() -> assignment.recordContainerNumber("MSCU7654321", null,
                ContainerSource.MANUAL_ENTRY, NOW, ACTOR))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("already assigned");
    }

    @Test
    void aCustomsResealKeepsTheOriginalSealOnRecord() {
        ContainerAssignment assignment = sealed();
        SealRecord original = assignment.activeSeal().orElseThrow();

        SealRecord previous = assignment.deactivateActiveSeal(
                SealDeactivationReason.CUSTOMS_INSPECTION, NOW);
        assignment.attachReplacementSeal(previous, "CBP778899",
                SealSource.CUSTOMS_ISSUED, NOW, "cbp.officer");

        assertThat(assignment.getSealRecords()).hasSize(2);
        assertThat(assignment.activeSeal().orElseThrow().getSealNumber()).isEqualTo("CBP778899");
        assertThat(assignment.activeSeal().orElseThrow().getSealSource())
                .isEqualTo(SealSource.CUSTOMS_ISSUED);

        // The number that was on the container before customs cut it is still there.
        assertThat(original.getSealNumber()).isEqualTo("SEAL123456");
        assertThat(original.isActive()).isFalse();
        assertThat(original.getDeactivationReason())
                .isEqualTo(SealDeactivationReason.CUSTOMS_INSPECTION);
        assertThat(original.getReplacedBySealId())
                .isEqualTo(assignment.activeSeal().orElseThrow().getId());
    }

    @Test
    void onlyOneSealIsEverActive() {
        ContainerAssignment assignment = sealed();
        SealRecord previous = assignment.deactivateActiveSeal(
                SealDeactivationReason.DAMAGED_SEAL, NOW);
        assignment.attachReplacementSeal(previous, "SEAL999", SealSource.CUSTOMER_ISSUED, NOW, ACTOR);

        assertThat(assignment.getSealRecords()).filteredOn(SealRecord::isActive).hasSize(1);
    }

    @Test
    void sealingCannotHappenTwiceWithoutAReplacement() {
        ContainerAssignment assignment = sealed();
        assertThatThrownBy(() -> assignment.recordSealNumber("SEAL999", NOW, ACTOR))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("already on the container");
    }

    @Test
    void earlyDeliveryToTheTerminalFlagsStorageFees() {
        ContainerAssignment assignment = atTerminalReady();

        // Accepted 22 Aug, but the terminal will not take it free until 25 Aug.
        assignment.recordTerminalGateReceipt("GR-99887", "APM Terminals",
                LocalDate.of(2026, 8, 25), LocalDate.of(2026, 9, 1),
                new BigDecimal("150.00"), NOW);

        TerminalAcceptance acceptance = assignment.getTerminalAcceptance();
        assertThat(acceptance.isStorageFeeApplies()).isTrue();
        assertThat(acceptance.getDaysEarly()).isEqualTo(3);
        assertThat(acceptance.estimatedStorageFee()).isEqualByComparingTo("450.00");
    }

    @Test
    void onTimeDeliveryIncursNoStorageFee() {
        ContainerAssignment assignment = atTerminalReady();
        assignment.recordTerminalGateReceipt("GR-99887", "APM Terminals",
                LocalDate.of(2026, 8, 20), LocalDate.of(2026, 9, 1),
                new BigDecimal("150.00"), NOW);

        assertThat(assignment.getTerminalAcceptance().isStorageFeeApplies()).isFalse();
        assertThat(assignment.getTerminalAcceptance().estimatedStorageFee())
                .isEqualByComparingTo("0");
    }

    @Test
    void actualCargoDivergingFromTheBookingObligesAnEeiAmendment() {
        ContainerAssignment assignment = sealed();
        assignment.recordItnReceived("X99999999000001", NOW);

        // Booked 500 kg, loaded 620 kg — well past the 5% threshold.
        assignment.recordActualCargo(new BigDecimal("620"), 10, new BigDecimal("10"),
                new BigDecimal("500"), 10, new BigDecimal("10"), NOW, ACTOR);

        assertThat(assignment.getActualCargoDetails().divergesMaterially()).isTrue();
        assertThat(assignment.pendingEvents())
                .anyMatch(LogisticsEvent.AesAmendmentRequired.class::isInstance);
    }

    @Test
    void actualCargoWithinToleranceRaisesNoAmendment() {
        ContainerAssignment assignment = sealed();
        assignment.recordItnReceived("X99999999000001", NOW);

        assignment.recordActualCargo(new BigDecimal("505"), 10, new BigDecimal("10"),
                new BigDecimal("500"), 10, new BigDecimal("10"), NOW, ACTOR);

        assertThat(assignment.getActualCargoDetails().divergesMaterially()).isFalse();
        assertThat(assignment.pendingEvents())
                .noneMatch(LogisticsEvent.AesAmendmentRequired.class::isInstance);
    }

    @Test
    void documentationPreconditionsNeedContainerSealAndItn() {
        ContainerAssignment assignment = dispatched();
        assertThat(assignment.documentationPreconditionsMet()).isFalse();

        assignment.recordContainerNumber("MSCU1234567", ContainerType.FORTY_HC,
                ContainerSource.CARRIER_YARD, NOW, ACTOR);
        assignment.recordDeliveredToCustomer(NOW);
        assignment.recordLoadingComplete(NOW);
        assertThat(assignment.documentationPreconditionsMet()).isFalse();

        assignment.recordSealNumber("SEAL123456", NOW, ACTOR);
        assertThat(assignment.documentationPreconditionsMet()).isFalse();

        assignment.recordItnReceived("X99999999000001", NOW);
        assertThat(assignment.documentationPreconditionsMet()).isTrue();
        assertThat(assignment.pendingEvents())
                .anyMatch(LogisticsEvent.DocumentationPreconditionsMet.class::isInstance);
    }

    @Test
    void aVendorDispatchMustCarryAVehicleReferenceForAudit() {
        ContainerAssignment assignment = ContainerAssignment.open(
                BOOKING, ContainerType.FORTY_HC, NOW);

        assertThatThrownBy(() -> assignment.dispatchOutbound("TDO-1-OUT", null,
                UUID.randomUUID(), null, "Carrier yard", "Customer premises", NOW, NOW, NOW, ACTOR))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("vehicle reference");
    }

    @Test
    void aDispatchNeedsEitherADriverOrAVendor() {
        ContainerAssignment assignment = ContainerAssignment.open(
                BOOKING, ContainerType.FORTY_HC, NOW);

        assertThatThrownBy(() -> assignment.dispatchOutbound("TDO-1-OUT", null, null, null,
                "Carrier yard", "Customer premises", NOW, NOW, NOW, ACTOR))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("driver or a trucking vendor");
    }

    // ----------------------------------------------------------------- fixtures

    private ContainerAssignment dispatched() {
        ContainerAssignment assignment = ContainerAssignment.open(BOOKING, ContainerType.FORTY_HC, NOW);
        assignment.dispatchOutbound("TDO-1-OUT", DRIVER, null, null,
                "Carrier yard, Nhava Sheva", "Plot 14, MIDC Andheri", NOW, NOW, NOW, ACTOR);
        return assignment;
    }

    private ContainerAssignment sealed() {
        ContainerAssignment assignment = dispatched();
        assignment.recordContainerNumber("MSCU1234567", ContainerType.FORTY_HC,
                ContainerSource.CARRIER_YARD, NOW, ACTOR);
        assignment.recordDeliveredToCustomer(NOW);
        assignment.recordLoadingComplete(NOW);
        assignment.recordSealNumber("SEAL123456", NOW, ACTOR);
        return assignment;
    }

    private ContainerAssignment atTerminalReady() {
        ContainerAssignment assignment = sealed();
        assignment.recordItnReceived("X99999999000001", NOW);
        assignment.dispatchInbound("TDO-2-IN", DRIVER, null, null,
                "Plot 14, MIDC Andheri", "APM Terminals", NOW, NOW, NOW, ACTOR);
        assignment.recordLoadedContainerPickedUp(NOW);
        return assignment;
    }
}
