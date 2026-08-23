package com.eazyfreight.logistics.dto;

import com.eazyfreight.booking.domain.ContainerType;
import com.eazyfreight.logistics.domain.ContainerSource;
import com.eazyfreight.logistics.domain.ExaminationResult;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request payloads for the logistics commands, grouped in one file because each is
 * two or three fields and they are only ever used together.
 */
public final class LogisticsRequests {

    private LogisticsRequests() {
    }

    public record DispatchTruck(
            UUID driverId,
            UUID truckingVendorId,
            String vehicleReference,
            @NotBlank(message = "pickupAddress is required") String pickupAddress,
            @NotBlank(message = "deliveryAddress is required") String deliveryAddress,
            Instant scheduledPickupDate,
            Instant scheduledDeliveryDate
    ) {
    }

    public record RecordContainerNumber(
            @NotBlank(message = "containerNumber is required") String containerNumber,
            ContainerType containerType,
            ContainerSource source
    ) {
    }

    public record RecordSeal(
            @NotBlank(message = "sealNumber is required") String sealNumber
    ) {
    }

    public record TerminalGateReceipt(
            @NotBlank(message = "gateReceiptNumber is required") String gateReceiptNumber,
            @NotBlank(message = "terminalName is required") String terminalName,
            LocalDate earliestAcceptanceDate,
            LocalDate vesselCutOffDate,
            BigDecimal storageFeeDailyRate
    ) {
    }

    public record TerminalGateRejection(
            @NotBlank(message = "reason is required") String reason
    ) {
    }

    public record ExaminationHold(
            String cbpOfficerId,
            String notes
    ) {
    }

    public record ExaminationRelease(
            @NotNull(message = "result is required") ExaminationResult result,
            String replacementSealNumber,
            String notes
    ) {
    }

    public record LoadedOnVessel(
            String vesselName
    ) {
    }

    public record ActualCargo(
            @NotNull(message = "actualWeightKg is required")
            @DecimalMin(value = "0.001", message = "actualWeightKg must be greater than 0")
            BigDecimal actualWeightKg,
            @Min(value = 1, message = "actualPieces must be greater than 0") int actualPieces,
            BigDecimal actualCbm
    ) {
    }
}
