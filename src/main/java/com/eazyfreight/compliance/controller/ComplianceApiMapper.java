package com.eazyfreight.compliance.controller;

import com.eazyfreight.compliance.dto.AmendFilingRequest;
import com.eazyfreight.compliance.dto.CancelFilingRequest;
import com.eazyfreight.compliance.dto.CompileEEIDataRequest;
import com.eazyfreight.compliance.dto.EEIFilingHistoryResponse;
import com.eazyfreight.compliance.dto.EEIFilingResponse;
import com.eazyfreight.compliance.dto.ExportLicenseResponse;
import com.eazyfreight.compliance.dto.ItnRecordResponse;
import com.eazyfreight.compliance.dto.RecordAcceptanceRequest;
import com.eazyfreight.compliance.dto.RecordExportLicenseRequest;
import com.eazyfreight.compliance.dto.RecordRejectionRequest;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Converts between Compliance domain-facing DTOs and the generated OpenAPI models. */
final class ComplianceApiMapper {

    private ComplianceApiMapper() {
    }

    static com.eazyfreight.compliance.model.EEIFilingResponse toModel(EEIFilingResponse response) {
        if (response == null) {
            return null;
        }
        com.eazyfreight.compliance.model.EEIFilingResponse model = new com.eazyfreight.compliance.model.EEIFilingResponse();
        model.setId(response.id());
        model.setBookingId(response.bookingId());
        model.setFilingReference(response.filingReference());
        model.setFilingType(mapEnum(response.filingType(), com.eazyfreight.compliance.model.FilingType.class));
        model.setParentFilingId(response.parentFilingId());
        model.setStatus(mapEnum(response.status(), com.eazyfreight.compliance.model.FilingStatus.class));
        model.setShipperName(response.shipperName());
        model.setShipperEin(response.shipperEin());
        model.setShipperAddress(response.shipperAddress());
        model.setConsigneeName(response.consigneeName());
        model.setConsigneeAddress(response.consigneeAddress());
        model.setConsigneeCountry(response.consigneeCountry());
        model.setScheduleBNumber(response.scheduleBNumber());
        model.setScheduleBTranslated(response.scheduleBTranslated());
        model.setCommodityDescription(response.commodityDescription());
        model.setQuantityValue(response.quantityValue());
        model.setQuantityUnit(response.quantityUnit());
        model.setValueUsd(response.valueUsd());
        model.setCarrierScac(response.carrierScac());
        model.setVesselName(response.vesselName());
        model.setVoyageNumber(response.voyageNumber());
        model.setPortOfExportCode(response.portOfExportCode());
        model.setCountryOfDestination(response.countryOfDestination());
        model.setEstimatedEtd(response.estimatedEtd());
        model.setSubmittedAt(toOffsetDateTime(response.submittedAt()));
        model.setSubmittedBy(response.submittedBy());
        model.setAcceptedAt(toOffsetDateTime(response.acceptedAt()));
        model.setRejectedAt(toOffsetDateTime(response.rejectedAt()));
        model.setRejectionReasonCode(response.rejectionReasonCode());
        model.setRejectionReasonDescription(response.rejectionReasonDescription());
        model.setCancelledAt(toOffsetDateTime(response.cancelledAt()));
        model.setCancellationReason(response.cancellationReason());
        model.setAesSubmissionReference(response.aesSubmissionReference());
        model.setSimulated(response.simulated());
        model.setAmendmentReason(response.amendmentReason());
        model.setLicenseRequired(response.licenseRequired());
        model.setFilingRequired(response.filingRequired());
        model.setMissingRequiredFields(response.missingRequiredFields());
        model.setActiveItnNumber(response.activeItnNumber());
        model.setCreatedAt(toOffsetDateTime(response.createdAt()));
        model.setExportLicense(toModel(response.exportLicense()));
        model.setItnRecords(response.itnRecords().stream().map(ComplianceApiMapper::toModel).toList());
        model.setHistory(response.history().stream().map(ComplianceApiMapper::toModel).toList());
        return model;
    }

    private static com.eazyfreight.compliance.model.ExportLicenseResponse toModel(ExportLicenseResponse response) {
        if (response == null) {
            return null;
        }
        com.eazyfreight.compliance.model.ExportLicenseResponse model = new com.eazyfreight.compliance.model.ExportLicenseResponse();
        model.setId(response.id());
        model.setLicenseNumber(response.licenseNumber());
        model.setIssuingAuthority(response.issuingAuthority());
        model.setLicenseType(response.licenseType());
        model.setCommodityEccn(response.commodityEccn());
        model.setValidFrom(response.validFrom());
        model.setValidUntil(response.validUntil());
        model.setValueAuthorized(response.valueAuthorized());
        return model;
    }

    private static com.eazyfreight.compliance.model.ItnRecordResponse toModel(ItnRecordResponse response) {
        com.eazyfreight.compliance.model.ItnRecordResponse model = new com.eazyfreight.compliance.model.ItnRecordResponse();
        model.setId(response.id());
        model.setItnNumber(response.itnNumber());
        model.setIssuedAt(toOffsetDateTime(response.issuedAt()));
        model.setActive(response.active());
        model.setSimulated(response.simulated());
        model.setSupersededByItnId(response.supersededByItnId());
        model.setRecordedAt(toOffsetDateTime(response.recordedAt()));
        model.setRecordedBy(response.recordedBy());
        return model;
    }

    private static com.eazyfreight.compliance.model.EEIFilingHistoryResponse toModel(EEIFilingHistoryResponse response) {
        com.eazyfreight.compliance.model.EEIFilingHistoryResponse model = new com.eazyfreight.compliance.model.EEIFilingHistoryResponse();
        model.setId(response.id());
        model.setSequenceNumber(response.sequenceNumber());
        model.setFromStatus(mapEnum(response.fromStatus(), com.eazyfreight.compliance.model.FilingStatus.class));
        model.setToStatus(mapEnum(response.toStatus(), com.eazyfreight.compliance.model.FilingStatus.class));
        model.setOccurredAt(toOffsetDateTime(response.occurredAt()));
        model.setActor(response.actor());
        model.setDetail(response.detail());
        return model;
    }

    static CompileEEIDataRequest toDto(com.eazyfreight.compliance.model.CompileEEIDataRequest request) {
        return new CompileEEIDataRequest(
                request.getShipperName(),
                request.getShipperEin(),
                request.getShipperAddress(),
                request.getConsigneeName(),
                request.getConsigneeAddress(),
                request.getConsigneeCountry(),
                request.getScheduleBNumber(),
                request.getCommodityDescription(),
                request.getQuantityValue(),
                request.getQuantityUnit(),
                request.getValueUsd(),
                request.getCarrierScac(),
                request.getVesselName(),
                request.getVoyageNumber(),
                request.getPortOfExportCode(),
                request.getCountryOfDestination(),
                request.getEstimatedEtd(),
                Boolean.TRUE.equals(request.getLicenseRequired()));
    }

    static RecordAcceptanceRequest toDto(com.eazyfreight.compliance.model.RecordAcceptanceRequest request) {
        return new RecordAcceptanceRequest(
                request.getItnNumber(),
                request.getAesSubmissionReference(),
                toInstant(request.getAcceptedAt()));
    }

    static RecordRejectionRequest toDto(com.eazyfreight.compliance.model.RecordRejectionRequest request) {
        return new RecordRejectionRequest(
                request.getRejectionCode(),
                request.getRejectionDescription(),
                toInstant(request.getRejectedAt()));
    }

    static AmendFilingRequest toDto(com.eazyfreight.compliance.model.AmendFilingRequest request) {
        return new AmendFilingRequest(request.getReason());
    }

    static CancelFilingRequest toDto(com.eazyfreight.compliance.model.CancelFilingRequest request) {
        return new CancelFilingRequest(request.getReason());
    }

    static RecordExportLicenseRequest toDto(com.eazyfreight.compliance.model.RecordExportLicenseRequest request) {
        return new RecordExportLicenseRequest(
                request.getLicenseNumber(),
                request.getIssuingAuthority(),
                request.getLicenseType(),
                request.getCommodityEccn(),
                request.getValidFrom(),
                request.getValidUntil(),
                request.getValueAuthorized());
    }

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
