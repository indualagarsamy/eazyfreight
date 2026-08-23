package com.eazyfreight.compliance.client;

import com.eazyfreight.compliance.domain.ItnNumber;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Local stand-in for the CBP filing system. Fabricates acceptances and rejections
 * so the compliance track is exercisable end to end. Nothing leaves the process.
 *
 * <p>Issued ITNs use 99999999 where a genuine ITN carries the filing date, so a
 * simulated number is recognisable at a glance and {@link ItnNumber#isSimulated()}
 * can assert on it.
 *
 * <p>Rejection is triggered deterministically rather than randomly, so demos and
 * tests can reach the rejection path on purpose: a commodity description containing
 * REJECT comes back with CBP error code 127, and one containing UNAVAILABLE raises
 * {@link AesFilingClient.AesUnavailableException}.
 */
@Component
@Slf4j
public class SimulatedAesFilingClient implements AesFilingClient {

    private static final String REJECT_MARKER = "REJECT";
    private static final String UNAVAILABLE_MARKER = "UNAVAILABLE";
    private static final BigDecimal FILING_THRESHOLD_USD = new BigDecimal("2500");

    private final Clock clock;
    private final AtomicLong sequence = new AtomicLong();

    public SimulatedAesFilingClient(Clock clock) {
        this.clock = clock;
    }

    @PostConstruct
    void announce() {
        log.warn("AES filing is SIMULATED. No Electronic Export Information is transmitted "
                + "to CBP, and every ITN issued is fabricated. Do not treat any filing in "
                + "this system as a report to the United States government.");
    }

    @Override
    public boolean isSimulated() {
        return true;
    }

    @Override
    public AesResponse submit(AesSubmission submission) {
        String description = submission.commodityDescription() == null
                ? "" : submission.commodityDescription().toUpperCase(Locale.ROOT);

        if (description.contains(UNAVAILABLE_MARKER)) {
            throw new AesUnavailableException("Simulated AES outage for " + submission.filingReference());
        }
        if (description.contains(REJECT_MARKER)) {
            return AesResponse.rejected("127",
                    "Commodity description not valid for the Schedule B number provided",
                    clock.instant());
        }
        if (submission.valueUsd() != null
                && submission.valueUsd().compareTo(FILING_THRESHOLD_USD) <= 0) {
            // Mirrors a real CBP rejection: a filing below the threshold with no
            // licence is not required, and AES rejects it as unnecessary.
            if (submission.exportLicenseNumber() == null) {
                return AesResponse.rejected("015",
                        "Filing not required: value at or below $2,500 per Schedule B "
                                + "number and no export licence cited",
                        clock.instant());
            }
        }

        long next = sequence.incrementAndGet();
        String itn = "X%s%06d".formatted(ItnNumber.SIMULATED_DATE_PART, next);
        String submissionRef = "AES-SIM-%06d".formatted(next);
        log.info("Simulated AES acceptance for {} -> {}", submission.filingReference(), itn);
        return AesResponse.accepted(itn, submissionRef, clock.instant());
    }
}
