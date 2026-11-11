package com.jcondotta.scopedlogging.application.logscope;

import com.jcondotta.scopedlogging.domain.logging.LogKey;
import com.jcondotta.scopedlogging.domain.logging.LogLevel;
import com.jcondotta.scopedlogging.infrastructure.slf4j.Slf4jLogEventPublisher;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.jcondotta.scopedlogging.application.logscope.ScopeEventCapture.attributes;
import static org.assertj.core.api.Assertions.*;

class LogScopeInterruptionTest {

    private static final String INTERRUPTED_OPERATION = "interrupted";
    private static final String NEXT_OPERATION = "next";
    private static final String INTERRUPTED_ATTRIBUTE = "interrupted_attribute";
    private static final String TRUE_ATTRIBUTE_VALUE = "true";
    private static final String SUCCESS_OUTCOME = "success";
    private static final String FAILURE_OUTCOME = "failure";
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldCompleteAndCleanContext_whenRunningTaskIsCancelledByInterruption(boolean virtual) throws Exception {
        var context = new ScopedLogContext();
        var executor = new LogScopeExecutor(context, new Slf4jLogEventPublisher());
        var entered = new CountDownLatch(1);
        var exited = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var capture = new ScopeEventCapture()) {
            var pool = virtual ? Executors.newVirtualThreadPerTaskExecutor() : Executors.newSingleThreadExecutor();
            try {
                var future = pool.submit(() -> {
                    try {
                        executor.run(capture.logger.getName(), INTERRUPTED_OPERATION, () -> {
                            context.with(INTERRUPTED_ATTRIBUTE, true);
                            entered.countDown();
                            if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("Cancellation did not interrupt operation");
                        });
                    }
                    finally { exited.countDown(); }
                    return null;
                });
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(future.cancel(true)).isTrue();
                assertThat(exited.await(5, TimeUnit.SECONDS)).isTrue();
                pool.submit(() -> {
                    assertThatThrownBy(context::success).isInstanceOf(IllegalStateException.class);
                    executor.run(capture.logger.getName(), NEXT_OPERATION, () -> {});
                }).get(5, TimeUnit.SECONDS);
                assertThat(capture.events()).hasSize(2);
                var interrupted = capture.events().getFirst();
                assertThat(attributes(interrupted)).containsEntry(LogKey.OUTCOME, FAILURE_OUTCOME)
                    .containsEntry(INTERRUPTED_ATTRIBUTE, TRUE_ATTRIBUTE_VALUE);
                assertThat(interrupted.getThrowableProxy().getClassName()).isEqualTo(InterruptedException.class.getName());
                assertThat(attributes(capture.events().getLast())).containsEntry(LogKey.OUTCOME, SUCCESS_OUTCOME)
                    .doesNotContainKeys(INTERRUPTED_ATTRIBUTE, LogKey.REASON);
            }
            finally {
                release.countDown();
                pool.shutdownNow();
                assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            }
        }
    }
}
