package com.jcondotta.scopedlogging.domain.logging;

import static java.util.Objects.requireNonNull;

/**
 * Severity, message and optional cause of a scope's event, replaced as a single value.
 * Used both for explicit declarations during a scope and for the resolved completion event.
 * Severity does not determine the business outcome. The cause is retained by reference.
 *
 * @param severity non-null event severity
 * @param message non-null event message; blank messages are accepted
 * @param cause optional throwable associated with the event
 */
public record LogEventDeclaration(LogLevel severity, String message, Throwable cause) {
    /**
     * Creates a declaration with the supplied severity, message and optional cause.
     *
     * @param severity non-null event severity
     * @param message non-null event message, which may be blank
     * @param cause optional throwable, or null for no cause
     * @throws NullPointerException if severity or message is null
     */
    public LogEventDeclaration {
        requireNonNull(severity, Errors.SEVERITY_REQUIRED);
        requireNonNull(message, Errors.MESSAGE_REQUIRED);
    }

    /** Validation messages for declaration construction. */
    private static final class Errors {
        private static final String SEVERITY_REQUIRED = "log severity must not be null";
        private static final String MESSAGE_REQUIRED = "log message must not be null";

        /** Prevents instantiation of the message holder. */
        private Errors() { }
    }
}
