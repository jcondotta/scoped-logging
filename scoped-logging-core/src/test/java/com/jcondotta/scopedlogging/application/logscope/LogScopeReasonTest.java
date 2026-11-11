package com.jcondotta.scopedlogging.application.logscope;

import com.jcondotta.scopedlogging.domain.logging.LogEvent;
import com.jcondotta.scopedlogging.domain.logging.LogKey;
import com.jcondotta.scopedlogging.domain.logging.LogLevel;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LogScopeReasonTest {

    private static final String LOGGER_NAME = "reason-test";
    private static final String OPERATION = "operation";
    private static final String BUSINESS_REASON = "payment_declined";
    private static final String INTERNAL_ERROR = "internal_error";

    private final ScopedLogContext context = new ScopedLogContext();
    private final ArrayList<LogEvent> events = new ArrayList<>();
    private final LogScopeExecutor executor = new LogScopeExecutor(context, events::add);

    @Test
    void shouldAddInternalErrorReason_whenExceptionEscapesWithoutReason() {
        var event = executeFailingScope(() -> {});

        assertThat(event.attributes()).containsEntry(LogKey.REASON, INTERNAL_ERROR);
    }

    @Test
    void shouldPreserveNullReason_whenExceptionEscapesWithExplicitNullReason() {
        var event = executeFailingScope(() -> context.with(LogKey.REASON, null));

        assertThat(event.attributes()).containsEntry(LogKey.REASON, null);
        assertThatThrownBy(() -> event.attributes().put(LogKey.REASON, BUSINESS_REASON))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldPreserveReason_whenExceptionEscapesWithExplicitReason() {
        var event = executeFailingScope(() -> context.with(LogKey.REASON, BUSINESS_REASON));

        assertThat(event.attributes()).containsEntry(LogKey.REASON, BUSINESS_REASON);
    }

    @Test
    void shouldOmitReason_whenScopeReturnsWithoutReason() {
        var event = executeSuccessfulScope(() -> {});

        assertThat(event.attributes()).doesNotContainKey(LogKey.REASON);
    }

    @Test
    void shouldPreserveNullReason_whenScopeReturnsWithExplicitNullReason() {
        var event = executeSuccessfulScope(() -> context.with(LogKey.REASON, null));

        assertThat(event.attributes()).containsEntry(LogKey.REASON, null);
    }

    @Test
    void shouldPreserveReason_whenScopeReturnsWithExplicitReason() {
        var event = executeSuccessfulScope(() -> context.with(LogKey.REASON, BUSINESS_REASON));

        assertThat(event.attributes()).containsEntry(LogKey.REASON, BUSINESS_REASON);
    }

    @Test
    void shouldOmitFallbackReason_whenExceptionEscapesAfterFailureDeclaration() {
        var event = executeFailingScope(context::failure);

        assertThat(event.attributes()).doesNotContainKey(LogKey.REASON);
    }

    private LogEvent executeFailingScope(Runnable enrichment) {
        var original = new IOException("business failed");

        assertThatThrownBy(() -> executor.run(LOGGER_NAME, OPERATION, () -> {
            enrichment.run();
            throw original;
        })).isSameAs(original);

        assertThat(events).hasSize(1);
        var event = events.getFirst();
        assertThat(event.attributes()).containsEntry(LogKey.OUTCOME, "failure");
        assertThat(event.declaration().severity()).isEqualTo(LogLevel.ERROR);
        assertThat(event.declaration().cause()).isSameAs(original);
        assertThat(original.getSuppressed()).isEmpty();
        assertThat(original.getCause()).isNull();
        return event;
    }

    private LogEvent executeSuccessfulScope(Runnable enrichment) {
        var expected = new Object();

        var result = executor.execute(LOGGER_NAME, OPERATION, () -> {
            enrichment.run();
            return expected;
        });

        assertThat(result).isSameAs(expected);
        assertThat(events).hasSize(1);
        var event = events.getFirst();
        assertThat(event.attributes()).containsEntry(LogKey.OUTCOME, "success");
        assertThat(event.declaration().severity()).isEqualTo(LogLevel.INFO);
        assertThat(event.declaration().cause()).isNull();
        return event;
    }
}
