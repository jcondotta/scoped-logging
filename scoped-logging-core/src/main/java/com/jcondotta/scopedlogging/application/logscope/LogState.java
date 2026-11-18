package com.jcondotta.scopedlogging.application.logscope;

import com.jcondotta.scopedlogging.domain.logging.LogEvent;
import com.jcondotta.scopedlogging.domain.logging.LogEventDeclaration;
import com.jcondotta.scopedlogging.domain.logging.LogKey;
import com.jcondotta.scopedlogging.domain.logging.LogLevel;
import com.jcondotta.scopedlogging.domain.logging.LogOutcome;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

/**
 * Mutable attributes, outcome and event declaration for one synchronous scope.
 * Owned by one binding in {@link ScopedLogContext}; concurrent mutation of the same
 * state is unsupported. Completion derives a snapshot without publishing it.
 */
final class LogState {

    private static final String PROPAGATED_EXCEPTION_MESSAGE = "Operation failed with an exception";

    private final String loggerName;
    private final Map<String, Object> attributes;
    private final long startedAtNanos = System.nanoTime();
    private final LogLevel defaultSeverity;

    private LogEventDeclaration declaration;
    private LogOutcome outcome;

    /**
     * Initializes the state with the operation attribute and starts duration measurement.
     * Arguments are validated by {@link LogScopeExecutor} before creating the state.
     *
     * @param loggerName destination logger name
     * @param operation operation name stored under the reserved operation key
     * @param defaultSeverity severity for normal completion without an explicit declaration or failure
     */
    LogState(String loggerName, String operation, LogLevel defaultSeverity) {
        this.loggerName = loggerName;
        this.attributes = new LinkedHashMap<>(Map.of(LogKey.OPERATION, operation));
        this.defaultSeverity = defaultSeverity;
    }

    /**
     * Adds or replaces a business attribute, retaining null values as explicit mappings.
     *
     * @param key non-null, non-reserved attribute key
     * @param value attribute value, retained by reference and allowed to be null
     * @throws NullPointerException if the key is null
     * @throws IllegalArgumentException if the key is reserved
     */
    void addAttribute(String key, Object value) {
        requireNonNull(key, Errors.ATTRIBUTE_KEY_REQUIRED);
        if (LogKey.isReserved(key)) {
            throw new IllegalArgumentException(Errors.reservedAttribute(key));
        }
        attributes.put(key, value);
    }

    /**
     * Replaces the explicit outcome without changing the event declaration.
     *
     * @param outcome explicit outcome, or null to restore the implicit success default
     */
    void setOutcome(LogOutcome outcome) {
        this.outcome = outcome;
    }

    /**
     * Replaces severity, message and cause together while preserving the outcome.
     *
     * @param severity non-null event severity
     * @param message non-null event message, which may be blank
     * @param error optional cause, replacing any previous cause
     * @throws NullPointerException if severity or message is null
     */
    void declareEvent(LogLevel severity, String message, Throwable error) {
        this.declaration = new LogEventDeclaration(severity, message, error);
    }

    /**
     * Resolves completion attributes and the final event declaration.
     * An escaping throwable takes precedence over outcome, severity and cause, but retains
     * an explicitly declared message. Its fallback reason is added only when no reason
     * key exists and the current outcome is not explicitly failure.
     *
     * <p>Normal return defaults to success. A failure outcome without a declaration uses
     * WARN and {@code Operation failed}; otherwise an undeclared event uses the configured
     * severity and {@code Operation completed}. Duration is measured through completion,
     * truncated to whole milliseconds, and excludes publication time.
     *
     * @param propagatedError escaping throwable, or null for normal return
     * @return completed event with an unmodifiable shallow copy of its attributes
     */
    LogEvent complete(Throwable propagatedError) {
        var completedAttributes = new LinkedHashMap<>(attributes);
        var failureDeclared = outcome == LogOutcome.FAILURE;
        LogLevel completedSeverity;
        String completedMessage;
        Throwable completedError;

        if (propagatedError != null) {
            completedAttributes.put(LogKey.OUTCOME, LogOutcome.FAILURE.value());
            if (!failureDeclared && !completedAttributes.containsKey(LogKey.REASON)) {
                completedAttributes.put(LogKey.REASON, "internal_error");
            }
            completedSeverity = LogLevel.ERROR;
            completedMessage = declaration != null ? declaration.message() : PROPAGATED_EXCEPTION_MESSAGE;
            completedError = propagatedError;
        }
        else {
            var completedOutcome = outcome != null ? outcome : LogOutcome.SUCCESS;
            completedAttributes.put(LogKey.OUTCOME, completedOutcome.value());
            completedMessage = declaration != null
                ? declaration.message()
                : failureDeclared ? "Operation failed" : "Operation completed";
            completedSeverity = declaration != null
                ? declaration.severity()
                : failureDeclared ? LogLevel.WARN : defaultSeverity;
            completedError = declaration != null ? declaration.cause() : null;
        }

        completedAttributes.put(LogKey.DURATION_MS,
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos));
        var completedDeclaration = new LogEventDeclaration(completedSeverity, completedMessage, completedError);
        return new LogEvent(loggerName, completedAttributes, completedDeclaration);
    }

    /** Validation messages for business attribute keys. */
    private static final class Errors {
        private static final String ATTRIBUTE_KEY_REQUIRED = "log attribute key must not be null";
        /**
         * Identifies the reserved attribute rejected during enrichment.
         *
         * @param key rejected structural key
         * @return validation message naming the key
         */
        private static String reservedAttribute(String key) {
            return "attribute '" + key + "' is reserved and managed by scoped logging";
        }
    }
}
