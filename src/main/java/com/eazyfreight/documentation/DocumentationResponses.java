package com.eazyfreight.documentation;

import com.eazyfreight.documentation.DocumentationEnums.DistributionChannel;
import com.eazyfreight.documentation.DocumentationEnums.DistributionRecipient;
import com.eazyfreight.documentation.DocumentationEnums.FreightTerms;
import com.eazyfreight.documentation.DocumentationEnums.HouseBOLStatus;
import com.eazyfreight.documentation.DocumentationEnums.InstructionsStatus;
import com.eazyfreight.documentation.DocumentationEnums.ReleaseType;
import com.eazyfreight.documentation.DocumentationEnums.VerificationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Read models for the Documentation context. */
public final class DocumentationResponses {

    private DocumentationResponses() {
    }

    /** What the Documentation track is waiting on, per booking. */
    public record Preconditions(
            UUID bookingId,
            boolean met,
            String containerNumber,
            String sealNumber,
            String itnNumber,
            String carrierBookingRef,
            List<String> missing
    ) {
    }

    public record Instructions(
            UUID id, UUID bookingId, String instructionsReference, InstructionsStatus status,
            UUID supersedesInstructionsId, String carrierBookingRef,
            String containerNumber, String sealNumber, String itnNumber,
            String shipperName, String shipperAddress,
            String consigneeName, String consigneeAddress,
            String notifyPartyName, String notifyPartyAddress,
            String portOfLoadingCode, String portOfDischargeCode,
            String vesselName, String voyageNumber,
            String cargoDescription, String hsCode,
            BigDecimal actualWeightKg, Integer actualPieces, BigDecimal actualCbm,
            String marksAndNumbers, FreightTerms freightTerms,
            Instant draftedAt, String draftedBy, Instant approvedAt, String approvedBy,
            Instant sentAt, LocalDate documentationCutOffDate, boolean sentAfterCutOff,
            String carrierQuery
    ) {
        public static Instructions from(MasterBOLInstructions i) {
            return new Instructions(i.getId(), i.getBookingId(), i.getInstructionsReference(),
                    i.getStatus(), i.getSupersedesInstructionsId(), i.getCarrierBookingRef(),
                    i.getContainerNumber(), i.getSealNumber(), i.getItnNumber(),
                    i.getShipperName(), i.getShipperAddress(),
                    i.getConsigneeName(), i.getConsigneeAddress(),
                    i.getNotifyPartyName(), i.getNotifyPartyAddress(),
                    i.getPortOfLoadingCode(), i.getPortOfDischargeCode(),
                    i.getVesselName(), i.getVoyageNumber(),
                    i.getCargoDescription(), i.getHsCode(),
                    i.getActualWeightKg(), i.getActualPieces(), i.getActualCbm(),
                    i.getMarksAndNumbers(), i.getFreightTerms(),
                    i.getDraftedAt(), i.getDraftedBy(), i.getApprovedAt(), i.getApprovedBy(),
                    i.getSentAt(), i.getDocumentationCutOffDate(), i.isSentAfterCutOff(),
                    i.getCarrierQuery());
        }
    }

    public record Master(
            UUID id, UUID bookingId, UUID instructionsId, String masterBolNumber,
            Instant issuedByCarrierAt, Instant receivedAt, String receivedBy,
            VerificationStatus verificationStatus, Instant verifiedAt, String verifiedBy,
            List<String> discrepancyFields, Instant discrepancyRaisedAt,
            Instant discrepancyResolvedAt, String documentFileReference, boolean verified
    ) {
        public static Master from(MasterBOL m) {
            return new Master(m.getId(), m.getBookingId(), m.getInstructionsId(),
                    m.getMasterBolNumber(), m.getIssuedByCarrierAt(), m.getReceivedAt(),
                    m.getReceivedBy(), m.getVerificationStatus(), m.getVerifiedAt(),
                    m.getVerifiedBy(), m.getDiscrepancyFields(), m.getDiscrepancyRaisedAt(),
                    m.getDiscrepancyResolvedAt(), m.getDocumentFileReference(), m.isVerified());
        }
    }

    public record Originals(
            UUID id, int originalsIssued, int originalsSurrendered, int outstanding,
            boolean allSurrendered, Instant releasedAt, String releasedTo,
            String courierReference, Instant surrenderedAt
    ) {
        static Originals from(OriginalBOLTracking t) {
            if (t == null) {
                return null;
            }
            return new Originals(t.getId(), t.getOriginalsIssued(), t.getOriginalsSurrendered(),
                    t.outstanding(), t.allSurrendered(), t.getReleasedAt(), t.getReleasedTo(),
                    t.getCourierReference(), t.getSurrenderedAt());
        }
    }

    public record Distribution(
            UUID id, DistributionRecipient recipient, String recipientName,
            String recipientAddress, DistributionChannel channel, int revisionNumber,
            Instant sentAt, String sentBy, String reference
    ) {
        static Distribution from(HouseBOLDistribution d) {
            return new Distribution(d.getId(), d.getRecipient(), d.getRecipientName(),
                    d.getRecipientAddress(), d.getChannel(), d.getRevisionNumber(),
                    d.getSentAt(), d.getSentBy(), d.getReference());
        }
    }

    /**
     * @param amendmentBlockedReason why this revision cannot be amended, or null.
     *                               Outstanding negotiable originals live here.
     */
    public record House(
            UUID id, UUID bookingId, UUID masterBolId, String houseBolNumber,
            int revisionNumber, boolean active, HouseBOLStatus status,
            ReleaseType releaseType, Instant releaseTypeConfirmedAt, String releaseTypeConfirmedBy,
            String shipperNameSnapshot, String shipperAddressSnapshot,
            String consigneeNameSnapshot, String consigneeAddressSnapshot,
            String notifyPartyNameSnapshot, String notifyPartyAddressSnapshot,
            String portOfLoadingCode, String portOfDischargeCode,
            String vesselName, String voyageNumber,
            String containerNumber, String sealNumber,
            String cargoDescription, String hsCode,
            BigDecimal weightKg, Integer pieces, BigDecimal cbm,
            String marksAndNumbers, FreightTerms freightTerms,
            Instant issuedAt, String issuedBy, Instant voidedAt,
            UUID supersededByHouseBolId, String amendmentReason, String pdfReference,
            String amendmentBlockedReason,
            Originals originals, List<Distribution> distributions
    ) {
        public static House from(HouseBOL b) {
            return new House(b.getId(), b.getBookingId(), b.getMasterBolId(),
                    b.getHouseBolNumber(), b.getRevisionNumber(), b.isActive(), b.getStatus(),
                    b.getReleaseType(), b.getReleaseTypeConfirmedAt(), b.getReleaseTypeConfirmedBy(),
                    b.getShipperNameSnapshot(), b.getShipperAddressSnapshot(),
                    b.getConsigneeNameSnapshot(), b.getConsigneeAddressSnapshot(),
                    b.getNotifyPartyNameSnapshot(), b.getNotifyPartyAddressSnapshot(),
                    b.getPortOfLoadingCode(), b.getPortOfDischargeCode(),
                    b.getVesselName(), b.getVoyageNumber(),
                    b.getContainerNumber(), b.getSealNumber(),
                    b.getCargoDescription(), b.getHsCode(),
                    b.getWeightKg(), b.getPieces(), b.getCbm(),
                    b.getMarksAndNumbers(), b.getFreightTerms(),
                    b.getIssuedAt(), b.getIssuedBy(), b.getVoidedAt(),
                    b.getSupersededByHouseBolId(), b.getAmendmentReason(), b.getPdfReference(),
                    b.amendmentBlockedReason(),
                    Originals.from(b.getOriginalBolTracking()),
                    b.getDistributions().stream().map(Distribution::from).toList());
        }
    }
}
