package com.jcondotta.scopedlogging;

import com.jcondotta.scopedlogging.domain.logging.LogLevel;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method for a synchronous logging scope when invoked through a Spring AOP proxy.
 *
 * <p>When no operation is supplied,
 * the operation is derived from the target class and method name. The configured level
 * is the default severity for a normally completed operation; a severity declared through
 * {@code LogContext} takes precedence. Classified failures default to WARN, while any
 * propagated exception determines ERROR severity. Completion and publication use the core
 * executor's failure-isolation policy; event delivery is not guaranteed.
 *
 * <p>Self-invocation does not pass through the proxy. Private methods cannot be intercepted,
 * and class-based proxies cannot advise final methods. The scope ends when the invocation
 * returns or throws; completion of a returned future or reactive publisher is not awaited.
 *
 * <pre>{@code
 * @LogScope("orders.create")
 * public String createOrder() {
 *     logContext.with("order.id", "123").info("Order created");
 *     return "123";
 * }
 * }</pre>
 *
 * @see LogScopeAspect
 * @see ScopedLoggingAutoConfiguration
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogScope {

    /**
     * Specifies the operation name recorded in the completed event.
     * A blank value uses the target class's simple name followed by a dot and the method name.
     * Non-blank values are used as supplied, without trimming.
     *
     * @return explicit operation name, or a blank value to derive it from the invocation
     */
    String value() default "";

    /**
     * Specifies the default severity for normal completion without an event declaration
     * or an explicit failure outcome. Explicit failure without a declaration uses WARN;
     * an escaping throwable uses ERROR.
     *
     * @return default scope severity, INFO unless configured otherwise
     */
    LogLevel level() default LogLevel.INFO;
}
