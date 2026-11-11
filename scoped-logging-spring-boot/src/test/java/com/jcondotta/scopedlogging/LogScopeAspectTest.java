package com.jcondotta.scopedlogging;

import com.jcondotta.scopedlogging.application.logscope.LogScopeExecutor;
import com.jcondotta.scopedlogging.application.logscope.ScopedLogContext;
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
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.util.Map;

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LogScopeAspectTest {

    private AnnotationConfigApplicationContext applicationContext;
    private ScopedOperations operations;
    private Logger targetLogger;
    private Logger outerLogger;
    private Level previousTargetLevel;
    private Level previousOuterLevel;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        applicationContext = new AnnotationConfigApplicationContext(TestConfiguration.class);
        operations = applicationContext.getBean(ScopedOperations.class);
        targetLogger = (Logger) LoggerFactory.getLogger(ScopedOperations.class);
        outerLogger = (Logger) LoggerFactory.getLogger(OuterScopedOperations.class);
        previousTargetLevel = targetLogger.getLevel();
        previousOuterLevel = outerLogger.getLevel();
        targetLogger.setLevel(Level.DEBUG);
        outerLogger.setLevel(Level.DEBUG);
        appender = new ListAppender<>();
        appender.start();
        targetLogger.addAppender(appender);
        outerLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        targetLogger.detachAppender(appender);
        outerLogger.detachAppender(appender);
        appender.stop();
        targetLogger.setLevel(previousTargetLevel);
        outerLogger.setLevel(previousOuterLevel);
        applicationContext.close();
    }

    @Test
    void shouldEmitExactlyOneStructuredEventAndPreserveReturnValue_whenAnnotatedMethodSucceeds() {
        assertThat(AopUtils.isAopProxy(operations)).isTrue();

        var result = operations.open();

        var softly = new org.assertj.core.api.SoftAssertions();
        softly.assertThat(result).isEqualTo("opened");
        softly.assertThat(appender.list).hasSize(1);
        softly.assertThat(appender.list.getFirst().getFormattedMessage()).isEqualTo("Operation completed");
        softly.assertThat(appender.list.getFirst().getLoggerName()).isEqualTo(ScopedOperations.class.getName());
        softly.assertThat(attributes())
            .containsEntry(LogKey.OPERATION, "accounts.open")
            .containsEntry("bank_account_id", "account-123")
            .containsEntry(LogKey.OUTCOME, "success")
            .containsKey(LogKey.DURATION_MS);
        softly.assertAll();
    }

    @Test
    void shouldDeriveOperationFromTargetClassAndMethod_whenAnnotationHasNoValue() {
        assertThat(operations.automaticallyNamed()).isEqualTo("automatically-named");

        assertThat(appender.list).hasSize(1);
        assertThat(attributes())
            .containsEntry(LogKey.OPERATION, "ScopedOperations.automaticallyNamed")
            .containsEntry(LogKey.OUTCOME, "success");
    }

    @Test
    void shouldUseAnnotationLevelAsCompletionDefault_whenContextDoesNotDeclareSeverity() {
        operations.debugByDefault();

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getLevel()).isEqualTo(Level.DEBUG);
        assertThat(attributes())
            .containsEntry(LogKey.OPERATION, "accounts.debugByDefault")
            .containsEntry(LogKey.OUTCOME, "success");
    }

    @Test
    void shouldUseContextSeverityOverAnnotationLevel_whenContextDeclaresSeverity() {
        operations.warnOverridesAnnotationLevel();

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getLevel()).isEqualTo(Level.WARN);
        assertThat(appender.list.getFirst().getFormattedMessage()).isEqualTo("Context warning");
    }

    @Test
    void shouldKeepFailureDefaultAtWarn_whenAnnotationLevelIsDebug() {
        operations.failureWithDebugAnnotation();

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getLevel()).isEqualTo(Level.WARN);
        assertThat(attributes())
            .containsEntry(LogKey.OPERATION, "ScopedOperations.failureWithDebugAnnotation")
            .containsEntry(LogKey.OUTCOME, "failure");
    }

    @Test
    void shouldUseWarnAnnotationLevel_whenContextDoesNotDeclareSeverity() {
        operations.warnByDefault();

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getLevel()).isEqualTo(Level.WARN);
    }

    @Test
    void shouldUseErrorAnnotationLevel_whenContextDoesNotDeclareSeverity() {
        operations.errorByDefault();

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getLevel()).isEqualTo(Level.ERROR);
    }

    @Test
    void shouldEmitErrorEventAndPropagateOriginalException_whenAnnotatedMethodThrowsUnexpectedException() {
        assertThatThrownBy(operations::fail)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("unexpected application failure");

        var softly = new org.assertj.core.api.SoftAssertions();
        softly.assertThat(appender.list).hasSize(1);
        softly.assertThat(appender.list.getFirst().getLevel().toString()).isEqualTo("ERROR");
        softly.assertThat(appender.list.getFirst().getThrowableProxy().getMessage())
            .isEqualTo("unexpected application failure");
        softly.assertThat(attributes())
            .containsEntry(LogKey.OPERATION, "accounts.fail")
            .containsEntry("bank_account_id", "account-456")
            .containsEntry(LogKey.OUTCOME, "failure")
            .containsEntry(LogKey.REASON, "internal_error");
        softly.assertAll();
    }

    @Test
    void shouldEmitClassifiedFailureAtErrorWithPropagatedException_whenAnnotatedMethodThrowsAfterFailureDeclaration() {
        assertThatThrownBy(operations::classifiedFailure)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("account is not active");

        var softly = new org.assertj.core.api.SoftAssertions();
        softly.assertThat(appender.list).hasSize(1);
        softly.assertThat(appender.list.getFirst().getLevel().toString()).isEqualTo("ERROR");
        softly.assertThat(appender.list.getFirst().getFormattedMessage()).isEqualTo("Account is not active");
        softly.assertThat(appender.list.getFirst().getThrowableProxy().getMessage())
            .isEqualTo("account is not active");
        softly.assertThat(attributes())
            .containsEntry(LogKey.OPERATION, "accounts.classifiedFailure")
            .containsEntry("bank_account_id", "account-789")
            .containsEntry(LogKey.OUTCOME, "failure")
            .containsEntry(LogKey.REASON, "not_active");
        softly.assertAll();
    }

    @Test
    void shouldRestoreOuterLogScope_whenAnnotatedOperationCallsAnotherScopedOperationThroughAnotherBean() {
        var outer = applicationContext.getBean(OuterScopedOperations.class);

        assertThat(outer.runNestedOperation()).isEqualTo("outer-result");

        assertThat(appender.list).hasSize(2);
        assertThat(attributes(appender.list.getFirst()))
            .containsEntry(LogKey.OPERATION, "accounts.open")
            .doesNotContainKey("scope");
        assertThat(attributes(appender.list.getLast()))
            .containsEntry(LogKey.OPERATION, "accounts.outer")
            .containsEntry("scope", "outer-after")
            .doesNotContainKey("bank_account_id");
    }

    @Test
    void shouldRestoreOuterLogScope_whenInnerExceptionIsCaughtByOuterOperation() {
        var outer = applicationContext.getBean(OuterScopedOperations.class);

        assertThat(outer.catchInnerFailure()).isEqualTo("outer-recovered");

        var softly = new org.assertj.core.api.SoftAssertions();
        softly.assertThat(appender.list).hasSize(2);
        softly.assertThat(attributes(appender.list.getFirst()))
            .containsEntry(LogKey.OPERATION, "accounts.fail")
            .containsEntry(LogKey.OUTCOME, "failure")
            .containsEntry("bank_account_id", "account-456")
            .doesNotContainKey("outer_state");
        softly.assertThat(attributes(appender.list.getLast()))
            .containsEntry(LogKey.OPERATION, "accounts.outer.catches")
            .containsEntry(LogKey.OUTCOME, "success")
            .containsEntry("outer_state", "after-inner-failure")
            .doesNotContainKey("bank_account_id");
        softly.assertAll();
    }

    @Test
    void shouldLogFailureInBothScopesAndPropagateSameException_whenInnerExceptionEscapesOuterOperation() {
        var outer = applicationContext.getBean(OuterScopedOperations.class);

        assertThatThrownBy(outer::propagateInnerFailure)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("unexpected application failure");

        var softly = new org.assertj.core.api.SoftAssertions();
        softly.assertThat(appender.list).hasSize(2);
        softly.assertThat(attributes(appender.list.getFirst()))
            .containsEntry(LogKey.OPERATION, "accounts.fail")
            .containsEntry(LogKey.OUTCOME, "failure");
        softly.assertThat(attributes(appender.list.getLast()))
            .containsEntry(LogKey.OPERATION, "accounts.outer.propagates")
            .containsEntry(LogKey.OUTCOME, "failure")
            .containsEntry(LogKey.REASON, "internal_error")
            .containsEntry("outer_state", "before-inner-failure");
        softly.assertThat(appender.list.getLast().getThrowableProxy().getMessage())
            .isEqualTo("unexpected application failure");
        softly.assertAll();
    }

    @Test
    void shouldPreserveBusinessReturnValue_whenCompletionLoggingFails() {
        replaceWithFailingPublisher();

        assertThat(operations.open()).isEqualTo("opened");
        assertThat(appender.list).isEmpty();
    }

    @Test
    void shouldPreserveBusinessException_whenCompletionLoggingFails() {
        replaceWithFailingPublisher();

        assertThatThrownBy(operations::fail)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("unexpected application failure");
        assertThat(appender.list).isEmpty();
    }

    private void replaceWithFailingPublisher() {
        applicationContext.close();
        applicationContext = new AnnotationConfigApplicationContext(FailingLoggingConfiguration.class);
        operations = applicationContext.getBean(ScopedOperations.class);
    }

    private Map<String, String> attributes() {
        return attributes(appender.list.getFirst());
    }

    private Map<String, String> attributes(ILoggingEvent event) {
        return event.getKeyValuePairs().stream()
            .collect(toMap(pair -> pair.key, pair -> String.valueOf(pair.value), (first, last) -> last));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class TestConfiguration {

        @Bean
        ScopedLogContext scopedLogContext() {
            return new ScopedLogContext();
        }

        @Bean
        LogScopeAspect logScopeAspect(ScopedLogContext logContext) {
            return new LogScopeAspect(new LogScopeExecutor(logContext, new Slf4jLogEventPublisher()));
        }

        @Bean
        ScopedOperations scopedOperations(ScopedLogContext logContext) {
            return new ScopedOperations(logContext);
        }

        @Bean
        OuterScopedOperations outerScopedOperations(ScopedLogContext logContext, ScopedOperations inner) {
            return new OuterScopedOperations(logContext, inner);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class FailingLoggingConfiguration {

        @Bean
        ScopedLogContext scopedLogContext() {
            return new ScopedLogContext();
        }

        @Bean
        LogScopeAspect logScopeAspect(ScopedLogContext logContext) {
            return new LogScopeAspect(new LogScopeExecutor(logContext, event -> {
                throw new IllegalStateException("log emission failed");
            }));
        }

        @Bean
        ScopedOperations scopedOperations(ScopedLogContext logContext) {
            return new ScopedOperations(logContext);
        }
    }

    public static class ScopedOperations {

        private final ScopedLogContext logContext;

        ScopedOperations(ScopedLogContext logContext) {
            this.logContext = logContext;
        }

        @LogScope("accounts.open")
        public String open() {
            logContext.with("bank_account_id", "account-123");
            return "opened";
        }

        @LogScope(value = "accounts.fail", level = LogLevel.DEBUG)
        public String fail() {
            logContext.with("bank_account_id", "account-456");
            logContext.success();
            throw new IllegalStateException("unexpected application failure");
        }

        @LogScope
        public String automaticallyNamed() {
            return "automatically-named";
        }

        @LogScope(value = "accounts.debugByDefault", level = LogLevel.DEBUG)
        public void debugByDefault() {}

        @LogScope(value = "accounts.warnOverridesAnnotationLevel", level = LogLevel.DEBUG)
        public void warnOverridesAnnotationLevel() {
            logContext.warn("Context warning");
        }

        @LogScope(level = LogLevel.DEBUG)
        public void failureWithDebugAnnotation() {
            logContext.failure();
        }

        @LogScope(value = "accounts.warnByDefault", level = LogLevel.WARN)
        public void warnByDefault() {}

        @LogScope(value = "accounts.errorByDefault", level = LogLevel.ERROR)
        public void errorByDefault() {}

        @LogScope("accounts.classifiedFailure")
        public void classifiedFailure() {
            logContext.with("bank_account_id", "account-789")
                .with(LogKey.REASON, "not_active")
                .warn("Account is not active");
            logContext.failure();
            throw new IllegalArgumentException("account is not active");
        }

    }

    public static class OuterScopedOperations {

        private final ScopedLogContext logContext;
        private final ScopedOperations inner;

        OuterScopedOperations(ScopedLogContext logContext, ScopedOperations inner) {
            this.logContext = logContext;
            this.inner = inner;
        }

        @LogScope("accounts.outer")
        public String runNestedOperation() {
            logContext.with("scope", "outer-before");
            inner.open();
            logContext.with("scope", "outer-after");
            return "outer-result";
        }

        @LogScope("accounts.outer.catches")
        public String catchInnerFailure() {
            try {
                inner.fail();
            }
            catch (IllegalStateException ignored) {
                logContext.with("outer_state", "after-inner-failure");
                return "outer-recovered";
            }
            throw new AssertionError("Expected inner operation to fail");
        }

        @LogScope("accounts.outer.propagates")
        public String propagateInnerFailure() {
            logContext.with("outer_state", "before-inner-failure");
            inner.fail();
            return "unreachable";
        }
    }
}
