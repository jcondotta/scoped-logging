package com.jcondotta.scopedlogging.application.logscope;

/** An operation without a return value that may throw a checked exception. */
@FunctionalInterface
public interface LogAction<X extends Throwable> {
    void run() throws X;
}
