package com.eazyfreight.compliance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecordExportLicenseRequest(
        @NotBlank(message = "licenseNumber is required") String licenseNumber,
        @NotBlank(message = "issuingAuthority is required") String issuingAuthority,
        @NotBlank(message = "licenseType is required") String licenseType,
        String commodityEccn,
        @NotNull(message = "validFrom is required") LocalDate validFrom,
        @NotNull(message = "validUntil is required") LocalDate validUntil,
        BigDecimal valueAuthorized
) {
}
