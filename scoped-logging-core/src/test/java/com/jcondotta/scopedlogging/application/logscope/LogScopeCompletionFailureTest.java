package com.jcondotta.scopedlogging.application.logscope;

import com.jcondotta.scopedlogging.domain.logging.LogEvent;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

// These tests temporarily configure the JDK System.Logger's JUL backend.
@Isolated
class LogScopeCompletionFailureTest {

    private static final String LOGGER_NAME = "completion-failure-test";
    private static final String OPERATION = "operation";

    static Stream<Throwable> recoverableFailures() {
        return Stream.of(new IllegalStateException("logging failed"), new AssertionError("logging failed"));
    }

    @SuppressWarnings("removal")
    static Stream<Error> fatalErrors() {
        // Throw synthetic instances synchronously; do not exhaust memory or stop a thread.
        return Stream.of(new VirtualMachineError("synthetic VM failure") {},
            new LinkageError("synthetic linkage failure"), new ThreadDeath());
    }

    @ParameterizedTest
    @MethodSource("fatalErrors")
    void shouldPropagateFatalError_whenPublisherFailsAfterSuccess(Error error) {
        assertFatalErrorPropagates(error, false);
    }

    @ParameterizedTest
    @MethodSource("fatalErrors")
    void shouldPropagateFatalError_whenPublisherFailsAfterBusinessException(Error error) {
        assertFatalErrorPropagates(error, true);
    }

    @ParameterizedTest
    @MethodSource("recoverableFailures")
    void shouldPreserveBusinessResult_whenCompletionFails(Throwable failure) {
        assertCompletionFailureIsIsolated(failure, false);
    }

    @ParameterizedTest
    @MethodSource("recoverableFailures")
    void shouldPreserveBusinessException_whenCompletionFails(Throwable failure) {
        assertCompletionFailureIsIsolated(failure, true);
    }

    @ParameterizedTest
    @MethodSource("recoverableFailures")
    void shouldPreserveBusinessResult_whenDiagnosticFails(Throwable failure) {
        assertDiagnosticFailureIsIsolated(failure, false);
    }

    @ParameterizedTest
    @MethodSource("recoverableFailures")
    void shouldPreserveBusinessException_whenDiagnosticFails(Throwable failure) {
        assertDiagnosticFailureIsIsolated(failure, true);
    }

    private void assertFatalErrorPropagates(Error error, boolean businessFailure) {
        var executions = new AtomicInteger();
        var publications = new AtomicInteger();
        var context = new ScopedLogContext();
        var executor = new LogScopeExecutor(context, event -> {
            publications.incrementAndGet();
            throw error;
        });
        var original = new IOException("business failed");

        var thrown = catchThrowable(() -> executor.execute(LOGGER_NAME, OPERATION, () -> {
            executions.incrementAndGet();
            if (businessFailure) throw original;
            return new Object();
        }));

        assertThat(thrown).isSameAs(error);
        assertThat(executions).hasValue(1);
        assertThat(publications).hasValue(1);
        assertThat(catchThrowable(context::success)).isInstanceOf(IllegalStateException.class);
    }

    private void assertCompletionFailureIsIsolated(Throwable failure, boolean businessFailure) {
        var completions = new AtomicInteger();
        var publications = new AtomicInteger();
        // Exercise the existing package-visible completion seam without changing production design.
        var context = new ScopedLogContext() {
            @Override
            LogEvent complete(Throwable error) {
                completions.incrementAndGet();
                throwFailure(failure);
                return super.complete(error);
            }
        };
        var executor = new LogScopeExecutor(context, event -> publications.incrementAndGet());

        assertBusinessOutcome(executor, businessFailure);

        assertThat(completions).hasValue(1);
        assertThat(publications).hasValue(0);
        assertThat(catchThrowable(context::success)).isInstanceOf(IllegalStateException.class);
    }

    private void assertDiagnosticFailureIsIsolated(Throwable failure, boolean businessFailure) {
        var diagnostics = new ArrayList<LogRecord>();
        var publications = new AtomicInteger();
        var publicationFailure = new IllegalStateException("publisher failed");
        var logger = Logger.getLogger(LogScopeExecutor.class.getName());
        var originalLevel = logger.getLevel();
        var originalUseParentHandlers = logger.getUseParentHandlers();
        var handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                diagnostics.add(record);
                throwFailure(failure);
            }

            @Override
            public void flush() { }

            @Override
            public void close() { }
        };
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        try {
            var executor = new LogScopeExecutor(new ScopedLogContext(), event -> {
                publications.incrementAndGet();
                throw publicationFailure;
            });
            assertBusinessOutcome(executor, businessFailure);
            assertThat(publications).hasValue(1);
            assertThat(diagnostics).hasSize(1);
            assertThat(diagnostics.getFirst().getThrown()).isSameAs(publicationFailure);
            assertThat(diagnostics.getFirst().getLevel()).isEqualTo(Level.WARNING);
        }
        finally {
            logger.removeHandler(handler);
            logger.setLevel(originalLevel);
            logger.setUseParentHandlers(originalUseParentHandlers);
        }
    }

    private void assertBusinessOutcome(LogScopeExecutor executor, boolean businessFailure) {
        var original = new IOException("business failed");
        var expected = new Object();
        var executions = new AtomicInteger();
        var thrown = catchThrowable(() -> {
            var actual = executor.execute(LOGGER_NAME, OPERATION, () -> {
                executions.incrementAndGet();
                if (businessFailure) throw original;
                return expected;
            });
            assertThat(actual).isSameAs(expected);
        });
        assertThat(thrown).isSameAs(businessFailure ? original : null);
        assertThat(executions).hasValue(1);
    }

    private static void throwFailure(Throwable failure) {
        if (failure instanceof RuntimeException runtimeException) throw runtimeException;
        throw (AssertionError) failure;
    }
}
