package com.jcondotta.scopedlogging.domain.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogOutcomeTest {

    @Test
    void shouldReturnSuccessValue_whenOutcomeIsSuccess() {
        assertThat(LogOutcome.SUCCESS.value()).isEqualTo("success");
    }

    @Test
    void shouldReturnFailureValue_whenOutcomeIsFailure() {
        assertThat(LogOutcome.FAILURE.value()).isEqualTo("failure");
    }
}
