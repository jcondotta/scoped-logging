package com.jcondotta.scopedlogging.application.logscope;

/**
 * Enriches one completion event in the current log scope.
 *
 * <p>Adds business attributes and declarations to the single completion event for the
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
 *
 * <p>All methods require an active scope on this context. They enrich its state without
 * publishing immediately. Prefer simple, stable attribute values; do not add secrets or sensitive data unless your
 * application's logging policy permits them.
 *
 * @see LogScopeExecutor
 */
public interface LogContext {

    /**
     * Adds or replaces a business attribute. Prefer simple, stable values such as strings,
     * numbers, booleans and identifiers. Avoid secrets and sensitive data unless permitted
     * by the application's logging policy. Reserved structural keys throw
     * {@link IllegalArgumentException}. Keys must not be null; values may be null.
     * A null value remains an explicitly supplied attribute, not an absent key.
     * The completed event copies the map shallowly, retaining values by reference.
     *
     * @param key business attribute key; non-null, but may be blank
     * @param value attribute value, which may be null
     * @return this context for further enrichment
     * @throws IllegalStateException if no scope is active on this context
     * @throws NullPointerException if {@code key} is null
     * @throws IllegalArgumentException if {@code key} is a reserved structural key
     */
    LogContext with(String key, Object value);

    /**
     * Declares DEBUG severity and a message, clearing any previously declared cause.
     * The outcome is unchanged; this replaces the previous event declaration.
     *
     * @param message completion message; non-null, but may be blank
     * @return this context for further enrichment
     * @throws IllegalStateException if no scope is active on this context
     * @throws NullPointerException if {@code message} is null
     */
    LogContext debug(String message);

    /**
     * Declares INFO severity and a message, clearing any previously declared cause.
     * The outcome is unchanged; this replaces the previous event declaration.
     *
     * @param message completion message; non-null, but may be blank
     * @return this context for further enrichment
     * @throws IllegalStateException if no scope is active on this context
     * @throws NullPointerException if {@code message} is null
     */
    LogContext info(String message);

    /**
     * Declares WARN severity and a message, clearing any previously declared cause.
     * This does not mark the outcome as failure.
     *
     * @param message completion message; non-null, but may be blank
     * @return this context for further enrichment
     * @throws IllegalStateException if no scope is active on this context
     * @throws NullPointerException if {@code message} is null
     */
    LogContext warn(String message);

    /**
     * Replaces the event declaration with ERROR severity, a message and an optional cause.
     * This does not mark the outcome as failure. An exception escaping the action
     * takes precedence over this cause at completion, while retaining the message.
     *
     * @param message completion message; non-null, but may be blank
     * @param error cause to attach, or null to clear a previous cause
     * @return this context for further enrichment
     * @throws IllegalStateException if no scope is active on this context
     * @throws NullPointerException if {@code message} is null
     */
    LogContext error(String message, Throwable error);

    /**
     * Replaces the current outcome with success without ending the scope or changing
     * an explicit event declaration. An escaping exception still produces failure.
     *
     * @throws IllegalStateException if no scope is active on this context
     */
    void success();

    /**
     * Replaces the current outcome with failure without ending the scope or changing
     * an explicit event declaration. On normal return without an event declaration,
     * completion uses WARN severity and the message {@code Operation failed}.
     * A current failure outcome suppresses the automatic {@code internal_error} reason;
     * a business reason can be supplied through {@link #with(String, Object)}.
     *
     * @throws IllegalStateException if no scope is active on this context
     */
    void failure();
}
