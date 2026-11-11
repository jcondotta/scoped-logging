package com.jcondotta.scopedlogging.application.logscope;

import com.jcondotta.scopedlogging.domain.logging.LogKey;
import com.jcondotta.scopedlogging.domain.logging.LogLevel;
import com.jcondotta.scopedlogging.infrastructure.slf4j.Slf4jLogEventPublisher;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScopedLogContextTest {

    private static final String OPERATION = "resource.process";
    private static final String MESSAGE = "Operation accepted";
    private static final String SUCCESS_OUTCOME = "success";
    private static final String FAILURE_OUTCOME = "failure";
    private static final String RESOURCE_ID_KEY = "resource_id";
    private static final String RESOURCE_ID = "resource-123";
    private static final String ATTEMPT_COUNT_KEY = "attempt_count";
    private static final int ATTEMPT_COUNT = 2;
    private static final String ATTEMPT_COUNT_VALUE = String.valueOf(ATTEMPT_COUNT);
    private static final String INACTIVE_REASON = "invalid_state";
    private static final String INACTIVE_MESSAGE = "Resource is not active";
    private static final String ACTIVATION_FAILURE_MESSAGE = "Resource activation failed";
    private static final String INTERNAL_ERROR_REASON = "internal_error";
    private static final String PAYMENT_DECLINED_REASON = "payment_declined";
    private static final String PAYMENT_FAILURE_MESSAGE = "payment declined";
    private static final String UNEXPECTED_FAILURE_MESSAGE = "unexpected failure";
    private static final String PROPAGATED_EXCEPTION_MESSAGE = "Operation failed with an exception";
    private static final String DECLARED_FAILURE_MESSAGE = "declared failure";
    private static final String CLASSIFIED_FAILURE_MESSAGE = "classified failure";
    private static final String NO_ACTIVE_SCOPE_MESSAGE =
        "no active logging scope; access LogContext only within LogScopeExecutor.run or execute";

    private final ScopedLogContext logContext = new ScopedLogContext();

    private Logger logger;
    private Level previousLevel;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(ScopedLogContextTest.class);
        previousLevel = logger.getLevel();

        logger.setLevel(Level.DEBUG);
        appender = new ListAppender<>();
        appender.start();

        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
        logger.setLevel(previousLevel);
    }

    @Test
    void shouldEmitOneInfoEventWithSuccessAndDuration_whenScopeCompletesWithoutDeclarations() throws Throwable {
        runInScope(context -> {});

        var softly = new org.assertj.core.api.SoftAssertions();
        softly.assertThat(appender.list).hasSize(1);
        softly.assertThat(event(ILoggingEvent::getLevel)).isEqualTo(Level.INFO);
        softly.assertThat(event(ILoggingEvent::getFormattedMessage)).isEqualTo("Operation completed");
        softly.assertThat(attributes())
            .containsEntry(LogKey.OPERATION, OPERATION)
            .containsEntry(LogKey.OUTCOME, SUCCESS_OUTCOME)
            .containsKey(LogKey.DURATION_MS);
        softly.assertThat(Long.parseLong(attributes().get(LogKey.DURATION_MS))).isGreaterThanOrEqualTo(0);
        softly.assertAll();
    }

    @Test
    void shouldIncludeAccumulatedAttributes_whenScopeCompletes() throws Throwable {
        runInScope(context -> context
            .with(RESOURCE_ID_KEY, RESOURCE_ID)
            .with(ATTEMPT_COUNT_KEY, ATTEMPT_COUNT));

        assertThat(appender.list).hasSize(1);
        assertThat(attributes())
            .containsEntry(LogKey.OPERATION, OPERATION)
            .containsEntry(RESOURCE_ID_KEY, RESOURCE_ID)
            .containsEntry(ATTEMPT_COUNT_KEY, ATTEMPT_COUNT_VALUE)
            .containsEntry(LogKey.OUTCOME, SUCCESS_OUTCOME);
    }

    @Test
    void shouldEmitWarnWithDefaultMessage_whenFailureIsDeclaredWithoutMessage() throws Throwable {
        runInScope(LogContext::failure);

        var softly = new org.assertj.core.api.SoftAssertions();
        softly.assertThat(appender.list).hasSize(1);
        softly.assertThat(event(ILoggingEvent::getLevel)).isEqualTo(Level.WARN);
        softly.assertThat(event(ILoggingEvent::getFormattedMessage)).isEqualTo("Operation failed");
        softly.assertThat(attributes()).containsEntry(LogKey.OUTCOME, FAILURE_OUTCOME);
        softly.assertAll();
    }

    @Test
    void shouldEmitDebugEvent_whenDebugSeverityIsDeclared() throws Throwable {
        runInScope(context -> context.debug("Diagnostic details"));

        assertThat(appender.list).hasSize(1);
        assertThat(event(ILoggingEvent::getLevel)).isEqualTo(Level.DEBUG);
        assertThat(event(ILoggingEvent::getFormattedMessage)).isEqualTo("Diagnostic details");
    }

    @Test
    void shouldEmitInfoEvent_whenInfoSeverityIsDeclared() throws Throwable {
        runInScope(context -> context.info(MESSAGE));

        var softly = new org.assertj.core.api.SoftAssertions();
        softly.assertThat(appender.list).hasSize(1);
        softly.assertThat(event(ILoggingEvent::getLevel)).isEqualTo(Level.INFO);
        softly.assertThat(event(ILoggingEvent::getFormattedMessage)).isEqualTo(MESSAGE);
        softly.assertThat(attributes()).containsEntry(LogKey.OUTCOME, SUCCESS_OUTCOME);
        softly.assertAll();
    }

    @Test
    void shouldUseLastOutcomeAndAttributeDeclaration_whenContextIsUpdatedRepeatedly() throws Throwable {
        runInScope(context -> {
            context.success();
            context.with(LogKey.REASON, "first");
            context.with(LogKey.REASON, "last");
            context.failure();
        });

        assertThat(appender.list).hasSize(1);
        assertThat(event(ILoggingEvent::getLevel)).isEqualTo(Level.WARN);
        assertThat(attributes())
            .containsEntry(LogKey.OUTCOME, FAILURE_OUTCOME)
            .containsEntry(LogKey.REASON, "last");
    }

    @Test
    void shouldUseSuccess_whenSuccessIsDeclaredAfterFailure() throws Throwable {
        runInScope(context -> {
            context.failure();
            context.success();
        });

        assertThat(appender.list).hasSize(1);
        assertThat(event(ILoggingEvent::getLevel)).isEqualTo(Level.INFO);
        assertThat(attributes()).containsEntry(LogKey.OUTCOME, SUCCESS_OUTCOME);
    }

    @Test
    void shouldUseDeclaredSeverityAndMessageAndPreserveAttributes_whenEventIsDeclared() throws Throwable {
        runInScope(context -> context
            .with(RESOURCE_ID_KEY, RESOURCE_ID)
            .error(ACTIVATION_FAILURE_MESSAGE, new IllegalStateException(DECLARED_FAILURE_MESSAGE))
            .failure());

        var softly = new org.assertj.core.api.SoftAssertions();
        softly.assertThat(appender.list).hasSize(1);
        softly.assertThat(event(ILoggingEvent::getLevel)).isEqualTo(Level.ERROR);
        softly.assertThat(event(ILoggingEvent::getFormattedMessage)).isEqualTo(ACTIVATION_FAILURE_MESSAGE);
        softly.assertThat(event(ILoggingEvent::getThrowableProxy).getMessage()).isEqualTo(DECLARED_FAILURE_MESSAGE);
        softly.assertThat(attributes())
            .containsEntry(RESOURCE_ID_KEY, RESOURCE_ID)
            .containsEntry(LogKey.OUTCOME, FAILURE_OUTCOME);
        softly.assertAll();
    }

    @Test
    void shouldEmitPropagatedExceptionAsErrorAndPreserveAttributes_whenNoFailureWasDeclared() {
        var failure = new IllegalStateException(UNEXPECTED_FAILURE_MESSAGE);

        assertThatThrownBy(() -> runInScope(context -> {
            context.with(RESOURCE_ID_KEY, RESOURCE_ID);
            context.success();
            throw failure;
        })).isSameAs(failure);

        var softly = new org.assertj.core.api.SoftAssertions();
        softly.assertThat(appender.list).hasSize(1);
        softly.assertThat(event(ILoggingEvent::getLevel)).isEqualTo(Level.ERROR);
        softly.assertThat(event(ILoggingEvent::getFormattedMessage)).isEqualTo(PROPAGATED_EXCEPTION_MESSAGE);
        softly.assertThat(event(ILoggingEvent::getThrowableProxy).getMessage()).isEqualTo(UNEXPECTED_FAILURE_MESSAGE);
        softly.assertThat(attributes())
            .containsEntry(RESOURCE_ID_KEY, RESOURCE_ID)
            .containsEntry(LogKey.OUTCOME, FAILURE_OUTCOME)
            .containsEntry(LogKey.REASON, INTERNAL_ERROR_REASON);
        softly.assertAll();
    }

    @Test
    void shouldPreserveDeclaredReason_whenExceptionEscapesWithoutFailureDeclaration() {
        var failure = new IllegalStateException(PAYMENT_FAILURE_MESSAGE);

        assertThatThrownBy(() -> runInScope(context -> {
            context.with(LogKey.REASON, PAYMENT_DECLINED_REASON);
            throw failure;
        })).isSameAs(failure);

        var softly = new org.assertj.core.api.SoftAssertions();
        softly.assertThat(appender.list).hasSize(1);
        softly.assertThat(event(ILoggingEvent::getLevel)).isEqualTo(Level.ERROR);
        softly.assertThat(event(ILoggingEvent::getThrowableProxy).getMessage()).isEqualTo(PAYMENT_FAILURE_MESSAGE);
        softly.assertThat(attributes())
            .containsEntry(LogKey.OUTCOME, FAILURE_OUTCOME)
            .containsEntry(LogKey.REASON, PAYMENT_DECLINED_REASON);
        softly.assertAll();
    }

    @Test
    void shouldPreserveDeclaredFailureAndAttributes_whenExceptionPropagatesAfterFailureWasDeclared() {
        var failure = new IllegalArgumentException(CLASSIFIED_FAILURE_MESSAGE);

        assertThatThrownBy(() -> runInScope(context -> {
            context.with(LogKey.REASON, INACTIVE_REASON)
                .warn(INACTIVE_MESSAGE)
                .with(RESOURCE_ID_KEY, RESOURCE_ID);
            context.failure();
            throw failure;
        })).isSameAs(failure);

        var softly = new org.assertj.core.api.SoftAssertions();
        softly.assertThat(appender.list).hasSize(1);
        softly.assertThat(event(ILoggingEvent::getLevel)).isEqualTo(Level.ERROR);
        softly.assertThat(event(ILoggingEvent::getFormattedMessage)).isEqualTo(INACTIVE_MESSAGE);
        softly.assertThat(event(ILoggingEvent::getThrowableProxy).getMessage()).isEqualTo(CLASSIFIED_FAILURE_MESSAGE);
        softly.assertThat(attributes())
            .containsEntry(LogKey.REASON, INACTIVE_REASON)
            .containsEntry(RESOURCE_ID_KEY, RESOURCE_ID)
            .containsEntry(LogKey.OUTCOME, FAILURE_OUTCOME);
        softly.assertAll();
    }

    @Test
    void shouldThrowIllegalStateException_whenWithIsCalledOutsideScope() {
        assertNoActiveScope(() -> logContext.with(RESOURCE_ID_KEY, RESOURCE_ID));
    }

    @Test
    void shouldThrowIllegalStateException_whenDebugIsCalledOutsideScope() {
        assertNoActiveScope(() -> logContext.debug(MESSAGE));
    }

    @Test
    void shouldThrowIllegalStateException_whenInfoIsCalledOutsideScope() {
        assertNoActiveScope(() -> logContext.info(MESSAGE));
    }

    @Test
    void shouldThrowIllegalStateException_whenWarnIsCalledOutsideScope() {
        assertNoActiveScope(() -> logContext.warn(MESSAGE));
    }

    @Test
    void shouldThrowIllegalStateException_whenErrorIsCalledOutsideScope() {
        assertNoActiveScope(() -> logContext.error(MESSAGE, null));
    }

    @Test
    void shouldThrowIllegalStateException_whenSuccessIsCalledOutsideScope() {
        assertNoActiveScope(logContext::success);
    }

    @Test
    void shouldThrowIllegalStateException_whenFailureIsCalledOutsideScope() {
        assertNoActiveScope(logContext::failure);
    }

    private void assertNoActiveScope(Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(NO_ACTIVE_SCOPE_MESSAGE);
    }

    @Test
    void shouldThrowIllegalStateException_whenUnstructuredChildThreadAccessesContext() throws Throwable {
        new LogScopeExecutor(logContext, new Slf4jLogEventPublisher()).run(logger.getName(), OPERATION, () -> {
            assertNoActiveScopeInChild(Thread.ofPlatform());
            assertNoActiveScopeInChild(Thread.ofVirtual());
        });

        assertThat(appender.list).hasSize(1);
        assertThat(attributes())
            .containsEntry(LogKey.OPERATION, OPERATION)
            .containsEntry(LogKey.OUTCOME, SUCCESS_OUTCOME);
    }

    private void assertNoActiveScopeInChild(Thread.Builder builder) throws InterruptedException {
        var error = new AtomicReference<Throwable>();
        var child = builder.unstarted(() -> {
            try {
                logContext.with("child_attribute", "must-not-be-visible");
            }
            catch (Throwable thrown) {
                error.set(thrown);
            }
        });

        child.start();
        child.join();

        assertThat(error.get())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(NO_ACTIVE_SCOPE_MESSAGE);
    }

    private void runInScope(ThrowingConsumer action) throws Throwable {
        new LogScopeExecutor(logContext, new Slf4jLogEventPublisher())
            .run(logger.getName(), OPERATION, () -> action.accept(logContext));
    }

    private Map<String, String> attributes() {
        return event(ILoggingEvent::getKeyValuePairs)
            .stream()
            .collect(toMap(pair -> pair.key, pair -> String.valueOf(pair.value), (first, last) -> last));
    }

    private <T> T event(Function<ILoggingEvent, T> mapper) {
        return mapper.apply(appender.list.getFirst());
    }

    @FunctionalInterface
    private interface ThrowingConsumer {
        void accept(LogContext context) throws Throwable;
    }
}
