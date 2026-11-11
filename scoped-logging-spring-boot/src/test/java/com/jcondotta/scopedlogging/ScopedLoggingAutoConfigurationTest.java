package com.jcondotta.scopedlogging;

import com.jcondotta.scopedlogging.application.logscope.LogContext;
import com.jcondotta.scopedlogging.application.logscope.LogScopeExecutor;
import com.jcondotta.scopedlogging.application.logscope.ScopedLogContext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.aop.support.AopUtils;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScopedLoggingAutoConfigurationTest {

    @Test
    void shouldProvideContextAndInterceptOperations_whenConsumerDoesNotScanLibraryPackage() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ScopedLoggingAutoConfiguration.class))
            .withBean(Operation.class)
            .run(context -> {
                assertThat(context).hasSingleBean(LogContext.class)
                    .hasSingleBean(LogScopeExecutor.class).hasSingleBean(LogScopeAspect.class);
                var operation = context.getBean(Operation.class);
                assertThat(AopUtils.isAopProxy(operation)).isTrue();
                assertThat(operation.execute()).isEqualTo("done");
                assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                    context.getBean(LogContext.class).with("outside", true)))
                    .isInstanceOf(IllegalStateException.class);
            });
    }

    static Stream<Throwable> publisherFailures() {
        return Stream.of(new IllegalStateException("publisher failed"), new AssertionError("publisher failed"));
    }

    @ParameterizedTest
    @MethodSource("publisherFailures")
    void shouldUseCustomExecutorAndPreserveBusinessOutcomes_whenPublisherFails(Throwable loggingFailure) {
        var logContext = new ScopedLogContext();
        var publications = new AtomicInteger();
        var executor = new LogScopeExecutor(logContext, event -> {
            publications.incrementAndGet();
            if (loggingFailure instanceof RuntimeException runtimeException) throw runtimeException;
            throw (AssertionError) loggingFailure;
        });
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ScopedLoggingAutoConfiguration.class))
            .withBean(ScopedLogContext.class, () -> logContext)
            .withBean(LogScopeExecutor.class, () -> executor)
            .withBean(Operation.class)
            .run(application -> {
                assertThat(application).hasSingleBean(LogScopeExecutor.class).hasSingleBean(ScopedLogContext.class);
                assertThat(application.getBean(LogScopeExecutor.class)).isSameAs(executor);
                var operation = application.getBean(Operation.class);
                var result = new Object();
                var original = new IOException("business failed");

                assertThat(operation.echo(result)).isSameAs(result);
                assertThatThrownBy(() -> operation.fail(original)).isSameAs(original);
                assertThat(publications).hasValue(2);
                assertThatThrownBy(logContext::success).isInstanceOf(IllegalStateException.class);
            });
    }

    static class Operation {
        private final LogContext logContext;

        Operation(LogContext logContext) {
            this.logContext = logContext;
        }

        @LogScope("test.echo")
        public Object echo(Object result) {
            logContext.with("test_attribute", "present");
            return result;
        }

        @LogScope("test.fail")
        public void fail(IOException original) throws IOException {
            throw original;
        }

        @LogScope("test.operation")
        public String execute() {
            logContext.with("test_attribute", "present");
            return "done";
        }
    }
}
