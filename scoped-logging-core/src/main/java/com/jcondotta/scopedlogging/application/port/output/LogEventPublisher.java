package com.jcondotta.scopedlogging.application.port.output;

import com.jcondotta.scopedlogging.domain.logging.LogEvent;

/**
 * Output port for publishing a completed scope event.
 *
 * <p>When invoked by {@link com.jcondotta.scopedlogging.application.logscope.LogScopeExecutor},
 * {@link RuntimeException} and {@link AssertionError} are isolated
 * and diagnosed once through System.Logger on a best-effort basis. Other errors,
 * including VirtualMachineError, ThreadDeath and LinkageError, propagate and can override
 * the business outcome. Recoverable failures in the diagnostic fallback are also isolated.
 * Neither business execution nor publication is retried. Direct callers
 * must provide their own failure policy. Publishers shared across independent concurrent
 * scopes must support concurrent calls.
 * The executor makes one publication attempt per successfully completed event. An implementation may
 * perform partial work before failing, so this contract does not guarantee event delivery
 * exactly once. Implementations should avoid recursively invoking instrumented operations
 * during publication unless recursion is explicitly controlled.
 */
@FunctionalInterface
public interface LogEventPublisher {
    /**
     * Attempts to publish one completed event according to the implementation's delivery policy.
     * The event's attribute map is unmodifiable, but values are retained by reference.
     * Implementations that defer delivery must capture mutable values before returning
     * if a stable snapshot is required.
     *
     * @param event completed event supplied by the executor; never null
     */
    void publish(LogEvent event);
}
