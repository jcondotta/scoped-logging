package com.jcondotta.scopedlogging.application.logscope;

import com.jcondotta.scopedlogging.application.port.output.LogEventPublisher;
import com.jcondotta.scopedlogging.domain.logging.LogLevel;

import static java.util.Objects.requireNonNull;

/**
 * Executes synchronous operations within independent logging scopes.
 *
 * <p>Use the same context instance here and in collaborators that enrich the event.
 * Automatically attempts completion, including on exceptional exits, before returning
 * to the caller. Each successfully completed event receives one publication attempt;
 * delivery is not guaranteed. Outcomes and severity are independent; escaping exceptions
 * produce ERROR events when completion succeeds. RuntimeException and AssertionError
 * during completion or publication preserve the exact business result or escaping exception.
 * A single best-effort System.Logger WARNING diagnoses the logging failure; the same
 * recoverable failures in that diagnostic are ignored. Neither action nor publication is retried.
 *
 * <p>Other errors, including VirtualMachineError, ThreadDeath and LinkageError, propagate
 * and may override the business outcome. This is not protection against blocking, deadlocks,
 * infinite loops or arbitrary JVM failures. Validation, scope setup and explicit context
 * calls within the action are outside this completion isolation boundary.
 * Logger names and operation names must not be blank.
 *
 * <p>Nested calls bind independent state and restore the enclosing scope on exit.
 * Independent scopes may share an executor across platform or virtual threads when its
 * publisher supports concurrent calls. Returning a future or reactive publisher ends
 * the scope immediately; deferred work is neither awaited nor automatically scoped.
 *
 * <p>For example, with the SLF4J adapter:
 * <pre>{@code
 * var context = new ScopedLogContext();
 * var publisher = new com.jcondotta.scopedlogging.infrastructure.slf4j.Slf4jLogEventPublisher();
 * var scopes = new LogScopeExecutor(context, publisher);
 * String orderId = scopes.execute("orders", "orders.create", () -> {
 *     context.with("order.id", "123").info("Order created");
 *     return "123";
 * });
 * }</pre>
 *
 * @see ScopedLogContext
 * @see LogEventPublisher
 */
public final class LogScopeExecutor {

    private static final System.Logger LOGGER = System.getLogger(LogScopeExecutor.class.getName());

    private final ScopedLogContext context;
    private final LogEventPublisher eventPublisher;

    /**
     * Creates an executor using the context shared by the operation's collaborators.
     *
     * @param context context in which each invocation binds its state
     * @param eventPublisher destination for completed events; must support concurrent
     *                       calls if this executor is shared across concurrent scopes
     * @throws NullPointerException if either argument is null
     */
    public LogScopeExecutor(ScopedLogContext context, LogEventPublisher eventPublisher) {
        this.context = requireNonNull(context, Errors.CONTEXT_REQUIRED);
        this.eventPublisher = requireNonNull(eventPublisher, Errors.PUBLISHER_REQUIRED);
    }

    /**
     * Executes a value-producing action with INFO as the default severity.
     * Completion follows the same policy as {@link #execute(String, String, LogLevel, ScopedValue.CallableOp)}.
     *
     * @param <T> action result type
     * @param <X> throwable type propagated by the action
     * @param loggerName non-blank name of the destination logger
     * @param operation non-blank operation name stored in the event
     * @param action synchronous action to invoke once within the scope
     * @return the action's original result, including null, after completion is attempted
     * @throws NullPointerException if any argument is null
     * @throws IllegalArgumentException if {@code loggerName} or {@code operation} is blank
     * @throws X if the action throws
     */
    public <T, X extends Throwable> T execute(String loggerName, String operation, ScopedValue.CallableOp<T, X> action) throws X {
        return execute(loggerName, operation, LogLevel.INFO, action);
    }

    /**
     * Executes a value-producing action in a fresh scope and attempts completion before exit.
     * Normal return defaults to success. Explicit event declarations override the default
     * severity; a declared failure without an event declaration defaults to WARN.
     * An escaping throwable determines failure outcome, ERROR severity and the event cause.
     *
     * <p>Argument validation occurs before invoking the action. Runtime exceptions and
     * assertion errors from automatic completion or publication do not replace the action's
     * result or throwable. Other errors can propagate as described in the class contract.
     * Parent bindings are restored when the scope exits.
     *
     * @param <T> action result type
     * @param <X> throwable type propagated by the action
     * @param loggerName non-blank name of the destination logger
     * @param operation non-blank operation name stored in the event
     * @param level default severity for normal completion without a declaration or failure outcome
     * @param action synchronous action to invoke once within the scope
     * @return the action's original result, including null, after completion is attempted
     * @throws NullPointerException if any argument is null
     * @throws IllegalArgumentException if {@code loggerName} or {@code operation} is blank
     * @throws X if the action throws
     */
    public <T, X extends Throwable> T execute(String loggerName, String operation, LogLevel level, ScopedValue.CallableOp<T, X> action) throws X {
        requireNonNull(loggerName, Errors.LOGGER_NAME_REQUIRED);
        requireNonNull(operation, Errors.OPERATION_REQUIRED);
        if (loggerName.isBlank()) {
            throw new IllegalArgumentException(Errors.LOGGER_NAME_MUST_NOT_BE_BLANK);
        }
        if (operation.isBlank()) {
            throw new IllegalArgumentException(Errors.OPERATION_MUST_NOT_BE_BLANK);
        }
        requireNonNull(level, Errors.LEVEL_REQUIRED);
        requireNonNull(action, Errors.EXECUTE_ACTION_REQUIRED);

        return context.call(loggerName, operation, level, () -> {
            Throwable failure = null;
            try {
                return action.call();
            }
            catch (Throwable error) {
                failure = error;
                throw error;
            }
            finally {
                try {
                    eventPublisher.publish(context.complete(failure));
                }
                catch (RuntimeException | AssertionError loggingError) {
                    // Keep the business result or original exception if emission fails.
                    try {
                        LOGGER.log(System.Logger.Level.WARNING,
                            "Could not write operation completion log for " + operation, loggingError);
                    }
                    catch (RuntimeException | AssertionError ignored) {
                        // A failing diagnostic logger must not replace the business result either.
                    }
                }
            }
        });
    }

    /**
     * Runs an action without a return value using INFO as the default severity.
     * Completion follows the same policy as {@link #run(String, String, LogLevel, LogAction)}.
     *
     * @param <X> throwable type propagated by the action
     * @param loggerName non-blank name of the destination logger
     * @param operation non-blank operation name stored in the event
     * @param action synchronous action to invoke once within the scope
     * @throws NullPointerException if any argument is null
     * @throws IllegalArgumentException if {@code loggerName} or {@code operation} is blank
     * @throws X if the action throws
     */
    public <X extends Throwable> void run(String loggerName, String operation, LogAction<X> action) throws X {
        run(loggerName, operation, LogLevel.INFO, action);
    }

    /**
     * Runs an action without a return value in a fresh scope with the supplied default severity.
     * Delegates to {@link #execute(String, String, LogLevel, ScopedValue.CallableOp)},
     * including its completion, exception propagation and parent restoration policies.
     *
     * @param <X> throwable type propagated by the action
     * @param loggerName non-blank name of the destination logger
     * @param operation non-blank operation name stored in the event
     * @param level default severity for normal completion without a declaration or failure outcome
     * @param action synchronous action to invoke once within the scope
     * @throws NullPointerException if any argument is null
     * @throws IllegalArgumentException if {@code loggerName} or {@code operation} is blank
     * @throws X if the action throws
     */
    public <X extends Throwable> void run(String loggerName, String operation, LogLevel level, LogAction<X> action) throws X {
        requireNonNull(action, Errors.RUN_ACTION_REQUIRED);
        execute(loggerName, operation, level, (ScopedValue.CallableOp<Void, X>) () -> {
            action.run();
            return null;
        });
    }

    /** Validation messages for executor construction and invocation. */
    private static final class Errors {
        private static final String CONTEXT_REQUIRED = "scoped log context must not be null";
        private static final String LOGGER_NAME_REQUIRED = "logger name must not be null";
        private static final String LOGGER_NAME_MUST_NOT_BE_BLANK = "logger name must not be blank";
        private static final String OPERATION_REQUIRED = "operation must not be null";
        private static final String OPERATION_MUST_NOT_BE_BLANK = "operation must not be blank";
        private static final String LEVEL_REQUIRED = "log level must not be null";
        private static final String EXECUTE_ACTION_REQUIRED = "execute action must not be null";
        private static final String RUN_ACTION_REQUIRED = "run action must not be null";
        private static final String PUBLISHER_REQUIRED = "log event publisher must not be null";

        /** Prevents instantiation of the message holder. */
        private Errors() { }
    }
}
