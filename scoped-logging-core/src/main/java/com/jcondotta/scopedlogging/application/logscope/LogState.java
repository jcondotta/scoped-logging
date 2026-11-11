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

final class LogState {

    private static final String PROPAGATED_EXCEPTION_MESSAGE = "Operation failed with an exception";

    private final String loggerName;
    private final Map<String, Object> attributes;
    private final long startedAtNanos = System.nanoTime();
    private final LogLevel defaultSeverity;

    private LogEventDeclaration declaration;
    private LogOutcome outcome;

    LogState(String loggerName, String operation, LogLevel defaultSeverity) {
        this.loggerName = loggerName;
        this.attributes = new LinkedHashMap<>(Map.of(LogKey.OPERATION, operation));
        this.defaultSeverity = defaultSeverity;
    }

    void addAttribute(String key, Object value) {
        requireNonNull(key, Errors.ATTRIBUTE_KEY_REQUIRED);
        if (LogKey.isReserved(key)) {
            throw new IllegalArgumentException(Errors.reservedAttribute(key));
        }
        attributes.put(key, value);
    }

    void setOutcome(LogOutcome outcome) {
        this.outcome = outcome;
    }

    void declareEvent(LogLevel severity, String message, Throwable error) {
        this.declaration = new LogEventDeclaration(severity, message, error);
    }

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

    private static final class Errors {
        private static final String ATTRIBUTE_KEY_REQUIRED = "log attribute key must not be null";
        private static String reservedAttribute(String key) {
            return "attribute '" + key + "' is reserved and managed by scoped logging";
        }
    }
}
