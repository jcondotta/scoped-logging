package com.jcondotta.scopedlogging.application.logscope;

import com.jcondotta.scopedlogging.application.port.output.LogEventPublisher;
import com.jcondotta.scopedlogging.domain.logging.LogEvent;
import com.jcondotta.scopedlogging.domain.logging.LogKey;
import com.jcondotta.scopedlogging.infrastructure.slf4j.Slf4jLogEventPublisher;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static com.jcondotta.scopedlogging.application.logscope.ScopeEventCapture.attributes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LogScopeEmissionFailureTest {

    private static final String PARENT_OPERATION = "parent";
    private static final String CHILD_OPERATION = "child";
    private static final String NEXT_OPERATION = "next";
    private static final String OWNER_ATTRIBUTE = "owner";
    private static final String PARENT_OWNER = PARENT_OPERATION;
    private static final String CHILD_OWNER = CHILD_OPERATION;
    private static final String RESTORED_ATTRIBUTE = "restored";
    private static final String TRUE_ATTRIBUTE_VALUE = "true";
    private static final String SUCCESS_OUTCOME = "success";
    private static final String PUBLISHER_FAILURE_MESSAGE = "publisher failed";
    private static final String BUSINESS_FAILURE_MESSAGE = "business failed";
    private static final String LOGGER_NAME = "scope-emission-test";
    private static final long PUBLISHER_DELAY_MILLIS = 250;

    @Test
    void shouldPreserveBusinessResult_whenPublisherFails() {
        assertBusinessBehaviorIsPreserved_whenPublisherFails(false, new IllegalStateException(PUBLISHER_FAILURE_MESSAGE));
    }

    @Test
    void shouldPreserveBusinessException_whenPublisherFails() {
        assertBusinessBehaviorIsPreserved_whenPublisherFails(true, new IllegalStateException(PUBLISHER_FAILURE_MESSAGE));
    }

    @Test
    void shouldPreserveBusinessResult_whenPublisherThrowsAssertionError() {
        assertBusinessBehaviorIsPreserved_whenPublisherFails(false, new AssertionError(PUBLISHER_FAILURE_MESSAGE));
    }

    @Test
    void shouldPreserveBusinessException_whenPublisherThrowsAssertionError() {
        assertBusinessBehaviorIsPreserved_whenPublisherFails(true, new AssertionError(PUBLISHER_FAILURE_MESSAGE));
    }

    @Test
    void shouldAttemptPublicationOnce_whenPublisherAcceptsEventBeforeFailing() {
        var attempts = new AtomicInteger();
        var acceptedEvents = new ArrayList<LogEvent>();
        LogEventPublisher partiallyFailingPublisher = event -> {
            attempts.incrementAndGet();
            acceptedEvents.add(event);
            throw new IllegalStateException(PUBLISHER_FAILURE_MESSAGE);
        };
        var context = new ScopedLogContext();
        var executor = new LogScopeExecutor(context, partiallyFailingPublisher);
        var expected = new Object();

        var actual = executor.execute("test.logger", CHILD_OPERATION, () -> expected);

        assertThat(actual).isSameAs(expected);
        assertThat(attempts).hasValue(1);
        assertThat(acceptedEvents).hasSize(1);
        assertThat(acceptedEvents.getFirst().attributes())
            .containsEntry(LogKey.OPERATION, CHILD_OPERATION);
    }

    @Test
    void shouldExcludePublisherLatencyFromDuration_whenPublisherIsSlow() {
        var events = new ArrayList<LogEvent>();
        LogEventPublisher slowPublisher = event -> {
            events.add(event);
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(PUBLISHER_DELAY_MILLIS));
        };
        var executor = new LogScopeExecutor(new ScopedLogContext(), slowPublisher);
        var startedAt = System.nanoTime();

        executor.execute(LOGGER_NAME, NEXT_OPERATION, () -> null);

        var elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        var eventDurationMillis = (Long) events.getFirst().attributes().get(LogKey.DURATION_MS);

        assertThat(elapsedMillis).isGreaterThanOrEqualTo(PUBLISHER_DELAY_MILLIS);
        assertThat(eventDurationMillis).isLessThan(elapsedMillis - PUBLISHER_DELAY_MILLIS / 2);
    }

    private void assertBusinessBehaviorIsPreserved_whenPublisherFails(boolean businessFailure, Throwable loggingFailure) {
        var attempts = new AtomicInteger();
        LogEventPublisher failingPublisher = event -> {
            attempts.incrementAndGet();
            if (loggingFailure instanceof RuntimeException runtimeException) throw runtimeException;
            throw (AssertionError) loggingFailure;
        };
        var executions = new AtomicInteger();
        var context = new ScopedLogContext();
        var executor = new LogScopeExecutor(context, failingPublisher);
        var parentExecutor = new LogScopeExecutor(context, new Slf4jLogEventPublisher());

        try (var capture = new ScopeEventCapture()) {
            var original = new IOException(BUSINESS_FAILURE_MESSAGE);
            var expected = new Object();
            parentExecutor.run(capture.logger.getName(), PARENT_OPERATION, () -> {
                context.with(OWNER_ATTRIBUTE, PARENT_OWNER);
                var thrown = catchThrowable(() -> {
                    var actual = executor.execute(capture.logger.getName(), CHILD_OPERATION, () -> {
                        executions.incrementAndGet();
                        context.with(OWNER_ATTRIBUTE, CHILD_OWNER).info(CHILD_OPERATION);
                        if (businessFailure) throw original;
                        return expected;
                    });
                    assertThat(actual).isSameAs(expected);
                });
                assertThat(thrown).isSameAs(businessFailure ? original : null);
                context.with(RESTORED_ATTRIBUTE, true);
            });

            assertThat(attempts).hasValue(1);
            assertThat(executions).hasValue(1);
            assertThat(capture.events()).hasSize(1);
            assertThat(attributes(capture.events().getFirst()))
                .containsEntry(OWNER_ATTRIBUTE, PARENT_OWNER)
                .containsEntry(RESTORED_ATTRIBUTE, TRUE_ATTRIBUTE_VALUE)
                .containsEntry(LogKey.OUTCOME, SUCCESS_OUTCOME);
            assertThatThrownBy(context::success).isInstanceOf(IllegalStateException.class);
            parentExecutor.run(capture.logger.getName(), NEXT_OPERATION, () -> {});
            assertThat(attributes(capture.events().getLast()))
                .doesNotContainKeys(OWNER_ATTRIBUTE, RESTORED_ATTRIBUTE, LogKey.REASON);
        }
    }
}
