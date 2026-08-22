package com.eazyfreight.compliance;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The EEI data entered by compliance staff.
 *
 * <p>Shipper EIN, carrier SCAC and consignee country are captured here rather than
 * resolved from the booking, because the Party and Carrier contexts do not exist —
 * the booking holds only opaque identifiers for those parties. This mirrors what
 * actually happens today, where staff key the same fields into the ACE portal.
 *
 * <p>Leave {@code scheduleBNumber} blank to carry the booking's HS code across the
 * boundary untranslated; it will be flagged as unmapped.
 */
public record CompileEEIDataRequest(
        @NotBlank(message = "shipperName is required") String shipperName,
        @NotBlank(message = "shipperEin is required") String shipperEin,
        String shipperAddress,
        @NotBlank(message = "consigneeName is required") String consigneeName,
        String consigneeAddress,
        @NotBlank(message = "consigneeCountry is required")
        @Pattern(regexp = "[A-Z]{2}", message = "consigneeCountry must be a 2-letter ISO code")
        String consigneeCountry,
        @Pattern(regexp = "\\d{4}\\.\\d{2}\\.\\d{4}|",
                message = "scheduleBNumber must match format NNNN.NN.NNNN")
        String scheduleBNumber,
        @NotBlank(message = "commodityDescription is required")
        @Size(max = 150, message = "commodityDescription exceeds CBP's 150 character limit")
        String commodityDescription,
        @NotNull(message = "quantityValue is required")
        @DecimalMin(value = "0.001", message = "quantityValue must be greater than 0")
        BigDecimal quantityValue,
        @NotBlank(message = "quantityUnit is required") String quantityUnit,
        @NotNull(message = "valueUsd is required")
        @DecimalMin(value = "0.00", message = "valueUsd cannot be negative") BigDecimal valueUsd,
        @NotBlank(message = "carrierScac is required")
        @Pattern(regexp = "[A-Z]{2,4}", message = "carrierScac must be 2-4 uppercase letters")
        String carrierScac,
        String vesselName,
        String voyageNumber,
        @NotBlank(message = "portOfExportCode is required") String portOfExportCode,
        @NotBlank(message = "countryOfDestination is required")
        @Pattern(regexp = "[A-Z]{2}", message = "countryOfDestination must be a 2-letter ISO code")
        String countryOfDestination,
        @NotNull(message = "estimatedEtd is required") LocalDate estimatedEtd,
        boolean licenseRequired
) {
}
