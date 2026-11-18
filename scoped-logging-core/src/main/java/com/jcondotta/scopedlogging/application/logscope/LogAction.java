package com.jcondotta.scopedlogging.application.logscope;

/**
 * Synchronous operation without a return value, executed inside a logging scope.
 *
 * @param <X> type of throwable that the operation may propagate
 * @see LogScopeExecutor#run(String, String, LogAction)
 */
@FunctionalInterface
public interface LogAction<X extends Throwable> {
    /**
     * Performs the operation using the context bound by the executor.
     *
     * @throws X if the operation fails
     */
    void run() throws X;
}
