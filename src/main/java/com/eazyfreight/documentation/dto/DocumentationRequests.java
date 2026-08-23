package com.eazyfreight.documentation.dto;

import com.eazyfreight.documentation.domain.DocumentationEnums.DistributionChannel;
import com.eazyfreight.documentation.domain.DocumentationEnums.DistributionRecipient;
import com.eazyfreight.documentation.domain.DocumentationEnums.FreightTerms;
import com.eazyfreight.documentation.domain.DocumentationEnums.ReleaseType;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Request payloads for the Documentation commands. */
public final class DocumentationRequests {

    private DocumentationRequests() {
    }

    /**
     * Overrides for the instruction snapshot. Everything else is pulled from the
     * booking, logistics and compliance records at compile time.
     */
    public record CompileInstructions(
            String shipperName, String shipperAddress,
            String consigneeName, String consigneeAddress,
            String notifyPartyName, String notifyPartyAddress,
            String marksAndNumbers,
            FreightTerms freightTerms,
            LocalDate documentationCutOffDate
    ) {
    }

    public record CarrierQuery(
            @NotBlank(message = "query is required") String query
    ) {
    }

    public record MasterBOLReceived(
            @NotBlank(message = "masterBolNumber is required") String masterBolNumber,
            Instant issuedByCarrierAt,
            String documentFileReference
    ) {
    }

    public record Discrepancy(
            @NotEmpty(message = "discrepancyFields is required") List<String> discrepancyFields
    ) {
    }

    public record MasterBOLCorrection(
            String correctedMasterBolNumber,
            String documentFileReference
    ) {
    }

    public record GenerateHouseBOL(
            @NotNull(message = "releaseType is required") ReleaseType releaseType,
            FreightTerms freightTerms
    ) {
    }

    public record Distribute(
            @NotNull(message = "recipient is required") DistributionRecipient recipient,
            String recipientName,
            String recipientAddress,
            DistributionChannel channel,
            String reference
    ) {
    }

    public record ReleaseOriginals(
            @NotBlank(message = "releasedTo is required") String releasedTo,
            String courierReference
    ) {
    }

    public record SurrenderOriginals(
            @Min(value = 1, message = "count must be at least 1") int count
    ) {
    }

    public record AmendHouseBOL(
            @NotBlank(message = "reason is required") String reason,
            String consigneeName, String consigneeAddress,
            String notifyPartyName, String notifyPartyAddress,
            String vesselName, String voyageNumber, String sealNumber,
            String cargoDescription,
            BigDecimal weightKg, Integer pieces, BigDecimal cbm,
            ReleaseType releaseType
    ) {
    }

    public record VoidHouseBOL(
            @NotBlank(message = "reason is required") String reason
    ) {
    }
}
