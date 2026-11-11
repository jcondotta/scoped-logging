package com.jcondotta.scopedlogging;

import com.jcondotta.scopedlogging.domain.logging.LogLevel;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Starts a logging scope around a method invocation. When no operation is supplied,
 * the operation is derived from the target class and method name. The configured level
 * is the default severity for a normally completed operation; a severity declared through
 * {@code LogContext} takes precedence. Classified failures default to WARN, while any
 * propagated exception determines ERROR severity. Completion and publication use the core
 * executor's failure-isolation policy; event delivery is not guaranteed.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogScope {

    String value() default "";

    LogLevel level() default LogLevel.INFO;
}
