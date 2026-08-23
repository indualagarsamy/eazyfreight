package com.eazyfreight.documentation.domain;

import com.eazyfreight.documentation.domain.DocumentationEnums.DistributionChannel;
import com.eazyfreight.documentation.domain.DocumentationEnums.DistributionRecipient;
import com.eazyfreight.documentation.domain.DocumentationEnums.FreightTerms;
import com.eazyfreight.documentation.domain.DocumentationEnums.HouseBOLStatus;
import com.eazyfreight.documentation.domain.DocumentationEnums.ReleaseType;

import com.eazyfreight.documentation.event.DocumentationEvent;

import com.eazyfreight.exception.DomainRuleViolationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests for the House BOL — revisions, snapshots and the originals gate. */
class HouseBOLLifecycleTest {

    private static final Instant NOW = Instant.parse("2026-08-22T10:00:00Z");
    private static final UUID BOOKING = UUID.randomUUID();
    private static final UUID MASTER = UUID.randomUUID();
    private static final String ACTOR = "ops.jane";

    @Test
    void anAmendmentKeepsTheNumberAndIncrementsTheRevision() {
        HouseBOL rev0 = issued(ReleaseType.TELEX_RELEASE);

        HouseBOL rev1 = rev0.amendInto("Consignee address corrected", null,
                null, "12 Marina Boulevard", null, null,
                null, null, null, null, null, null, null, null, NOW, ACTOR);

        assertThat(rev1.getHouseBolNumber()).isEqualTo(rev0.getHouseBolNumber());
        assertThat(rev1.getRevisionNumber()).isEqualTo(1);
        assertThat(rev1.isActive()).isTrue();
        assertThat(rev1.getConsigneeAddressSnapshot()).isEqualTo("12 Marina Boulevard");

        // The superseded revision survives, voided, still carrying its own snapshot.
        assertThat(rev0.isActive()).isFalse();
        assertThat(rev0.getStatus()).isEqualTo(HouseBOLStatus.VOIDED);
        assertThat(rev0.getConsigneeAddressSnapshot()).isEqualTo("9 Raffles Place");

        // Linking is a separate step, because the successor row has to exist before
        // anything can reference it. The service does this after inserting rev1.
        assertThat(rev0.getSupersededByHouseBolId()).isNull();
        rev0.linkSupersededBy(rev1.getId());
        assertThat(rev0.getSupersededByHouseBolId()).isEqualTo(rev1.getId());
    }

    @Test
    void partySnapshotsDoNotFollowLaterChanges() {
        HouseBOL bol = issued(ReleaseType.SEA_WAYBILL);

        // Whatever happens to the customer record, the issued document is fixed.
        assertThat(bol.getShipperNameSnapshot()).isEqualTo("Acme Manufacturing");
        assertThat(bol.getConsigneeNameSnapshot()).isEqualTo("Singapore Trading Pte");
        assertThat(bol.getConsigneeAddressSnapshot()).isEqualTo("9 Raffles Place");
    }

    @Test
    void outstandingOriginalsBlockAnAmendment() {
        HouseBOL bol = issued(ReleaseType.ORIGINAL_BOL);
        bol.releaseOriginals("Acme Manufacturing", "DHL-8891", NOW);

        assertThat(bol.amendmentBlockedReason()).contains("3 of 3 negotiable originals");
        assertThatThrownBy(() -> bol.amendInto("Vessel changed", null, null, null, null, null,
                "MAERSK KOWLOON", null, null, null, null, null, null, null, NOW, ACTOR))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("still outstanding");

        bol.recordOriginalsSurrendered(3, NOW);

        assertThat(bol.originals().orElseThrow().allSurrendered()).isTrue();
        assertThat(bol.amendmentBlockedReason()).isNull();
        HouseBOL rev1 = bol.amendInto("Vessel changed", null, null, null, null, null,
                "MAERSK KOWLOON", null, null, null, null, null, null, null, NOW, ACTOR);
        assertThat(rev1.getVesselName()).isEqualTo("MAERSK KOWLOON");
    }

    @Test
    void partialSurrenderStillBlocks() {
        HouseBOL bol = issued(ReleaseType.ORIGINAL_BOL);
        bol.releaseOriginals("Acme Manufacturing", "DHL-8891", NOW);
        bol.recordOriginalsSurrendered(2, NOW);

        assertThat(bol.originals().orElseThrow().outstanding()).isEqualTo(1);
        assertThat(bol.amendmentBlockedReason()).contains("1 of 3");
    }

    @Test
    void moreOriginalsCannotBeSurrenderedThanWereIssued() {
        HouseBOL bol = issued(ReleaseType.ORIGINAL_BOL);
        bol.releaseOriginals("Acme Manufacturing", "DHL-8891", NOW);

        assertThatThrownBy(() -> bol.recordOriginalsSurrendered(4, NOW))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("only 3 of 3 are outstanding");
    }

    @Test
    void aTelexReleaseHasNoOriginalsToRelease() {
        HouseBOL bol = issued(ReleaseType.TELEX_RELEASE);

        assertThatThrownBy(() -> bol.releaseOriginals("Acme", "DHL-8891", NOW))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("only exist for an ORIGINAL_BOL");
        assertThat(bol.amendmentBlockedReason()).isNull();
    }

    @Test
    void distributionRecordsWhoGotWhichRevision() {
        HouseBOL rev0 = issued(ReleaseType.TELEX_RELEASE);
        rev0.recordDistribution(DistributionRecipient.SHIPPER, "Acme Manufacturing",
                "Plot 14", DistributionChannel.EMAIL, "msg-1", NOW, ACTOR);

        HouseBOL rev1 = rev0.amendInto("Weight corrected", null, null, null, null, null,
                null, null, null, null, new BigDecimal("620"), null, null, null, NOW, ACTOR);
        rev1.recordDistribution(DistributionRecipient.CONSIGNEE_AGENT, "Singapore Trading Pte",
                "9 Raffles Place", DistributionChannel.EMAIL, "msg-2", NOW, ACTOR);

        assertThat(rev0.getDistributions()).hasSize(1);
        assertThat(rev0.getDistributions().get(0).getRevisionNumber()).isZero();
        assertThat(rev1.getDistributions().get(0).getRevisionNumber()).isEqualTo(1);
    }

    @Test
    void aSupersededRevisionCannotBeDistributed() {
        HouseBOL rev0 = issued(ReleaseType.TELEX_RELEASE);
        rev0.amendInto("Weight corrected", null, null, null, null, null,
                null, null, null, null, new BigDecimal("620"), null, null, null, NOW, ACTOR);

        assertThatThrownBy(() -> rev0.recordDistribution(DistributionRecipient.SHIPPER,
                "Acme", "Plot 14", DistributionChannel.EMAIL, "msg-3", NOW, ACTOR))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("superseded revision");
    }

    @Test
    void changingCargoOnAnAmendmentFlagsComplianceForAnEeiAmendment() {
        HouseBOL rev0 = issued(ReleaseType.TELEX_RELEASE);

        HouseBOL rev1 = rev0.amendInto("Actual weight came in higher", null,
                null, null, null, null, null, null, null, null,
                new BigDecimal("620"), null, null, null, NOW, ACTOR);

        assertThat(rev1.pendingEvents())
                .anyMatch(DocumentationEvent.BOLCargoDetailsChanged.class::isInstance);
    }

    @Test
    void anAmendmentThatLeavesCargoAloneRaisesNoComplianceFlag() {
        HouseBOL rev0 = issued(ReleaseType.TELEX_RELEASE);

        HouseBOL rev1 = rev0.amendInto("Notify party added", null, null, null,
                "Destination Agent Pte", "1 Keppel Road", null, null, null,
                null, null, null, null, null, NOW, ACTOR);

        assertThat(rev1.pendingEvents())
                .noneMatch(DocumentationEvent.BOLCargoDetailsChanged.class::isInstance);
    }

    // ----------------------------------------------------------------- fixtures

    private HouseBOL issued(ReleaseType releaseType) {
        return HouseBOL.issue("HBL-2026-00287", BOOKING, MASTER, releaseType,
                UUID.randomUUID(), "Acme Manufacturing", "Plot 14, MIDC Andheri",
                UUID.randomUUID(), "Singapore Trading Pte", "9 Raffles Place",
                null, null, "INBOM", "SGSIN", "MAERSK SEALAND", "024W",
                "MSCU1234567", "SEAL123456", "Machine parts", "8471.30.0100",
                new BigDecimal("500"), 10, new BigDecimal("10"), null,
                FreightTerms.PREPAID, NOW, ACTOR);
    }
}
