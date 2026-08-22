package com.eazyfreight.compliance;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Outbound port to the CBP filing system (AES via ACE, or a certified third-party
 * filer such as Descartes).
 *
 * <p><strong>No implementation of this interface may transmit to CBP.</strong>
 * Submitting an EEI is a filing with the United States government, lawful only for
 * a party holding an AES filer certification, and a false or duplicate filing is a
 * federal matter rather than a bug. The only implementation in this codebase is
 * {@link SimulatedAesFilingClient}, which fabricates responses locally. There is no
 * endpoint configured anywhere, and no property that would switch a live adapter
 * on, because no live adapter exists.
 *
 * <p>{@code AesBoundaryTest} fails the build if a second implementation appears, so
 * adding one is a deliberate act with a failing test attached rather than a quiet
 * change of wiring.
 */
public interface AesFilingClient {

    /**
     * @throws AesUnavailableException when the filing system cannot be reached. The
     *         caller must leave the filing submitted and retry — never infer
     *         acceptance from a failure to get an answer.
     */
    AesResponse submit(AesSubmission submission);

    /** Whether this client fabricates responses instead of contacting CBP. */
    boolean isSimulated();

    /** The core EEI fields, matching CBP's schema at the boundary. */
    record AesSubmission(
            String filingReference,
            FilingType filingType,
            String supersedesItnNumber,
            String shipperEin,
            String shipperName,
            String shipperAddress,
            String consigneeName,
            String consigneeAddress,
            String consigneeCountry,
            String scheduleBNumber,
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
            String exportLicenseNumber,
            BigDecimal exportLicenseValue
    ) {
    }

    /** Either an acceptance carrying an ITN, or a rejection carrying a CBP code. */
    record AesResponse(
            boolean accepted,
            String itnNumber,
            String aesSubmissionReference,
            String rejectionCode,
            String rejectionDescription,
            Instant respondedAt
    ) {
        public static AesResponse accepted(String itnNumber, String submissionRef, Instant at) {
            return new AesResponse(true, itnNumber, submissionRef, null, null, at);
        }

        public static AesResponse rejected(String code, String description, Instant at) {
            return new AesResponse(false, null, null, code, description, at);
        }
    }

    class AesUnavailableException extends RuntimeException {
        public AesUnavailableException(String message) {
            super(message);
        }
    }
}
