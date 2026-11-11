package com.jcondotta.scopedlogging.domain.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogKeyTest {

    private static final String BUSINESS_ATTRIBUTE_KEY = "post.id";

    @Test
    void shouldMarkOperationAsReserved_whenKeyIsOperation() {
        assertThat(LogKey.isReserved(LogKey.OPERATION)).isTrue();
    }

    @Test
    void shouldMarkOutcomeAsReserved_whenKeyIsOutcome() {
        assertThat(LogKey.isReserved(LogKey.OUTCOME)).isTrue();
    }

    @Test
    void shouldMarkDurationAsReserved_whenKeyIsDuration() {
        assertThat(LogKey.isReserved(LogKey.DURATION_MS)).isTrue();
    }

    @Test
    void shouldKeepReasonAvailable_whenKeyIsReason() {
        assertThat(LogKey.isReserved(LogKey.REASON)).isFalse();
    }

    @Test
    void shouldKeepBusinessAttributeAvailable_whenKeyIsNotStructural() {
        assertThat(LogKey.isReserved(BUSINESS_ATTRIBUTE_KEY)).isFalse();
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    void shouldReturnFalse_whenKeyIsNull() {
        assertThat(LogKey.isReserved(null)).isFalse();
    }
}
