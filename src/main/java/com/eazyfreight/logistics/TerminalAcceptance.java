package com.eazyfreight.logistics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * The terminal's gate receipt for the loaded container.
 *
 * <p>Delivering before the carrier's earliest acceptance date starts storage fees
 * accruing per diem. The monolith discovered that when the terminal's invoice
 * arrived; here it is computed at the gate.
 */
@Entity
@Table(name = "terminal_acceptances")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TerminalAcceptance {

    @Id
    private UUID id;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "container_number", nullable = false, length = 24)
    private String containerNumber;

    @Column(name = "gate_receipt_number", nullable = false, length = 64)
    private String gateReceiptNumber;

    @Column(name = "terminal_name", nullable = false, length = 128)
    private String terminalName;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;

    @Column(name = "earliest_acceptance_date")
    private LocalDate earliestAcceptanceDate;

    @Column(name = "vessel_cut_off_date")
    private LocalDate vesselCutOffDate;

    @Column(name = "storage_fee_applies", nullable = false)
    private boolean storageFeeApplies;

    @Column(name = "storage_fee_daily_rate", precision = 12, scale = 2)
    private BigDecimal storageFeeDailyRate;

    @Column(name = "days_early")
    private Integer daysEarly;

    static TerminalAcceptance record(
            UUID bookingId, String containerNumber, String gateReceiptNumber, String terminalName,
            Instant acceptedAt, LocalDate earliestAcceptanceDate, LocalDate vesselCutOffDate,
            BigDecimal storageFeeDailyRate
    ) {
        TerminalAcceptance acceptance = new TerminalAcceptance();
        acceptance.id = UUID.randomUUID();
        acceptance.bookingId = bookingId;
        acceptance.containerNumber = containerNumber;
        acceptance.gateReceiptNumber = gateReceiptNumber;
        acceptance.terminalName = terminalName;
        acceptance.acceptedAt = acceptedAt;
        acceptance.earliestAcceptanceDate = earliestAcceptanceDate;
        acceptance.vesselCutOffDate = vesselCutOffDate;
        acceptance.storageFeeDailyRate = storageFeeDailyRate;

        LocalDate acceptedOn = acceptedAt.atZone(ZoneOffset.UTC).toLocalDate();
        if (earliestAcceptanceDate != null && acceptedOn.isBefore(earliestAcceptanceDate)) {
            acceptance.storageFeeApplies = true;
            acceptance.daysEarly = (int) java.time.temporal.ChronoUnit.DAYS
                    .between(acceptedOn, earliestAcceptanceDate);
        }
        return acceptance;
    }

    /** Estimated exposure at the terminal's per diem rate. */
    public BigDecimal estimatedStorageFee() {
        if (!storageFeeApplies || storageFeeDailyRate == null || daysEarly == null) {
            return BigDecimal.ZERO;
        }
        return storageFeeDailyRate.multiply(BigDecimal.valueOf(daysEarly));
    }
}
