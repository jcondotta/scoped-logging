package com.jcondotta.scopedlogging.application.logscope;

import com.jcondotta.scopedlogging.domain.logging.LogEvent;
import com.jcondotta.scopedlogging.domain.logging.LogKey;
import com.jcondotta.scopedlogging.domain.logging.LogLevel;
import com.jcondotta.scopedlogging.infrastructure.slf4j.Slf4jLogEventPublisher;

import ch.qos.logback.classic.Level;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.jcondotta.scopedlogging.application.logscope.ScopeEventCapture.attributes;
import static org.assertj.core.api.Assertions.*;

class LogScopeExecutorTest {

    private static final String OUTER_OPERATION = "outer";
    private static final String MIDDLE_OPERATION = "middle";
    private static final String INNER_OPERATION = "inner";
    private static final String OWNER_ATTRIBUTE = "owner";
    private static final String RESTORED_ATTRIBUTE = "restored";
    private static final String BEFORE_ATTRIBUTE = "before";
    private static final String AFTER_ATTRIBUTE = "after";
    private static final String SUCCESS_OUTCOME = "success";
    private static final String FAILURE_OUTCOME = "failure";
    private static final String TRUE_VALUE = "true";
    private static final String RECOVERED_VALUE = "recovered";
    private static final String FIRST_CONTEXT_OPERATION = "first";
    private static final String SECOND_CONTEXT_OPERATION = "second";
    private static final String FIRST_CONTEXT_ATTRIBUTE = FIRST_CONTEXT_OPERATION;
    private static final String SECOND_CONTEXT_ATTRIBUTE = SECOND_CONTEXT_OPERATION;
    private static final String FIRST_CONTEXT_RESTORED_ATTRIBUTE = "still_first";
    private static final String NEXT_OPERATION = "next";
    private static final String INVALID_OPERATION = "invalid";
    private static final String INTERNAL_ERROR_REASON = "internal_error";
    private static final String EXCEPTION_MESSAGE = "original failure";
    private static final String TO_STRING_FAILURE_MESSAGE = "toString failed";
    private static final String PROPAGATED_EXCEPTION_MESSAGE = "Operation failed with an exception";
    private static final String DECLARED_ERROR_MESSAGE = "Payment rejected";

    static Stream<Arguments> throwableScenarios() {
        return Stream.of(
            Arguments.of(new IOException("checked")),
            Arguments.of(new IllegalStateException("runtime")),
            Arguments.of(new AssertionError("error"))
        );
    }

    private final ScopedLogContext context = new ScopedLogContext();
    private final LogScopeExecutor executor = new LogScopeExecutor(context, new Slf4jLogEventPublisher());

    @ParameterizedTest
    @ValueSource(strings = { LogKey.OPERATION, LogKey.OUTCOME, LogKey.DURATION_MS })
    void shouldThrowIllegalArgumentException_whenReservedAttributeIsAdded(String key) {
        try (var capture = new ScopeEventCapture()) {
            executor.run(capture.logger.getName(), "reserved", () -> {
                assertThatThrownBy(() -> context.with(key, "caller-value"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("attribute '" + key + "' is reserved and managed by scoped logging");
                context.with("business", true).with(LogKey.REASON, "validation_failed").failure();
            });
            assertThat(attributes(capture.events().getFirst()))
                .containsEntry(LogKey.OPERATION, "reserved")
                .containsEntry(LogKey.OUTCOME, FAILURE_OUTCOME)
                .containsEntry(LogKey.REASON, "validation_failed")
                .containsEntry("business", TRUE_VALUE);
        }
    }

    @Test
    void shouldKeepSuccessOutcome_whenWarnIsDeclared() {
        try (var capture = new ScopeEventCapture()) {
            executor.run(capture.logger.getName(), "warn-success", () -> context.warn("review").success());

            assertThat(capture.events()).hasSize(1);
            assertThat(capture.events().getFirst().getLevel()).isEqualTo(Level.WARN);
            assertThat(attributes(capture.events().getFirst())).containsEntry(LogKey.OUTCOME, SUCCESS_OUTCOME);
        }
    }

    @Test
    void shouldKeepSuccessOutcome_whenErrorIsDeclaredAndOperationReturnsNormally() {
        try (var capture = new ScopeEventCapture()) {
            var cause = new IllegalStateException(DECLARED_ERROR_MESSAGE);
            executor.run(capture.logger.getName(), "payment.process", () ->
                context.error(DECLARED_ERROR_MESSAGE, cause));

            assertThat(capture.events()).hasSize(1);
            var event = capture.events().getFirst();
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).isEqualTo(DECLARED_ERROR_MESSAGE);
            assertThat(event.getThrowableProxy().getMessage()).isEqualTo(DECLARED_ERROR_MESSAGE);
            assertThat(attributes(event)).containsEntry(LogKey.OUTCOME, SUCCESS_OUTCOME);
        }
    }

    @Test
    void shouldKeepFailureOutcome_whenInfoIsDeclared() {
        try (var capture = new ScopeEventCapture()) {
            executor.run(capture.logger.getName(), "failure-info", () -> {
                context.failure();
                context.info("rejected");
            });

            assertThat(capture.events()).hasSize(1);
            assertThat(capture.events().getFirst().getLevel()).isEqualTo(Level.INFO);
            assertThat(attributes(capture.events().getFirst())).containsEntry(LogKey.OUTCOME, FAILURE_OUTCOME);
        }
    }

    @Test
    void shouldClearPreviousCause_whenLaterDeclarationDoesNotHaveCause() {
        try (var capture = new ScopeEventCapture()) {
            executor.run(capture.logger.getName(), RECOVERED_VALUE, () -> {
                var old = new IllegalStateException("old cause");
                context.error("initial error", old).info(RECOVERED_VALUE).success();
            });

            assertThat(capture.events()).hasSize(1);
            assertThat(capture.events().getFirst().getFormattedMessage()).isEqualTo(RECOVERED_VALUE);
            assertThat(capture.events().getFirst().getLevel()).isEqualTo(Level.INFO);
            assertThat(capture.events().getFirst().getThrowableProxy()).isNull();
            assertThat(attributes(capture.events().getFirst())).containsEntry(LogKey.OUTCOME, SUCCESS_OUTCOME);
        }
    }

    @Test
    void shouldUseEscapingException_whenDeclarationAlsoExists() {
        try (var capture = new ScopeEventCapture()) {
            var thrown = new IllegalStateException("escaped");
            assertThatThrownBy(() -> executor.run(capture.logger.getName(), "escape", () -> {
                context.with(LogKey.REASON, "business_failure").failure();
                context.error("declared", new IllegalArgumentException("declared cause"))
                    .info("last declaration");
                throw thrown;
            })).isSameAs(thrown);
            var event = capture.events().getFirst();
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).isEqualTo("last declaration");
            assertThat(event.getThrowableProxy().getMessage()).isEqualTo("escaped");
            assertThat(attributes(event)).containsEntry(LogKey.OUTCOME, FAILURE_OUTCOME)
                .containsEntry(LogKey.REASON, "business_failure");
        }
    }

    @Test
    void shouldUseStableMessageAndPreserveCause_whenThrowableToStringFails() {
        var thrown = new RuntimeException(EXCEPTION_MESSAGE) {
            @Override
            public String toString() {
                throw new IllegalStateException(TO_STRING_FAILURE_MESSAGE);
            }
        };
        var events = new ArrayList<LogEvent>();
        var isolatedExecutor = new LogScopeExecutor(new ScopedLogContext(), events::add);

        assertThatThrownBy(() -> isolatedExecutor.run("test.logger", "to-string-failure", () -> {
            throw thrown;
        })).isSameAs(thrown);

        assertThat(events).hasSize(1);
        var event = events.getFirst();
        assertThat(event.declaration().severity()).isEqualTo(LogLevel.ERROR);
        assertThat(event.declaration().message()).isEqualTo(PROPAGATED_EXCEPTION_MESSAGE);
        assertThat(event.declaration().cause()).isSameAs(thrown);
        assertThat(event.attributes()).containsEntry(LogKey.OUTCOME, FAILURE_OUTCOME);
    }

    @Test
    void shouldCompleteBeforeReturningAndPreserveIdentity_whenOperationReturnsAnObject() {
        try (var capture = new ScopeEventCapture()) {
            var expected = new Object();
            var actual = executor.execute(capture.logger.getName(), "value", () -> {
                context.info("done").success();
                context.success();
                assertThat(capture.events()).isEmpty();
                return expected;
            });
            assertThat(actual).isSameAs(expected);
            assertThat(capture.events()).hasSize(1);
            assertThatThrownBy(() -> context.with("outside", true)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void shouldReturnNull_whenOperationReturnsNull() {
        try (var capture = new ScopeEventCapture()) {
            var result = executor.execute(capture.logger.getName(), "null", () -> null);

            assertThat(result).isNull();
            assertThat(capture.events()).hasSize(1);
        }
    }

    @ParameterizedTest
    @EnumSource(LogLevel.class)
    void shouldUseConfiguredDefaultSeverity_whenNoEventIsDeclared(LogLevel level) {
        try (var capture = new ScopeEventCapture()) {
            executor.run(capture.logger.getName(), "default", level, () -> {});

            assertThat(capture.events()).hasSize(1);
            assertThat(capture.events().getFirst().getLevel().toString()).isEqualTo(level.name());
        }
    }

    @Test
    void shouldUseDeclaredSeverity_whenExplicitDeclarationOverridesDefault() {
        try (var capture = new ScopeEventCapture()) {
            executor.run(capture.logger.getName(), "explicit", LogLevel.DEBUG, () -> context.warn("override"));

            assertThat(capture.events()).hasSize(1);
            assertThat(capture.events().getFirst().getLevel()).isEqualTo(Level.WARN);
            assertThat(capture.events().getFirst().getFormattedMessage()).isEqualTo("override");
        }
    }

    @Test
    void shouldUseWarnSeverity_whenFailureIsDeclaredWithoutSeverity() {
        try (var capture = new ScopeEventCapture()) {
            executor.run(capture.logger.getName(), FAILURE_OUTCOME, LogLevel.DEBUG, context::failure);

            assertThat(capture.events()).hasSize(1);
            assertThat(capture.events().getFirst().getLevel()).isEqualTo(Level.WARN);
            assertThat(attributes(capture.events().getFirst())).containsEntry(LogKey.OUTCOME, FAILURE_OUTCOME);
        }
    }

    @ParameterizedTest
    @MethodSource("throwableScenarios")
    void shouldRestoreEveryScopeAndPreserveThrowableIdentity_whenNestedOperationThrows(Throwable original) {
        try (var capture = new ScopeEventCapture()) {
            var actual = catchThrowable(() -> executor.run(capture.logger.getName(), OUTER_OPERATION, () -> {
                context.with(OWNER_ATTRIBUTE, OUTER_OPERATION).success();
                executor.run(capture.logger.getName(), MIDDLE_OPERATION, () -> {
                    context.with(OWNER_ATTRIBUTE, MIDDLE_OPERATION);
                    executor.run(capture.logger.getName(), INNER_OPERATION, () -> {
                        context.with(OWNER_ATTRIBUTE, INNER_OPERATION).with(LogKey.REASON, "classified").failure();
                        throw original;
                    });
                });
            }));
            assertThat(actual).isSameAs(original);
            assertThat(capture.events()).hasSize(3);
            var softly = new SoftAssertions();
            for (int i = 0; i < 3; i++) {
                var owner = new String[]{INNER_OPERATION, MIDDLE_OPERATION, OUTER_OPERATION}[i];
                var event = capture.events().get(i);
                softly.assertThat(attributes(event))
                    .containsEntry(LogKey.OPERATION, owner).containsEntry(OWNER_ATTRIBUTE, owner)
                    .containsEntry(LogKey.OUTCOME, FAILURE_OUTCOME)
                    .containsEntry(LogKey.REASON, i == 0 ? "classified" : INTERNAL_ERROR_REASON);
                softly.assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                softly.assertThat(event.getThrowableProxy().getMessage()).isEqualTo(original.getMessage());
            }
            softly.assertAll();
            assertThatThrownBy(() -> context.success()).isInstanceOf(IllegalStateException.class);
            executor.run(capture.logger.getName(), NEXT_OPERATION, () -> {});
            assertThat(attributes(capture.events().getLast())).doesNotContainKeys(OWNER_ATTRIBUTE, LogKey.REASON);
        }
    }

    @Test
    void shouldRestoreParentAndCompleteSuccessfully_whenInnerFailureIsHandled() {
        try (var capture = new ScopeEventCapture()) {
            var original = new IOException(INNER_OPERATION);
            var result = executor.execute(capture.logger.getName(), OUTER_OPERATION, () -> {
                context.with(OWNER_ATTRIBUTE, OUTER_OPERATION);
                var caught = catchThrowable(() -> executor.run(capture.logger.getName(), MIDDLE_OPERATION, () -> {
                    context.with(OWNER_ATTRIBUTE, MIDDLE_OPERATION);
                    try {
                        executor.run(capture.logger.getName(), INNER_OPERATION, () -> { throw original; });
                    }
                    catch (IOException error) {
                        context.with(OWNER_ATTRIBUTE, MIDDLE_OPERATION);
                        context.with(RESTORED_ATTRIBUTE, true);
                        context.info(RECOVERED_VALUE).success();
                    }
                }));
                assertThat(caught).isNull();
                context.with(AFTER_ATTRIBUTE, true).info("outer completed").success();
                return RECOVERED_VALUE;
            });
            assertThat(result).isEqualTo(RECOVERED_VALUE);
            assertThat(capture.events()).hasSize(3);
            assertThat(attributes(capture.events().get(1)))
                .containsEntry(OWNER_ATTRIBUTE, MIDDLE_OPERATION).containsEntry(RESTORED_ATTRIBUTE, TRUE_VALUE)
                .containsEntry(LogKey.OUTCOME, SUCCESS_OUTCOME);
            assertThat(attributes(capture.events().getLast()))
                .containsEntry(OWNER_ATTRIBUTE, OUTER_OPERATION).containsEntry(AFTER_ATTRIBUTE, TRUE_VALUE)
                .containsEntry(LogKey.OUTCOME, SUCCESS_OUTCOME).doesNotContainKeys(RESTORED_ATTRIBUTE, LogKey.REASON);
        }
    }

    @Test
    void shouldPropagateReplacementException_whenInnerFailureIsWrapped() {
        try (var capture = new ScopeEventCapture()) {
            var original = new IOException(INNER_OPERATION);
            var replacement = new IllegalArgumentException(MIDDLE_OPERATION, original);
            var caught = catchThrowable(() -> executor.run(capture.logger.getName(), OUTER_OPERATION, () -> {
                context.with(OWNER_ATTRIBUTE, OUTER_OPERATION);
                executor.run(capture.logger.getName(), MIDDLE_OPERATION, () -> {
                    try {
                        executor.run(capture.logger.getName(), INNER_OPERATION, () -> { throw original; });
                    }
                    catch (IOException error) {
                        context.with(OWNER_ATTRIBUTE, MIDDLE_OPERATION);
                        context.with(RESTORED_ATTRIBUTE, true);
                        throw replacement;
                    }
                });
            }));

            assertThat(caught).isSameAs(replacement);
            assertThat(capture.events()).hasSize(3);
            assertThat(attributes(capture.events().get(1)))
                .containsEntry(OWNER_ATTRIBUTE, MIDDLE_OPERATION)
                .containsEntry(RESTORED_ATTRIBUTE, TRUE_VALUE)
                .containsEntry(LogKey.OUTCOME, FAILURE_OUTCOME);
            assertThat(attributes(capture.events().getLast()))
                .containsEntry(OWNER_ATTRIBUTE, OUTER_OPERATION)
                .containsEntry(LogKey.OUTCOME, FAILURE_OUTCOME);
        }
    }

    @Test
    void shouldKeepInnerSuccess_whenParentFailsAfterInnerCompletes() {
        try (var capture = new ScopeEventCapture()) {
            var original = new IllegalStateException("parent failed later");
            assertThatThrownBy(() -> executor.run(capture.logger.getName(), OUTER_OPERATION, () -> {
                context.with(OWNER_ATTRIBUTE, OUTER_OPERATION);
                executor.run(capture.logger.getName(), INNER_OPERATION, () -> context.with(OWNER_ATTRIBUTE, INNER_OPERATION).success());
                throw original;
            })).isSameAs(original);
            assertThat(capture.events()).hasSize(2);
            assertThat(attributes(capture.events().getFirst())).containsEntry(LogKey.OUTCOME, SUCCESS_OUTCOME)
                .containsEntry(OWNER_ATTRIBUTE, INNER_OPERATION).doesNotContainKey(LogKey.REASON);
            assertThat(capture.events().getFirst().getThrowableProxy()).isNull();
            assertThat(attributes(capture.events().getLast())).containsEntry(LogKey.OUTCOME, FAILURE_OUTCOME)
                .containsEntry(OWNER_ATTRIBUTE, OUTER_OPERATION);
        }
    }

    @Test
    void shouldIsolateContextInstances_whenIndependentExecutorsNestOnSameThread() {
        var other = new ScopedLogContext();
        try (var capture = new ScopeEventCapture()) {
            executor.run(capture.logger.getName(), FIRST_CONTEXT_OPERATION, () -> {
                context.with(FIRST_CONTEXT_ATTRIBUTE, true);
                assertThatThrownBy(other::success).isInstanceOf(IllegalStateException.class);
                new LogScopeExecutor(other, new Slf4jLogEventPublisher()).run(capture.logger.getName(), SECOND_CONTEXT_OPERATION, () -> {
                    other.with(SECOND_CONTEXT_ATTRIBUTE, true);
                    context.with(FIRST_CONTEXT_RESTORED_ATTRIBUTE, true);
                });
                assertThatThrownBy(other::success).isInstanceOf(IllegalStateException.class);
            });
            assertThat(attributes(capture.events().getFirst())).containsEntry(SECOND_CONTEXT_ATTRIBUTE, TRUE_VALUE)
                .doesNotContainKeys(FIRST_CONTEXT_ATTRIBUTE, FIRST_CONTEXT_RESTORED_ATTRIBUTE);
            assertThat(attributes(capture.events().getLast())).containsEntry(FIRST_CONTEXT_ATTRIBUTE, TRUE_VALUE)
                .containsEntry(FIRST_CONTEXT_RESTORED_ATTRIBUTE, TRUE_VALUE)
                .doesNotContainKey(SECOND_CONTEXT_ATTRIBUTE);
        }
    }

    @Test
    void shouldThrowNullPointerException_whenLoggerNameIsNull() {
        assertInvalidInvocation("logger name must not be null",
            (capture, invoked, action) -> executor.execute(null, INVALID_OPERATION, action));
    }

    @Test
    void shouldThrowNullPointerException_whenOperationIsNull() {
        assertInvalidInvocation("operation must not be null",
            (capture, invoked, action) -> executor.execute(capture.logger.getName(), null, action));
    }

    @Test
    void shouldThrowIllegalArgumentException_whenLoggerNameIsBlank() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> executor.execute("", INVALID_OPERATION, () -> null))
            .withMessage("logger name must not be blank");
    }

    @Test
    void shouldThrowIllegalArgumentException_whenOperationIsBlank() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> executor.execute("logger", "   ", () -> null))
            .withMessage("operation must not be blank");
    }

    @Test
    void shouldThrowNullPointerException_whenLogLevelIsNull() {
        assertInvalidInvocation("log level must not be null",
            (capture, invoked, action) -> executor.execute(capture.logger.getName(), INVALID_OPERATION, null, action));
    }

    @Test
    void shouldThrowNullPointerException_whenExecuteActionIsNull() {
        assertInvalidInvocation("execute action must not be null",
            (capture, invoked, action) -> executor.execute(capture.logger.getName(), INVALID_OPERATION,
                (ScopedValue.CallableOp<String, RuntimeException>) null));
    }

    @Test
    void shouldThrowNullPointerException_whenRunActionIsNull() {
        assertInvalidInvocation("run action must not be null",
            (capture, invoked, action) -> executor.run(capture.logger.getName(), INVALID_OPERATION,
                (LogAction<RuntimeException>) null));
    }

    private void assertInvalidInvocation(String expectedMessage, InvalidInvocation invocation) {
        try (var capture = new ScopeEventCapture()) {
            var invoked = new AtomicBoolean();
            executor.run(capture.logger.getName(), OUTER_OPERATION, () -> {
                context.with(BEFORE_ATTRIBUTE, true);
                ScopedValue.CallableOp<String, RuntimeException> action = () -> { invoked.set(true); return "value"; };
                assertThatThrownBy(() -> invocation.invoke(capture, invoked, action))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage(expectedMessage);
                context.with(AFTER_ATTRIBUTE, true);
            });
            assertThat(invoked).isFalse();
            assertThat(capture.events()).hasSize(1);
            assertThat(attributes(capture.events().getFirst())).containsEntry(BEFORE_ATTRIBUTE, TRUE_VALUE).containsEntry(AFTER_ATTRIBUTE, TRUE_VALUE);
        }
    }

    @FunctionalInterface
    private interface InvalidInvocation {
        void invoke(ScopeEventCapture capture, AtomicBoolean invoked,
                    ScopedValue.CallableOp<String, RuntimeException> action);
    }

    @Test
    void shouldPreserveTextAndManyAttributes_whenPayloadIsLargeAndContainsUnicode() {
        try (var capture = new ScopeEventCapture()) {
            var message = "Operação concluída ✓\n日本語 🚀";
            executor.run(capture.logger.getName(), "large", () -> {
                IntStream.range(0, 1000).forEach(i -> context.with("key_" + i, "value_" + i));
                context.info(message);
            });
            assertThat(capture.events()).hasSize(1);
            var event = capture.events().getFirst();
            assertThat(event.getFormattedMessage()).isEqualTo(message);
            var values = attributes(event);
            assertThat(values).hasSize(1003);
            IntStream.range(0, 1000).forEach(i -> assertThat(values).containsEntry("key_" + i, "value_" + i));
        }
    }

    @Test
    void shouldRestoreScopeEvenWithoutOutput_whenLoggerFiltersTheLevel() {
        try (var capture = new ScopeEventCapture()) {
            capture.logger.setLevel(Level.OFF);
            executor.run(capture.logger.getName(), "filtered", () -> context.with("secret", true));
            assertThat(capture.events()).isEmpty();
            assertThatThrownBy(context::success).isInstanceOf(IllegalStateException.class);
            capture.logger.setLevel(Level.INFO);
            executor.run(capture.logger.getName(), "visible", () -> {});
            assertThat(attributes(capture.events().getFirst())).doesNotContainKey("secret");
        }
    }
}
