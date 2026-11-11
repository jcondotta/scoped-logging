package com.jcondotta.scopedlogging.domain.logging;

import static java.util.Objects.requireNonNull;

/** One explicit severity, message and optional cause declared for a scope's completion event. */
public record LogEventDeclaration(LogLevel severity, String message, Throwable cause) {
    public LogEventDeclaration {
        requireNonNull(severity, Errors.SEVERITY_REQUIRED);
        requireNonNull(message, Errors.MESSAGE_REQUIRED);
    }

    private static final class Errors {
        private static final String SEVERITY_REQUIRED = "log severity must not be null";
        private static final String MESSAGE_REQUIRED = "log message must not be null";

        private Errors() { }
    }
}
