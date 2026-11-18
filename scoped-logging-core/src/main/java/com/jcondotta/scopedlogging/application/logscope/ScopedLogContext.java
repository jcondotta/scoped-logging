package com.jcondotta.scopedlogging.application.logscope;

import com.jcondotta.scopedlogging.domain.logging.LogEvent;
import com.jcondotta.scopedlogging.domain.logging.LogLevel;
import com.jcondotta.scopedlogging.domain.logging.LogOutcome;

/**
 * Binds a fresh mutable logging state to each synchronous invocation on a platform or virtual thread.
 *
 * <p>Independent invocations can share this context. Nested scopes isolate attributes and
 * restore their parent binding automatically, including on exceptional exits.
 * Scopes end when the action returns; future or reactive work is not awaited. There is
 * no automatic propagation to arbitrary asynchronous execution or raw child threads.
 * Structured child tasks may inherit the same mutable state; concurrent mutation of that
 * inherited state is unsupported and has no isolation guarantee. Open independent scopes
 * for independent work. Context access outside an active scope throws IllegalStateException.
 *
 * <p>Creating a context does not open a scope. Pass the same instance to a
 * {@link LogScopeExecutor} and to collaborators that enrich its events.
 *
 * @see LogContext
 */
public class ScopedLogContext implements LogContext {

    private final ScopedValue<LogState> current = ScopedValue.newInstance();

    /**
     * Creates an unbound context ready to be shared with an executor and its collaborators.
     * No logging scope is active until the executor starts an invocation.
     */
    public ScopedLogContext() {
    }

    /**
     * Binds fresh state for the action and restores the previous binding on exit.
     * The executor validates arguments and handles completion inside this binding.
     *
     * @param <T> action result type
     * @param <X> throwable type propagated by the action
     * @param loggerName destination logger name validated by the executor
     * @param operation operation name validated by the executor
     * @param defaultSeverity default severity used when completing the state
     * @param action action to invoke while the state is bound
     * @return the action's result, including null
     * @throws X if the action throws
     */
    <T, X extends Throwable> T call(String loggerName, String operation, LogLevel defaultSeverity,
                                    ScopedValue.CallableOp<T, X> action) throws X {
        var logState = new LogState(loggerName, operation, defaultSeverity);
        return ScopedValue.where(current, logState).call(action);
    }

    /** {@inheritDoc} */
    @Override
    public LogContext with(String key, Object value) {
        currentLogState().addAttribute(key, value);
        return this;
    }

    /** {@inheritDoc} */
    @Override
    public LogContext debug(String message) {
        return event(LogLevel.DEBUG, message);
    }

    /** {@inheritDoc} */
    @Override
    public LogContext info(String message) {
        return event(LogLevel.INFO, message);
    }

    /** {@inheritDoc} */
    @Override
    public LogContext warn(String message) {
        return event(LogLevel.WARN, message);
    }

    /** {@inheritDoc} */
    @Override
    public LogContext error(String message, Throwable error) {
        return event(LogLevel.ERROR, message, error);
    }

    /** {@inheritDoc} */
    @Override
    public void success() {
        currentLogState().setOutcome(LogOutcome.SUCCESS);
    }

    /** {@inheritDoc} */
    @Override
    public void failure() {
        currentLogState().setOutcome(LogOutcome.FAILURE);
    }

    /**
     * Replaces the active state's event declaration without publishing it.
     *
     * @param severity severity of the replacement declaration
     * @param message non-null completion message
     * @param error optional cause, replacing any previous cause
     * @return this context
     * @throws IllegalStateException if no scope is active
     * @throws NullPointerException if severity or message is null
     */
    private LogContext event(LogLevel severity, String message, Throwable error) {
        currentLogState().declareEvent(severity, message, error);
        return this;
    }

    /**
     * Replaces the event declaration and clears any previous cause.
     *
     * @param severity severity of the replacement declaration
     * @param message non-null completion message
     * @return this context
     * @throws IllegalStateException if no scope is active
     * @throws NullPointerException if severity or message is null
     */
    private LogContext event(LogLevel severity, String message) {
        return event(severity, message, null);
    }

    /**
     * Resolves the state bound to the current invocation.
     *
     * @return active state for this context
     * @throws IllegalStateException if no scope is active
     */
    private LogState currentLogState() {
        return current.orElseThrow(() ->
            new IllegalStateException(Errors.NO_ACTIVE_SCOPE));
    }

    /**
     * Builds the current scope's completion event without publishing or unbinding it.
     *
     * @param error escaping throwable, or null for normal return
     * @return a completed event with a shallow attribute snapshot
     * @throws IllegalStateException if no scope is active
     */
    LogEvent complete(Throwable error) {
        return currentLogState().complete(error);
    }

    /** Diagnostic text for context access outside a scope. */
    private static final class Errors {
        private static final String NO_ACTIVE_SCOPE =
            "no active logging scope; access LogContext only within LogScopeExecutor.run or execute";

        /** Prevents instantiation of the message holder. */
        private Errors() { }
    }
}
