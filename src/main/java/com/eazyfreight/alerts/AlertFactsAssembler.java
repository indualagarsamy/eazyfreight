package com.eazyfreight.alerts;

import com.eazyfreight.booking.Booking;
import com.eazyfreight.booking.BookingStatus;
import com.eazyfreight.booking.CarrierBooking;
import com.eazyfreight.compliance.EEIFiling;
import com.eazyfreight.compliance.EEIFilingRepository;
import com.eazyfreight.compliance.FilingStatus;
import com.eazyfreight.documentation.HouseBOL;
import com.eazyfreight.documentation.HouseBOLRepository;
import com.eazyfreight.documentation.MasterBOL;
import com.eazyfreight.documentation.MasterBOLInstructions;
import com.eazyfreight.documentation.MasterBOLInstructionsRepository;
import com.eazyfreight.documentation.MasterBOLRepository;
import com.eazyfreight.finance.CarrierPayable;
import com.eazyfreight.finance.CarrierPayableRepository;
import com.eazyfreight.finance.CreditHoldRepository;
import com.eazyfreight.finance.Invoice;
import com.eazyfreight.finance.InvoiceRepository;
import com.eazyfreight.finance.FinanceEnums.InvoiceStatus;
import com.eazyfreight.finance.FinanceEnums.InvoiceType;
import com.eazyfreight.finance.FinanceEnums.PayableStatus;
import com.eazyfreight.finance.StorageFee;
import com.eazyfreight.finance.StorageFeeRepository;
import com.eazyfreight.logistics.CBPExamination;
import com.eazyfreight.logistics.ContainerAssignment;
import com.eazyfreight.logistics.ContainerAssignmentRepository;
import com.eazyfreight.logistics.LogisticsStage;
import com.eazyfreight.logistics.TerminalAcceptance;
import com.eazyfreight.logistics.TruckDispatch;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Reads one booking's state out of the five operational contexts and flattens it into
 * an {@link AlertFacts}.
 *
 * <p>This is the only place in the Alerts track that knows another context exists. Every
 * condition in {@link AlertRules} works from the snapshot, which means the rules can be
 * unit-tested against a record literal rather than a database, and a change to how
 * Documentation stores its instructions touches one method here instead of four rules.
 *
 * <p>Reading across contexts rather than maintaining a projection is a deliberate trade.
 * A projection would be faster and would drift; alerting on stale facts is worse than
 * alerting slowly, because the whole point is to be right about what is missing.
 */
@Component
@RequiredArgsConstructor
public class AlertFactsAssembler {

    private final ContainerAssignmentRepository logisticsRepository;
    private final EEIFilingRepository filingRepository;
    private final MasterBOLInstructionsRepository instructionsRepository;
    private final MasterBOLRepository masterBolRepository;
    private final HouseBOLRepository houseBolRepository;
    private final InvoiceRepository invoiceRepository;
    private final CarrierPayableRepository payableRepository;
    private final StorageFeeRepository storageFeeRepository;
    private final CreditHoldRepository creditHoldRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AlertFacts assemble(Booking booking) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);

        CarrierBooking carrier = booking.getCarrierBooking();
        LocalDate etd = carrier == null ? null : carrier.getConfirmedEtd();
        Integer daysToEtd = etd == null ? null : (int) ChronoUnit.DAYS.between(today, etd);

        Optional<ContainerAssignment> logistics = logisticsRepository.findByBookingId(booking.getId());
        List<EEIFiling> filings = filingRepository.findByBookingId(booking.getId());
        List<MasterBOLInstructions> instructions = instructionsRepository.findByBookingId(booking.getId());
        Optional<MasterBOL> masterBol = masterBolRepository.findFirstByBookingIdOrderByReceivedAtDesc(booking.getId());
        Optional<HouseBOL> houseBol = houseBolRepository.findByBookingIdAndActiveTrue(booking.getId());
        Optional<Invoice> freightInvoice = invoiceRepository
                .findFirstByBookingIdAndInvoiceType(booking.getId(), InvoiceType.FREIGHT);
        List<CarrierPayable> payables = payableRepository.findByBookingId(booking.getId());
        Optional<StorageFee> storageFee = storageFeeRepository.findByBookingId(booking.getId());

        // The ITN belongs to Compliance. Logistics keeps its own copy to run the inbound
        // gate, but that copy only exists once a container assignment does — and an EEI
        // is routinely accepted before anyone books a truck. Reading the projection here
        // would report "no ITN" on a booking that has had one for a week.
        Optional<String> activeItn = filings.stream()
                .filter(filing -> filing.getStatus() == FilingStatus.ACCEPTED)
                .flatMap(filing -> filing.activeItn().stream())
                .map(com.eazyfreight.compliance.ItnRecord::getItnNumber)
                .findFirst();

        Optional<TerminalAcceptance> terminal = logistics.map(ContainerAssignment::getTerminalAcceptance);
        Optional<TruckDispatch> inbound = logistics.flatMap(ContainerAssignment::inboundDispatch);
        Optional<CarrierPayable> openPayable = payables.stream()
                .filter(payable -> payable.getStatus() != PayableStatus.PAID)
                .findFirst();

        // The most recent filing is the one that counts: a correction supersedes its
        // predecessor, and alerting on the superseded one would report a rejection that
        // has already been dealt with.
        Optional<EEIFiling> filing = filings.stream()
                .max(java.util.Comparator.comparing(EEIFiling::getCreatedAt));

        return new AlertFacts(
                booking.getId(),
                booking.getBookingReference(),
                today,
                now,

                booking.getStatus() == BookingStatus.CANCELLED,
                booking.getStatus() == BookingStatus.VESSEL_OVERBOOKED,
                booking.getStatus() == BookingStatus.SUBMITTED_TO_CARRIER,
                carrier == null ? null : carrier.getSubmittedAt(),
                carrier != null && carrier.getConfirmedAt() != null,
                etd,
                carrier == null ? null : carrier.getConfirmedEta(),
                daysToEtd,
                booking.requiresCustomerEtdNotification()
                        && booking.getEtdVarianceAcknowledgedAt() == null,

                logistics.flatMap(ContainerAssignment::outboundDispatch).isPresent(),
                logistics.map(a -> reached(a, LogisticsStage.AT_CUSTOMER)).orElse(false),
                logistics.flatMap(ContainerAssignment::outboundDispatch)
                        .map(TruckDispatch::getActualDeliveryDate)
                        .map(instant -> LocalDate.ofInstant(instant, ZoneOffset.UTC))
                        .orElse(null),
                logistics.map(a -> a.getLoadingCompletedAt() != null).orElse(false),
                logistics.flatMap(ContainerAssignment::activeSeal).isPresent(),
                activeItn.isPresent(),
                activeItn.orElse(null),
                inbound.isPresent(),
                inbound.map(TruckDispatch::getScheduledDeliveryDate).orElse(null),
                terminal.isPresent(),
                terminal.map(TerminalAcceptance::getEarliestAcceptanceDate).orElse(null),
                terminal.map(TerminalAcceptance::getVesselCutOffDate).orElse(null),
                terminal.map(TerminalAcceptance::getStorageFeeDailyRate).orElse(null),
                terminal.map(TerminalAcceptance::isStorageFeeApplies).orElse(false),
                terminal.map(TerminalAcceptance::getDaysEarly).orElse(null),
                logistics.map(a -> a.getExaminations().stream().anyMatch(CBPExamination::isOpen))
                        .orElse(false),
                logistics.map(a -> a.getVesselDepartedAt() != null).orElse(false),
                logistics.map(ContainerAssignment::getContainerNumber).orElse(null),

                filing.isPresent(),
                filing.map(f -> f.getSubmittedAt() != null).orElse(false),
                filing.map(f -> f.getStatus() == FilingStatus.REJECTED).orElse(false),
                filing.map(EEIFiling::getRejectionReasonCode).orElse(null),
                filing.map(EEIFiling::getRejectionReasonDescription).orElse(null),
                filing.map(f -> f.getAmendmentReason() != null
                        && f.getStatus() != FilingStatus.CANCELLED).orElse(false),
                filing.map(EEIFiling::getAmendmentReason).orElse(null),

                instructions.stream().anyMatch(si -> si.getSentAt() != null),
                instructions.stream()
                        .filter(si -> si.getSentAt() != null)
                        .map(MasterBOLInstructions::getSentAt)
                        .max(Instant::compareTo).orElse(null),
                masterBol.isPresent(),
                masterBol.map(MasterBOL::getReceivedAt).orElse(null),
                houseBol.isPresent(),
                houseBol.map(hbl -> !hbl.getDistributions().isEmpty()).orElse(false),
                houseBol.map(HouseBOL::getIssuedAt).orElse(null),

                freightInvoice.isPresent(),
                freightInvoice.map(inv -> inv.getStatus() != InvoiceStatus.PREPARED
                        && inv.getStatus() != InvoiceStatus.VOIDED).orElse(false),
                freightInvoice.map(Invoice::getInvoiceNumber).orElse(null),
                freightInvoice.map(Invoice::getTotalAmount).orElse(null),
                freightInvoice.map(Invoice::getPaymentDueDate).orElse(null),
                freightInvoice.map(inv -> inv.getStatus() == InvoiceStatus.PAID).orElse(false),
                daysOverdue(freightInvoice, today),
                creditHoldRepository.findByCustomerIdAndActiveTrue(booking.getCustomerId()).isPresent(),
                openPayable.isPresent(),
                openPayable.map(CarrierPayable::getDueDate).orElse(null),
                openPayable.map(CarrierPayable::getAmount).orElse(null),
                openPayable.map(p -> p.getCarrierInvoiceReference() != null).orElse(false),
                openPayable.map(CarrierPayable::invoiceVariance).orElse(BigDecimal.ZERO),
                storageFee.map(fee -> fee.getInvoiceId() != null).orElse(false));
    }

    /**
     * Whether the container has been at least as far as {@code stage}. Comparing stages
     * by ordinal works because the lifecycle is linear up to the terminal, and a
     * container that is loaded on a vessel has plainly been delivered to the customer.
     */
    private boolean reached(ContainerAssignment assignment, LogisticsStage stage) {
        LogisticsStage current = assignment.getStage();
        if (current == LogisticsStage.UNDER_CBP_EXAMINATION) {
            return true;
        }
        return current.ordinal() >= stage.ordinal();
    }

    private Integer daysOverdue(Optional<Invoice> invoice, LocalDate today) {
        return invoice
                .filter(inv -> inv.isOverdueAsOf(today))
                .map(inv -> (int) inv.daysOverdue(today))
                .orElse(null);
    }
}
