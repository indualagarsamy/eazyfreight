package com.eazyfreight.compliance;

import com.eazyfreight.exception.DomainRuleViolationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests for the EEI filing state machine. No Spring context, no database. */
class EEIFilingLifecycleTest {

    private static final Instant NOW = Instant.parse("2026-08-22T10:00:00Z");
    private static final LocalDate ETD = LocalDate.of(2026, 10, 15);
    private static final String ACTOR = "compliance.sam";
    private static final UUID BOOKING = UUID.randomUUID();

    @Test
    void aFilingCannotBeSubmittedWhileFieldsCbpRequiresAreBlank() {
        EEIFiling filing = EEIFiling.initiate("EEI-2026-00001", BOOKING, NOW, ACTOR);

        assertThatThrownBy(() -> filing.markSubmitted(NOW, ACTOR))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("CBP requires");
        assertThat(filing.missingRequiredFields()).contains("shipperEin", "carrierScac");
    }

    @Test
    void dataCannotBeChangedOnceCbpHasSeenTheFiling() {
        EEIFiling filing = compiled();
        filing.markSubmitted(NOW, ACTOR);

        assertThatThrownBy(() -> compile(filing, "Changed description"))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("draft");
    }

    @Test
    void acceptanceIssuesAnActiveItnAndOpensTheGate() {
        EEIFiling filing = compiled();
        filing.markSubmitted(NOW, ACTOR);

        filing.recordAcceptance(new ItnNumber("X99999999000001"), "AES-SIM-000001", NOW, null, NOW, ACTOR);

        assertThat(filing.getStatus()).isEqualTo(FilingStatus.ACCEPTED);
        assertThat(filing.activeItn()).isPresent();
        assertThat(filing.activeItn().orElseThrow().getItnNumber()).isEqualTo("X99999999000001");
        assertThat(filing.pendingEvents())
                .anyMatch(ComplianceEvent.ItnNumberReceived.class::isInstance)
                .anyMatch(ComplianceEvent.ItnGateCheckPassed.class::isInstance);
    }

    @Test
    void aCorrectionReplacesARejectedFilingAndIsNotAnAmendment() {
        EEIFiling rejected = compiled();
        rejected.markSubmitted(NOW, ACTOR);
        rejected.recordRejection("127", "Commodity description invalid", NOW, NOW, ACTOR);

        EEIFiling corrected = rejected.correctInto("EEI-2026-00002", NOW, ACTOR);

        // No ITN was ever issued, so there is nothing to amend — it is a fresh original.
        assertThat(corrected.getFilingType()).isEqualTo(FilingType.ORIGINAL);
        assertThat(corrected.getParentFilingId()).isEqualTo(rejected.getId());
        assertThat(corrected.getStatus()).isEqualTo(FilingStatus.DRAFT);
        assertThat(corrected.getShipperEin()).isEqualTo("12-3456789");
    }

    @Test
    void onlyARejectedFilingCanBeCorrected() {
        EEIFiling filing = compiled();
        assertThatThrownBy(() -> filing.correctInto("EEI-2026-00002", NOW, ACTOR))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("rejected");
    }

    @Test
    void anAmendmentSupersedesThePreviousItnWithoutDestroyingIt() {
        EEIFiling original = accepted("X99999999000001");
        ItnRecord firstItn = original.activeItn().orElseThrow();

        EEIFiling amendment = original.amendInto("EEI-2026-00002", "Vessel changed", NOW, ACTOR);
        amendment.markSubmitted(NOW, ACTOR);
        amendment.recordAcceptance(
                new ItnNumber("X99999999000002"), "AES-SIM-000002", NOW, firstItn, NOW, ACTOR);

        assertThat(amendment.getFilingType()).isEqualTo(FilingType.AMENDMENT);
        assertThat(amendment.getParentFilingId()).isEqualTo(original.getId());
        assertThat(amendment.activeItn().orElseThrow().getItnNumber()).isEqualTo("X99999999000002");

        // The original ITN survives, marked inactive and pointing at its replacement.
        assertThat(firstItn.isActive()).isFalse();
        assertThat(firstItn.getSupersededByItnId())
                .isEqualTo(amendment.activeItn().orElseThrow().getId());
        assertThat(original.getItnRecords()).hasSize(1);
    }

    @Test
    void onlyAnAcceptedFilingCanBeAmended() {
        EEIFiling filing = compiled();
        assertThatThrownBy(() -> filing.amendInto("EEI-2026-00002", "reason", NOW, ACTOR))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("accepted");
    }

    @Test
    void cancellingVoidsTheActiveItn() {
        EEIFiling filing = accepted("X99999999000001");

        filing.cancel("Booking cancelled by customer", NOW, ACTOR);

        assertThat(filing.getStatus()).isEqualTo(FilingStatus.CANCELLED);
        assertThat(filing.activeItn()).isEmpty();
        assertThat(filing.getItnRecords().get(0).isActive()).isFalse();
        assertThat(filing.pendingEvents()).anyMatch(ComplianceEvent.EEIFilingCancelled.class::isInstance);
    }

    @Test
    void aLicensableCommodityBlocksSubmissionUntilTheLicenceIsRecorded() {
        EEIFiling filing = EEIFiling.initiate("EEI-2026-00001", BOOKING, NOW, ACTOR);
        compile(filing, "Encryption hardware", true);

        assertThatThrownBy(() -> filing.markSubmitted(NOW, ACTOR))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("export licence");
        assertThat(filing.pendingEvents())
                .anyMatch(ComplianceEvent.ExportLicenseRequired.class::isInstance);

        filing.recordExportLicense(ExportLicense.of("BIS-12345", "BIS", "Individual",
                "5A002", ETD.minusMonths(6), ETD.plusMonths(6), new BigDecimal("100000")), NOW, ACTOR);
        filing.markSubmitted(NOW, ACTOR);

        assertThat(filing.getStatus()).isEqualTo(FilingStatus.SUBMITTED);
    }

    @Test
    void filingIsRequiredOverTheThresholdOrWhenLicensable() {
        EEIFiling low = EEIFiling.initiate("EEI-2026-00001", BOOKING, NOW, ACTOR);
        compileWithValue(low, new BigDecimal("1500"), false);
        assertThat(low.isFilingRequired()).isFalse();

        EEIFiling high = EEIFiling.initiate("EEI-2026-00002", BOOKING, NOW, ACTOR);
        compileWithValue(high, new BigDecimal("2500.01"), false);
        assertThat(high.isFilingRequired()).isTrue();

        EEIFiling licensable = EEIFiling.initiate("EEI-2026-00003", BOOKING, NOW, ACTOR);
        compileWithValue(licensable, new BigDecimal("100"), true);
        assertThat(licensable.isFilingRequired()).isTrue();
    }

    @Test
    void anUntranslatedHsCodeIsFlaggedRatherThanPassedOffAsScheduleB() {
        EEIFiling filing = EEIFiling.initiate("EEI-2026-00001", BOOKING, NOW, ACTOR);
        filing.compile("Acme", "12-3456789", "Plot 14", "Sing Pte", "9 Raffles", "SG",
                ScheduleBCode.fromHsCode("8471.30.0100"), "Machine parts",
                BigDecimal.TEN, "PCS", new BigDecimal("50000"), "MAEU",
                "MAERSK SEALAND", "024W", "INBOM", "SG", ETD, false, NOW, ACTOR);

        assertThat(filing.isScheduleBTranslated()).isFalse();
        assertThat(filing.getScheduleBNumber()).isEqualTo("8471.30.0100");
    }

    @Test
    void historyIsAppendOnlyAndOrdered() {
        EEIFiling filing = accepted("X99999999000001");

        assertThat(filing.getHistory())
                .extracting(EEIFilingHistory::getSequenceNumber)
                .containsExactly(1, 2, 3, 4);
        assertThat(filing.getHistory())
                .extracting(EEIFilingHistory::getToStatus)
                .containsExactly(FilingStatus.DRAFT, FilingStatus.DRAFT,
                        FilingStatus.SUBMITTED, FilingStatus.ACCEPTED);
    }

    // ----------------------------------------------------------------- fixtures

    private EEIFiling compiled() {
        EEIFiling filing = EEIFiling.initiate("EEI-2026-00001", BOOKING, NOW, ACTOR);
        compile(filing, "Machine parts");
        return filing;
    }

    private EEIFiling accepted(String itn) {
        EEIFiling filing = compiled();
        filing.markSubmitted(NOW, ACTOR);
        filing.recordAcceptance(new ItnNumber(itn), "AES-SIM-000001", NOW, null, NOW, ACTOR);
        return filing;
    }

    private void compile(EEIFiling filing, String description) {
        compile(filing, description, false);
    }

    private void compile(EEIFiling filing, String description, boolean licenseRequired) {
        filing.compile("Acme Manufacturing", "12-3456789", "Plot 14",
                "Singapore Trading Pte", "9 Raffles Place", "SG",
                ScheduleBCode.declared("8471.30.0100"), description,
                BigDecimal.TEN, "PCS", new BigDecimal("50000"), "MAEU",
                "MAERSK SEALAND", "024W", "INBOM", "SG", ETD, licenseRequired, NOW, ACTOR);
    }

    private void compileWithValue(EEIFiling filing, BigDecimal value, boolean licenseRequired) {
        filing.compile("Acme", "12-3456789", "Plot 14", "Sing Pte", "9 Raffles", "SG",
                ScheduleBCode.declared("8471.30.0100"), "Machine parts",
                BigDecimal.TEN, "PCS", value, "MAEU", "MAERSK SEALAND", "024W",
                "INBOM", "SG", ETD, licenseRequired, NOW, ACTOR);
    }
}
