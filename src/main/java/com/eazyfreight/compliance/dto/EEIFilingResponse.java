package com.eazyfreight.compliance.dto;

import com.eazyfreight.compliance.domain.EEIFiling;
import com.eazyfreight.compliance.domain.FilingStatus;
import com.eazyfreight.compliance.domain.FilingType;
import com.eazyfreight.compliance.domain.ItnRecord;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * @param scheduleBTranslated false when the Schedule B number is an untranslated HS
 *                            code carried across the CBP boundary as-is
 * @param simulated           true when the accepted ITN came from the local
 *                            simulator rather than CBP
 * @param missingRequiredFields CBP-mandated fields still blank; submission is
 *                            refused while this is non-empty
 */
public record EEIFilingResponse(
        UUID id,
        UUID bookingId,
        String filingReference,
        FilingType filingType,
        UUID parentFilingId,
        FilingStatus status,
        String shipperName,
        String shipperEin,
        String shipperAddress,
        String consigneeName,
        String consigneeAddress,
        String consigneeCountry,
        String scheduleBNumber,
        boolean scheduleBTranslated,
        String commodityDescription,
        BigDecimal quantityValue,
        String quantityUnit,
        BigDecimal valueUsd,
        String carrierScac,
        String vesselName,
        String voyageNumber,
        String portOfExportCode,
        String countryOfDestination,
        LocalDate estimatedEtd,
        Instant submittedAt,
        String submittedBy,
        Instant acceptedAt,
        Instant rejectedAt,
        String rejectionReasonCode,
        String rejectionReasonDescription,
        Instant cancelledAt,
        String cancellationReason,
        String aesSubmissionReference,
        boolean simulated,
        String amendmentReason,
        boolean licenseRequired,
        boolean filingRequired,
        List<String> missingRequiredFields,
        String activeItnNumber,
        Instant createdAt,
        ExportLicenseResponse exportLicense,
        List<ItnRecordResponse> itnRecords,
        List<EEIFilingHistoryResponse> history
) {
    public static EEIFilingResponse fromEntity(EEIFiling filing) {
        return new EEIFilingResponse(
                filing.getId(),
                filing.getBookingId(),
                filing.getFilingReference(),
                filing.getFilingType(),
                filing.getParentFilingId(),
                filing.getStatus(),
                filing.getShipperName(),
                filing.getShipperEin(),
                filing.getShipperAddress(),
                filing.getConsigneeName(),
                filing.getConsigneeAddress(),
                filing.getConsigneeCountry(),
                filing.getScheduleBNumber(),
                filing.isScheduleBTranslated(),
                filing.getCommodityDescription(),
                filing.getQuantityValue(),
                filing.getQuantityUnit(),
                filing.getValueUsd(),
                filing.getCarrierScac(),
                filing.getVesselName(),
                filing.getVoyageNumber(),
                filing.getPortOfExportCode(),
                filing.getCountryOfDestination(),
                filing.getEstimatedEtd(),
                filing.getSubmittedAt(),
                filing.getSubmittedBy(),
                filing.getAcceptedAt(),
                filing.getRejectedAt(),
                filing.getRejectionReasonCode(),
                filing.getRejectionReasonDescription(),
                filing.getCancelledAt(),
                filing.getCancellationReason(),
                filing.getAesSubmissionReference(),
                filing.isSimulated(),
                filing.getAmendmentReason(),
                filing.isLicenseRequired(),
                filing.isFilingRequired(),
                filing.missingRequiredFields(),
                filing.activeItn().map(ItnRecord::getItnNumber).orElse(null),
                filing.getCreatedAt(),
                ExportLicenseResponse.fromEntity(filing.getExportLicense()),
                filing.getItnRecords().stream().map(ItnRecordResponse::fromEntity).toList(),
                filing.getHistory().stream().map(EEIFilingHistoryResponse::fromEntity).toList()
        );
    }
}
