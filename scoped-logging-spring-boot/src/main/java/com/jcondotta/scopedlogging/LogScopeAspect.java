package com.jcondotta.scopedlogging;

import com.jcondotta.scopedlogging.application.logscope.LogScopeExecutor;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;

/**
 * Adapts Spring AOP invocations of {@link LogScope} methods to the core scope executor.
 * Resolves the destination logger from the target class and delegates scope binding,
 * completion and publication to {@link LogScopeExecutor}.
 *
 * <p>Only calls passing through a Spring proxy are intercepted. The scope covers the
 * synchronous invocation, including nested calls through other advised proxies; it does
 * not cover deferred execution represented by a returned future or reactive publisher.
 */
@Aspect
public class LogScopeAspect {

    private final LogScopeExecutor executor;

    /**
     * Creates an aspect backed by the application's scope executor.
     *
     * @param executor executor used for intercepted invocations; must be non-null
     */
    public LogScopeAspect(LogScopeExecutor executor) {
        this.executor = executor;
    }

    /**
     * Executes the intercepted method once inside a logging scope.
     * Uses the explicit operation name, or {@code SimpleClassName.methodName} when it is
     * blank. Completion uses the executor's failure-isolation policy before returning
     * the method's original result or propagating its throwable.
     *
     * @param joinPoint method invocation supplied by Spring AOP
     * @param scope annotation supplying the operation name and default severity
     * @return original method result, including null for a void method or null return value
     * @throws Throwable if the intercepted method or non-isolated scope infrastructure fails
     */
    @Around(value = "@annotation(scope)", argNames = "joinPoint,scope")
    public Object logScope(ProceedingJoinPoint joinPoint, LogScope scope) throws Throwable {
        var targetClass = AopUtils.getTargetClass(joinPoint.getTarget());
        var logger = LoggerFactory.getLogger(targetClass);
        var operation = scope.value().isBlank()
            ? targetClass.getSimpleName() + "." + joinPoint.getSignature().getName()
            : scope.value();

        return executor.execute(logger.getName(), operation, scope.level(),
            (ScopedValue.CallableOp<Object, Throwable>) joinPoint::proceed);
    }
}
