package com.jcondotta.scopedlogging.application.logscope;

/**
 * Enriches one completion event in the current log scope.
 * Adds business attributes and declarations to the single completion event for the
 * current scope. The latest message/severity declaration replaces the previous one,
 * including its throwable; an exception escaping the scope takes precedence and is
 * recorded at ERROR with the original throwable. Outcome is independent of severity: normal
 * return defaults to success, while {@link #success()} and {@link #failure()} explicitly
 * declare it. An ERROR declaration followed by normal return therefore has
 * {@code outcome=success} unless {@link #failure()} is called. The keys {@code operation},
 * {@code outcome}, and {@code duration_ms} are reserved and rejected by
 * {@link #with(String, Object)}. Use {@code reason} for a business failure classification.
 * Explicit reasons, including null, are preserved. An escaping exception adds
 * {@code reason=internal_error} only when the reason key is absent and the current
 * outcome has not been explicitly declared failure. Normal completion adds no reason.
 * Declarations enrich the event; publication and delivery are governed by the executor
 * and publisher contracts.
 * Prefer simple, stable attribute values; do not add secrets or sensitive data unless your
 * application's logging policy permits them.
 */
public interface LogContext {

    /**
     * Adds or replaces a business attribute. Prefer simple, stable values such as strings,
     * numbers, booleans and identifiers. Avoid secrets and sensitive data unless permitted
     * by the application's logging policy. Reserved structural keys throw
     * {@link IllegalArgumentException}. Keys must not be null; values may be null.
     * A null value remains an explicitly supplied attribute, not an absent key.
     * The completed event copies the map shallowly, retaining values by reference.
     */
    LogContext with(String key, Object value);

    LogContext debug(String message);

    LogContext info(String message);

    LogContext warn(String message);

    LogContext error(String message, Throwable error);

    /** Sets the current outcome to success without ending the scope or changing severity. */
    void success();

    /** Sets the current outcome to failure without ending the scope or changing severity. */
    void failure();
}
