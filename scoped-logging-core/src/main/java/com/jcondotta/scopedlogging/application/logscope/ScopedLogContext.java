package com.jcondotta.scopedlogging.application.logscope;

import com.jcondotta.scopedlogging.domain.logging.LogEvent;
import com.jcondotta.scopedlogging.domain.logging.LogLevel;
import com.jcondotta.scopedlogging.domain.logging.LogOutcome;

/**
 * Binds a fresh mutable logging state to each synchronous invocation on a platform or virtual thread.
 * Independent invocations can share this context. Nested scopes isolate attributes and
 * restore their parent binding automatically, including on exceptional exits.
 * Scopes end when the action returns; future or reactive work is not awaited. There is
 * no automatic propagation to arbitrary asynchronous execution or raw child threads.
 * Structured child tasks may inherit the same mutable state; concurrent mutation of that
 * inherited state is unsupported and has no isolation guarantee. Open independent scopes
 * for independent work. Context access outside an active scope throws IllegalStateException.
 */
public class ScopedLogContext implements LogContext {

    private final ScopedValue<LogState> current = ScopedValue.newInstance();

    <T, X extends Throwable> T call(String loggerName, String operation, LogLevel defaultSeverity,
                                    ScopedValue.CallableOp<T, X> action) throws X {
        var logState = new LogState(loggerName, operation, defaultSeverity);
        return ScopedValue.where(current, logState).call(action);
    }

    @Override
    public LogContext with(String key, Object value) {
        currentLogState().addAttribute(key, value);
        return this;
    }

    @Override
    public LogContext debug(String message) {
        return event(LogLevel.DEBUG, message);
    }

    @Override
    public LogContext info(String message) {
        return event(LogLevel.INFO, message);
    }

    @Override
    public LogContext warn(String message) {
        return event(LogLevel.WARN, message);
    }

    @Override
    public LogContext error(String message, Throwable error) {
        return event(LogLevel.ERROR, message, error);
    }

    @Override
    public void success() {
        currentLogState().setOutcome(LogOutcome.SUCCESS);
    }

    @Override
    public void failure() {
        currentLogState().setOutcome(LogOutcome.FAILURE);
    }

    private LogContext event(LogLevel severity, String message, Throwable error) {
        currentLogState().declareEvent(severity, message, error);
        return this;
    }

    private LogContext event(LogLevel severity, String message) {
        return event(severity, message, null);
    }

    private LogState currentLogState() {
        return current.orElseThrow(() ->
            new IllegalStateException(Errors.NO_ACTIVE_SCOPE));
    }

    LogEvent complete(Throwable error) {
        return currentLogState().complete(error);
    }

    private static final class Errors {
        private static final String NO_ACTIVE_SCOPE =
            "no active logging scope; access LogContext only within LogScopeExecutor.run or execute";

        private Errors() { }
    }
}
