package com.eazyfreight.quote.client;

import com.eazyfreight.quote.domain.EntityType;

/**
 * Outbound port to the denied party screening service (OFAC SDN, CBP denied party
 * lists). Modelled as an interface so the compliance gate is part of the domain
 * flow rather than something a staff member remembers to do on a government
 * website.
 */
public interface DeniedPartyScreeningClient {

    /**
     * @throws ScreeningServiceUnavailableException when the provider cannot be
     *         reached — the caller must leave the quote blocked rather than assume
     *         a clear result.
     */
    ScreeningResult screen(ScreeningRequest request);

    record ScreeningRequest(
            String name,
            String address,
            String country,
            EntityType entityType
    ) {
    }

    class ScreeningServiceUnavailableException extends RuntimeException {
        public ScreeningServiceUnavailableException(String message) {
            super(message);
        }
    }
}
