package com.eazyfreight.documentation.controller;

import com.eazyfreight.documentation.dto.DocumentationRequests;
import com.eazyfreight.documentation.dto.DocumentationResponses;
import com.eazyfreight.documentation.model.AmendHouseBOL;
import com.eazyfreight.documentation.model.CarrierQuery;
import com.eazyfreight.documentation.model.CompileInstructions;
import com.eazyfreight.documentation.model.Discrepancy;
import com.eazyfreight.documentation.model.Distribute;
import com.eazyfreight.documentation.model.DistributionChannel;
import com.eazyfreight.documentation.model.DistributionRecipient;
import com.eazyfreight.documentation.model.FreightTerms;
import com.eazyfreight.documentation.model.GenerateHouseBOL;
import com.eazyfreight.documentation.model.House;
import com.eazyfreight.documentation.model.HouseBOLStatus;
import com.eazyfreight.documentation.model.Instructions;
import com.eazyfreight.documentation.model.InstructionsStatus;
import com.eazyfreight.documentation.model.Master;
import com.eazyfreight.documentation.model.MasterBOLCorrection;
import com.eazyfreight.documentation.model.MasterBOLReceived;
import com.eazyfreight.documentation.model.Originals;
import com.eazyfreight.documentation.model.Preconditions;
import com.eazyfreight.documentation.model.ReleaseOriginals;
import com.eazyfreight.documentation.model.ReleaseType;
import com.eazyfreight.documentation.model.SurrenderOriginals;
import com.eazyfreight.documentation.model.VerificationStatus;
import com.eazyfreight.documentation.model.VoidHouseBOL;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** Converts between Documentation domain DTOs and the generated OpenAPI models. */
final class DocumentationApiMapper {

    private DocumentationApiMapper() {
    }

    // ------------------------------------------------------------- responses

    static Preconditions toApi(DocumentationResponses.Preconditions p) {
        Preconditions view = new Preconditions();
        view.setBookingId(p.bookingId());
        view.setMet(p.met());
        view.setContainerNumber(p.containerNumber());
        view.setSealNumber(p.sealNumber());
        view.setItnNumber(p.itnNumber());
        view.setCarrierBookingRef(p.carrierBookingRef());
        view.setMissing(p.missing());
        return view;
    }

    static Instructions toApi(DocumentationResponses.Instructions i) {
        Instructions view = new Instructions();
        view.setId(i.id());
        view.setBookingId(i.bookingId());
        view.setInstructionsReference(i.instructionsReference());
        view.setStatus(mapEnum(i.status(), InstructionsStatus.class));
        view.setSupersedesInstructionsId(i.supersedesInstructionsId());
        view.setCarrierBookingRef(i.carrierBookingRef());
        view.setContainerNumber(i.containerNumber());
        view.setSealNumber(i.sealNumber());
        view.setItnNumber(i.itnNumber());
        view.setShipperName(i.shipperName());
        view.setShipperAddress(i.shipperAddress());
        view.setConsigneeName(i.consigneeName());
        view.setConsigneeAddress(i.consigneeAddress());
        view.setNotifyPartyName(i.notifyPartyName());
        view.setNotifyPartyAddress(i.notifyPartyAddress());
        view.setPortOfLoadingCode(i.portOfLoadingCode());
        view.setPortOfDischargeCode(i.portOfDischargeCode());
        view.setVesselName(i.vesselName());
        view.setVoyageNumber(i.voyageNumber());
        view.setCargoDescription(i.cargoDescription());
        view.setHsCode(i.hsCode());
        view.setActualWeightKg(i.actualWeightKg());
        view.setActualPieces(i.actualPieces());
        view.setActualCbm(i.actualCbm());
        view.setMarksAndNumbers(i.marksAndNumbers());
        view.setFreightTerms(mapEnum(i.freightTerms(), FreightTerms.class));
        view.setDraftedAt(toOffsetDateTime(i.draftedAt()));
        view.setDraftedBy(i.draftedBy());
        view.setApprovedAt(toOffsetDateTime(i.approvedAt()));
        view.setApprovedBy(i.approvedBy());
        view.setSentAt(toOffsetDateTime(i.sentAt()));
        view.setDocumentationCutOffDate(i.documentationCutOffDate());
        view.setSentAfterCutOff(i.sentAfterCutOff());
        view.setCarrierQuery(i.carrierQuery());
        return view;
    }

    static Master toApi(DocumentationResponses.Master m) {
        Master view = new Master();
        view.setId(m.id());
        view.setBookingId(m.bookingId());
        view.setInstructionsId(m.instructionsId());
        view.setMasterBolNumber(m.masterBolNumber());
        view.setIssuedByCarrierAt(toOffsetDateTime(m.issuedByCarrierAt()));
        view.setReceivedAt(toOffsetDateTime(m.receivedAt()));
        view.setReceivedBy(m.receivedBy());
        view.setVerificationStatus(mapEnum(m.verificationStatus(), VerificationStatus.class));
        view.setVerifiedAt(toOffsetDateTime(m.verifiedAt()));
        view.setVerifiedBy(m.verifiedBy());
        view.setDiscrepancyFields(m.discrepancyFields());
        view.setDiscrepancyRaisedAt(toOffsetDateTime(m.discrepancyRaisedAt()));
        view.setDiscrepancyResolvedAt(toOffsetDateTime(m.discrepancyResolvedAt()));
        view.setDocumentFileReference(m.documentFileReference());
        view.setVerified(m.verified());
        return view;
    }

    private static Originals toApi(DocumentationResponses.Originals o) {
        if (o == null) {
            return null;
        }
        Originals view = new Originals();
        view.setId(o.id());
        view.setOriginalsIssued(o.originalsIssued());
        view.setOriginalsSurrendered(o.originalsSurrendered());
        view.setOutstanding(o.outstanding());
        view.setAllSurrendered(o.allSurrendered());
        view.setReleasedAt(toOffsetDateTime(o.releasedAt()));
        view.setReleasedTo(o.releasedTo());
        view.setCourierReference(o.courierReference());
        view.setSurrenderedAt(toOffsetDateTime(o.surrenderedAt()));
        return view;
    }

    private static com.eazyfreight.documentation.model.Distribution toApi(DocumentationResponses.Distribution d) {
        com.eazyfreight.documentation.model.Distribution view = new com.eazyfreight.documentation.model.Distribution();
        view.setId(d.id());
        view.setRecipient(mapEnum(d.recipient(), DistributionRecipient.class));
        view.setRecipientName(d.recipientName());
        view.setRecipientAddress(d.recipientAddress());
        view.setChannel(mapEnum(d.channel(), DistributionChannel.class));
        view.setRevisionNumber(d.revisionNumber());
        view.setSentAt(toOffsetDateTime(d.sentAt()));
        view.setSentBy(d.sentBy());
        view.setReference(d.reference());
        return view;
    }

    static House toApi(DocumentationResponses.House b) {
        House view = new House();
        view.setId(b.id());
        view.setBookingId(b.bookingId());
        view.setMasterBolId(b.masterBolId());
        view.setHouseBolNumber(b.houseBolNumber());
        view.setRevisionNumber(b.revisionNumber());
        view.setActive(b.active());
        view.setStatus(mapEnum(b.status(), HouseBOLStatus.class));
        view.setReleaseType(mapEnum(b.releaseType(), ReleaseType.class));
        view.setReleaseTypeConfirmedAt(toOffsetDateTime(b.releaseTypeConfirmedAt()));
        view.setReleaseTypeConfirmedBy(b.releaseTypeConfirmedBy());
        view.setShipperNameSnapshot(b.shipperNameSnapshot());
        view.setShipperAddressSnapshot(b.shipperAddressSnapshot());
        view.setConsigneeNameSnapshot(b.consigneeNameSnapshot());
        view.setConsigneeAddressSnapshot(b.consigneeAddressSnapshot());
        view.setNotifyPartyNameSnapshot(b.notifyPartyNameSnapshot());
        view.setNotifyPartyAddressSnapshot(b.notifyPartyAddressSnapshot());
        view.setPortOfLoadingCode(b.portOfLoadingCode());
        view.setPortOfDischargeCode(b.portOfDischargeCode());
        view.setVesselName(b.vesselName());
        view.setVoyageNumber(b.voyageNumber());
        view.setContainerNumber(b.containerNumber());
        view.setSealNumber(b.sealNumber());
        view.setCargoDescription(b.cargoDescription());
        view.setHsCode(b.hsCode());
        view.setWeightKg(b.weightKg());
        view.setPieces(b.pieces());
        view.setCbm(b.cbm());
        view.setMarksAndNumbers(b.marksAndNumbers());
        view.setFreightTerms(mapEnum(b.freightTerms(), FreightTerms.class));
        view.setIssuedAt(toOffsetDateTime(b.issuedAt()));
        view.setIssuedBy(b.issuedBy());
        view.setVoidedAt(toOffsetDateTime(b.voidedAt()));
        view.setSupersededByHouseBolId(b.supersededByHouseBolId());
        view.setAmendmentReason(b.amendmentReason());
        view.setPdfReference(b.pdfReference());
        view.setAmendmentBlockedReason(b.amendmentBlockedReason());
        view.setOriginals(toApi(b.originals()));
        view.setDistributions(b.distributions().stream().map(DocumentationApiMapper::toApi).toList());
        return view;
    }

    // -------------------------------------------------------------- requests

    static DocumentationRequests.CompileInstructions toDomain(CompileInstructions r) {
        return new DocumentationRequests.CompileInstructions(
                r.getShipperName(), r.getShipperAddress(),
                r.getConsigneeName(), r.getConsigneeAddress(),
                r.getNotifyPartyName(), r.getNotifyPartyAddress(),
                r.getMarksAndNumbers(),
                mapEnum(r.getFreightTerms(), com.eazyfreight.documentation.domain.DocumentationEnums.FreightTerms.class),
                r.getDocumentationCutOffDate());
    }

    static DocumentationRequests.CarrierQuery toDomain(CarrierQuery r) {
        return new DocumentationRequests.CarrierQuery(r.getQuery());
    }

    static DocumentationRequests.MasterBOLReceived toDomain(MasterBOLReceived r) {
        return new DocumentationRequests.MasterBOLReceived(
                r.getMasterBolNumber(), toInstant(r.getIssuedByCarrierAt()), r.getDocumentFileReference());
    }

    static DocumentationRequests.Discrepancy toDomain(Discrepancy r) {
        return new DocumentationRequests.Discrepancy(r.getDiscrepancyFields());
    }

    static DocumentationRequests.MasterBOLCorrection toDomain(MasterBOLCorrection r) {
        if (r == null) {
            return new DocumentationRequests.MasterBOLCorrection(null, null);
        }
        return new DocumentationRequests.MasterBOLCorrection(r.getCorrectedMasterBolNumber(), r.getDocumentFileReference());
    }

    static DocumentationRequests.GenerateHouseBOL toDomain(GenerateHouseBOL r) {
        return new DocumentationRequests.GenerateHouseBOL(
                mapEnum(r.getReleaseType(), com.eazyfreight.documentation.domain.DocumentationEnums.ReleaseType.class),
                mapEnum(r.getFreightTerms(), com.eazyfreight.documentation.domain.DocumentationEnums.FreightTerms.class));
    }

    static DocumentationRequests.Distribute toDomain(Distribute r) {
        return new DocumentationRequests.Distribute(
                mapEnum(r.getRecipient(), com.eazyfreight.documentation.domain.DocumentationEnums.DistributionRecipient.class),
                r.getRecipientName(), r.getRecipientAddress(),
                mapEnum(r.getChannel(), com.eazyfreight.documentation.domain.DocumentationEnums.DistributionChannel.class),
                r.getReference());
    }

    static DocumentationRequests.ReleaseOriginals toDomain(ReleaseOriginals r) {
        return new DocumentationRequests.ReleaseOriginals(r.getReleasedTo(), r.getCourierReference());
    }

    static DocumentationRequests.SurrenderOriginals toDomain(SurrenderOriginals r) {
        return new DocumentationRequests.SurrenderOriginals(r.getCount());
    }

    static DocumentationRequests.AmendHouseBOL toDomain(AmendHouseBOL r) {
        return new DocumentationRequests.AmendHouseBOL(
                r.getReason(), r.getConsigneeName(), r.getConsigneeAddress(),
                r.getNotifyPartyName(), r.getNotifyPartyAddress(),
                r.getVesselName(), r.getVoyageNumber(), r.getSealNumber(),
                r.getCargoDescription(), r.getWeightKg(), r.getPieces(), r.getCbm(),
                mapEnum(r.getReleaseType(), com.eazyfreight.documentation.domain.DocumentationEnums.ReleaseType.class));
    }

    static DocumentationRequests.VoidHouseBOL toDomain(VoidHouseBOL r) {
        return new DocumentationRequests.VoidHouseBOL(r.getReason());
    }

    // ---------------------------------------------------------------- helpers

    private static Instant toInstant(OffsetDateTime dateTime) {
        return dateTime == null ? null : dateTime.toInstant();
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static <S extends Enum<S>, T extends Enum<T>> T mapEnum(S source, Class<T> targetType) {
        return source == null ? null : Enum.valueOf(targetType, source.name());
    }
}
