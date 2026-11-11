package com.jcondotta.scopedlogging.application.logscope;

import com.jcondotta.scopedlogging.domain.logging.LogKey;
import com.jcondotta.scopedlogging.domain.logging.LogLevel;
import com.jcondotta.scopedlogging.infrastructure.slf4j.Slf4jLogEventPublisher;

import ch.qos.logback.classic.Level;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static com.jcondotta.scopedlogging.application.logscope.ScopeEventCapture.attributes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScopedLogContextConcurrencyTest {

    private static final String OWNER_ATTRIBUTE = "owner";
    private static final String RESTORED_ATTRIBUTE = "restored";
    private static final String UPDATED_ATTRIBUTE = "updated";
    private static final String FIRST_VALUE = "first";
    private static final String LAST_VALUE = "last";
    private static final String TRUE_VALUE = "true";
    private static final String SUCCESS_OUTCOME = "success";
    private static final String FAILURE_OUTCOME = "failure";
    private static final String WORKER_FAILURE_MESSAGE = "worker-0-failure";
    private static final String INTERNAL_ERROR_REASON = "internal_error";
    static Stream<Arguments> scenarios() {
        var cases = new ArrayList<Arguments>();
        for (boolean virtual : new boolean[]{false, true})
            for (int threads : new int[]{2, 3})
                for (int scopes : new int[]{1, 2, 3})
                    for (boolean nested : new boolean[]{false, true})
                        for (boolean failure : new boolean[]{false, true})
                            cases.add(Arguments.of(virtual, threads, scopes, nested, failure));
        return cases.stream();
    }

    @ParameterizedTest(name = "virtual={0}, threads={1}, scopes={2}, nested={3}, failure={4}")
    @MethodSource("scenarios")
    void shouldIsolateAndRestoreScopes_whenWorkersOverlap(boolean virtual, int threads, int scopes,
                                                         boolean nested, boolean failure) throws Exception {
        exercise(virtual, threads, scopes, nested, failure, 2);
    }

    @Test
    @EnabledIfSystemProperty(named = "scoped.logging.stress", matches = TRUE_VALUE)
    void shouldKeepEventsIndependent_whenManyDeepScopesRunConcurrently() throws Exception {
        exercise(false, 8, 20, true, true, 20);
        exercise(true, 8, 20, true, true, 20);
    }

    private void exercise(boolean virtual, int threads, int scopes, boolean nested,
                          boolean failure, int rounds) throws Exception {
        var context = new ScopedLogContext();
        var scopesExecutor = new LogScopeExecutor(context, new Slf4jLogEventPublisher());
        var barrier = new CyclicBarrier(threads);
        try (var capture = new ScopeEventCapture()) {
            var pool = virtual ? Executors.newVirtualThreadPerTaskExecutor() : Executors.newFixedThreadPool(threads);
            try {
                for (int round = 0; round < rounds; round++) {
                    var futures = new ArrayList<Future<?>>();
                    for (int worker = 0; worker < threads; worker++) {
                        int workerId = worker;
                        int roundId = round;
                        futures.add(pool.submit(() -> {
                            if (nested) {
                                invoke(context, scopesExecutor, capture, barrier, workerId, roundId, 0, scopes, true, failure);
                            }
                            else {
                                for (int scope = 0; scope < scopes; scope++) {
                                    invoke(context, scopesExecutor, capture, barrier, workerId, roundId, scope, scope + 1, false, failure);
                                }
                            }
                            assertThatThrownBy(() -> context.with("outside", true)).isInstanceOf(IllegalStateException.class);
                            return null;
                        }));
                    }
                    for (var future : futures) future.get(30, TimeUnit.SECONDS);
                }
            }
            finally {
                pool.shutdownNow();
                assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            }
            assertThat(capture.events()).hasSize(threads * scopes * rounds);
            var softly = new SoftAssertions();
            for (int round = 0; round < rounds; round++) {
                for (int worker = 0; worker < threads; worker++) {
                    final String prefix = round + "/" + worker + "/";
                    var events = capture.events().stream()
                        .filter(event -> attributes(event).get(LogKey.OPERATION).startsWith(prefix)).toList();
                    softly.assertThat(events).hasSize(scopes);
                    for (int index = 0; index < scopes; index++) {
                        int scope = nested ? scopes - index - 1 : index;
                        var event = events.get(index);
                        var values = attributes(event);
                        var id = prefix + scope;
                        boolean failed = failure && worker == 0;
                        softly.assertThat(values)
                            .containsEntry(LogKey.OPERATION, id)
                            .containsEntry(OWNER_ATTRIBUTE, id)
                            .containsEntry("owned_" + scope, TRUE_VALUE)
                            .containsEntry(LogKey.OUTCOME, failed ? FAILURE_OUTCOME : SUCCESS_OUTCOME);
                        softly.assertThat(values).hasSize(failed ? 6 : 7);
                        if (failed) {
                            softly.assertThat(values).containsEntry(LogKey.REASON, INTERNAL_ERROR_REASON);
                            softly.assertThat(event.getThrowableProxy().getMessage()).isEqualTo(WORKER_FAILURE_MESSAGE);
                            softly.assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                        }
                        else {
                            softly.assertThat(values).containsEntry(RESTORED_ATTRIBUTE, id).containsEntry(UPDATED_ATTRIBUTE, LAST_VALUE);
                            softly.assertThat(event.getThrowableProxy()).isNull();
                            softly.assertThat(event.getLevel()).isEqualTo(worker % 2 == 0 ? Level.DEBUG : Level.WARN);
                        }
                        softly.assertThat(event.getFormattedMessage()).isEqualTo(id);
                        softly.assertThat(Long.parseLong(values.get(LogKey.DURATION_MS))).isGreaterThanOrEqualTo(0);
                    }
                }
            }
            softly.assertAll();
        }
    }

    private void invoke(ScopedLogContext context, LogScopeExecutor executor, ScopeEventCapture capture,
                        CyclicBarrier barrier, int worker, int round, int scope, int limit,
                        boolean nested, boolean failure) throws Exception {
        try {
            runScope(context, executor, capture, barrier, worker, round, scope, limit, nested, failure);
        }
        catch (IOException expected) {
            assertThat(worker).isZero();
            assertThat(failure).isTrue();
        }
    }

    private void runScope(ScopedLogContext context, LogScopeExecutor executor, ScopeEventCapture capture,
                          CyclicBarrier barrier, int worker, int round, int scope, int limit,
                          boolean nested, boolean failure) throws Exception {
        String id = round + "/" + worker + "/" + scope;
        executor.run(capture.logger.getName(), id, () -> {
            context.with(OWNER_ATTRIBUTE, id).with("owned_" + scope, true);
            if (worker % 2 == 0) context.debug(id); else context.warn(id);
            barrier.await(10, TimeUnit.SECONDS);
            if (nested && scope + 1 < limit) {
                runScope(context, executor, capture, barrier, worker, round, scope + 1, limit, true, failure);
            }
            else if (failure && worker == 0) {
                throw new IOException(WORKER_FAILURE_MESSAGE);
            }
            context.with(RESTORED_ATTRIBUTE, id).with(UPDATED_ATTRIBUTE, FIRST_VALUE).with(UPDATED_ATTRIBUTE, LAST_VALUE).success();
        });
    }
}
