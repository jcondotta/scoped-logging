package com.jcondotta.scopedlogging.domain.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class LogEventDeclarationTest {

    private static final String MESSAGE = "Needs review";

    @Test
    void shouldCreateDeclaration_whenCauseIsNull() {
        var declaration = new LogEventDeclaration(LogLevel.WARN, MESSAGE, null);

        assertThat(declaration.severity()).isEqualTo(LogLevel.WARN);
        assertThat(declaration.message()).isEqualTo(MESSAGE);
        assertThat(declaration.cause()).isNull();
    }

    @Test
    void shouldCreateDeclaration_whenCauseIsProvided() {
        var cause = new IllegalStateException("manual review required");

        var declaration = new LogEventDeclaration(LogLevel.ERROR, MESSAGE, cause);

        assertThat(declaration.severity()).isEqualTo(LogLevel.ERROR);
        assertThat(declaration.message()).isEqualTo(MESSAGE);
        assertThat(declaration.cause()).isSameAs(cause);
    }

    @Test
    void shouldThrowNullPointerException_whenSeverityIsNull() {
        assertThatNullPointerException()
            .isThrownBy(() -> new LogEventDeclaration(null, "message", null))
            .withMessage("log severity must not be null");
    }

    @Test
    void shouldThrowNullPointerException_whenMessageIsNull() {
        assertThatNullPointerException()
            .isThrownBy(() -> new LogEventDeclaration(LogLevel.INFO, null, null))
            .withMessage("log message must not be null");
    }
}
