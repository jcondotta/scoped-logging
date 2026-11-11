package com.jcondotta.scopedlogging.application.logscope;

import com.jcondotta.scopedlogging.application.port.output.LogEventPublisher;
import com.jcondotta.scopedlogging.domain.logging.LogLevel;

import static java.util.Objects.requireNonNull;

/**
 * Executes synchronous operations within independent logging scopes.
 * Use the same context instance here and in collaborators that enrich the event.
 * Automatically attempts completion, including on exceptional exits, before returning
 * to the caller. Each successfully completed event receives one publication attempt;
 * delivery is not guaranteed. Outcomes and severity are independent; escaping exceptions
 * produce ERROR events when completion succeeds. RuntimeException and AssertionError
 * during completion or publication preserve the exact business result or escaping exception.
 * A single best-effort System.Logger WARNING diagnoses the logging failure; the same
 * recoverable failures in that diagnostic are ignored. Neither action nor publication is retried.
 * Other errors, including VirtualMachineError, ThreadDeath and LinkageError, propagate
 * and may override the business outcome. This is not protection against blocking, deadlocks,
 * infinite loops or arbitrary JVM failures. Validation, scope setup and explicit context
 * calls within the action are outside this completion isolation boundary.
 * Logger names and operation names must not be blank.
 */
public final class LogScopeExecutor {

    private static final System.Logger LOGGER = System.getLogger(LogScopeExecutor.class.getName());

    private final ScopedLogContext context;
    private final LogEventPublisher eventPublisher;

    public LogScopeExecutor(ScopedLogContext context, LogEventPublisher eventPublisher) {
        this.context = requireNonNull(context, Errors.CONTEXT_REQUIRED);
        this.eventPublisher = requireNonNull(eventPublisher, Errors.PUBLISHER_REQUIRED);
    }

    public <T, X extends Throwable> T execute(String loggerName, String operation, ScopedValue.CallableOp<T, X> action) throws X {
        return execute(loggerName, operation, LogLevel.INFO, action);
    }

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

    public <X extends Throwable> void run(String loggerName, String operation, LogAction<X> action) throws X {
        run(loggerName, operation, LogLevel.INFO, action);
    }

    public <X extends Throwable> void run(String loggerName, String operation, LogLevel level, LogAction<X> action) throws X {
        requireNonNull(action, Errors.RUN_ACTION_REQUIRED);
        execute(loggerName, operation, level, (ScopedValue.CallableOp<Void, X>) () -> {
            action.run();
            return null;
        });
    }

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

        private Errors() { }
    }
}
