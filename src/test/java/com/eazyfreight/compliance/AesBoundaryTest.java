package com.eazyfreight.compliance;

import com.eazyfreight.support.FixedClockConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the CBP boundary.
 *
 * <p>Submitting an EEI is a filing with the United States government. This test
 * fails the build if a second {@link AesFilingClient} appears, if the wired client
 * stops reporting itself as simulated, or if an endpoint property shows up that
 * something could be pointed at. Adding a real filing client is then a deliberate
 * act with a failing test attached, not a quiet change of wiring.
 */
@SpringBootTest
@Import(FixedClockConfiguration.class)
class AesBoundaryTest {

    @Autowired
    private Map<String, AesFilingClient> clients;

    @Autowired
    private AesFilingClient wiredClient;

    @Autowired
    private Environment environment;

    @Test
    void theOnlyFilingClientIsTheSimulator() {
        assertThat(clients)
                .as("a second AesFilingClient would be able to transmit to CBP")
                .hasSize(1);
        assertThat(clients.values().iterator().next()).isInstanceOf(SimulatedAesFilingClient.class);
    }

    @Test
    void theWiredClientReportsItselfAsSimulated() {
        assertThat(wiredClient.isSimulated()).isTrue();
    }

    @Test
    void noAesEndpointIsConfigured() {
        for (String property : new String[]{
                "eazyfreight.aes.url", "eazyfreight.aes.endpoint", "eazyfreight.cbp.url",
                "aes.url", "cbp.endpoint"}) {
            assertThat(environment.getProperty(property))
                    .as("%s must not exist — there is nothing to point at CBP", property)
                    .isNull();
        }
    }

    @Test
    void issuedItnsAreRecognisablySimulated() {
        AesFilingClient.AesResponse response = wiredClient.submit(submission("Machine parts"));

        assertThat(response.accepted()).isTrue();
        ItnNumber itn = new ItnNumber(response.itnNumber());
        assertThat(itn.isSimulated())
                .as("a simulated ITN must never look like one CBP issued")
                .isTrue();
        assertThat(itn.value()).startsWith("X99999999");
    }

    private AesFilingClient.AesSubmission submission(String description) {
        return new AesFilingClient.AesSubmission(
                "EEI-2026-00001", FilingType.ORIGINAL, null,
                "12-3456789", "Acme Manufacturing", "Plot 14",
                "Singapore Trading Pte", "9 Raffles Place", "SG",
                "8471.30.0100", description, new java.math.BigDecimal("10"), "PCS",
                new java.math.BigDecimal("50000"), "MAEU", "MAERSK SEALAND", "024W",
                "INBOM", "SG", java.time.LocalDate.of(2026, 10, 15), null, null);
    }
}
