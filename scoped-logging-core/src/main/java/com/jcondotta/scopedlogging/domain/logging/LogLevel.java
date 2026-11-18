package com.jcondotta.scopedlogging.domain.logging;

/**
 * Severity of a structured log event, also used as the default for a logging scope.
 * Severity is independent of {@link LogOutcome}; an ERROR event can describe a scope
 * that returned normally with a success outcome.
 */
public enum LogLevel {
    /** Diagnostic detail, emitted when the logging backend enables DEBUG. */
    DEBUG,
    /** Informational severity and the default for a normally completed scope. */
    INFO,
    /** Warning severity and the fallback for an explicitly failed scope without a declaration. */
    WARN,
    /** Error severity, used whenever a throwable escapes the scope's action. */
    ERROR
}
