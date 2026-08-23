package com.eazyfreight.compliance.domain;

import com.eazyfreight.exception.DomainRuleViolationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The format rule the monolith did not have — its ITNNo column accepted any string
 * anyone typed into it.
 */
class ItnNumberTest {

    @Test
    void acceptsXFollowedByFourteenDigits() {
        assertThat(new ItnNumber("X20240315123456").value()).isEqualTo("X20240315123456");
    }

    @Test
    void rejectsAnythingElse() {
        for (String invalid : new String[]{
                "X2024031512345",      // 13 digits
                "X202403151234567",    // 15 digits
                "20240315123456",      // no X
                "Y20240315123456",     // wrong prefix
                "X2024031512345A",     // not all digits
                "",
                null}) {
            assertThatThrownBy(() -> new ItnNumber(invalid))
                    .as("should reject %s", invalid)
                    .isInstanceOf(DomainRuleViolationException.class);
        }
    }

    @Test
    void distinguishesSimulatedFromGenuine() {
        assertThat(new ItnNumber("X99999999000001").isSimulated()).isTrue();
        assertThat(new ItnNumber("X20240315123456").isSimulated()).isFalse();
    }
}
