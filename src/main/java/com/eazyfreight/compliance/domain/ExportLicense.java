package com.eazyfreight.compliance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An export licence cited on a filing. Some commodities cannot be filed without
 * one, and submission is blocked until it is recorded.
 *
 * <p>The specification is inconsistent here: the RecordExportLicense command takes
 * a bookingId, but the aggregate boundaries define the licence as a child of the
 * filing. It is keyed to the filing, since the licence has to travel with the
 * filing that cites it; the service accepts a booking id as a lookup convenience.
 */
@Entity
@Table(name = "export_licenses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExportLicense {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "license_number", nullable = false, length = 64)
    private String licenseNumber;

    @Column(name = "issuing_authority", nullable = false, length = 64)
    private String issuingAuthority;

    @Column(name = "license_type", nullable = false, length = 64)
    private String licenseType;

    /** Export Control Classification Number for the commodity. */
    @Column(name = "commodity_eccn", length = 32)
    private String commodityEccn;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Column(name = "value_authorized", precision = 14, scale = 2)
    private BigDecimal valueAuthorized;

    public static ExportLicense of(
            String licenseNumber,
            String issuingAuthority,
            String licenseType,
            String commodityEccn,
            LocalDate validFrom,
            LocalDate validUntil,
            BigDecimal valueAuthorized
    ) {
        ExportLicense license = new ExportLicense();
        license.licenseNumber = licenseNumber;
        license.issuingAuthority = issuingAuthority;
        license.licenseType = licenseType;
        license.commodityEccn = commodityEccn;
        license.validFrom = validFrom;
        license.validUntil = validUntil;
        license.valueAuthorized = valueAuthorized;
        return license;
    }

    public boolean isValidOn(LocalDate date) {
        return !date.isBefore(validFrom) && !date.isAfter(validUntil);
    }
}
