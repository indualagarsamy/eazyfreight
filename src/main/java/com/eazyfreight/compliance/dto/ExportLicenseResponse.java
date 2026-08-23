package com.eazyfreight.compliance.dto;

import com.eazyfreight.compliance.domain.ExportLicense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ExportLicenseResponse(
        UUID id,
        String licenseNumber,
        String issuingAuthority,
        String licenseType,
        String commodityEccn,
        LocalDate validFrom,
        LocalDate validUntil,
        BigDecimal valueAuthorized
) {
    public static ExportLicenseResponse fromEntity(ExportLicense license) {
        if (license == null) {
            return null;
        }
        return new ExportLicenseResponse(
                license.getId(),
                license.getLicenseNumber(),
                license.getIssuingAuthority(),
                license.getLicenseType(),
                license.getCommodityEccn(),
                license.getValidFrom(),
                license.getValidUntil(),
                license.getValueAuthorized()
        );
    }
}
