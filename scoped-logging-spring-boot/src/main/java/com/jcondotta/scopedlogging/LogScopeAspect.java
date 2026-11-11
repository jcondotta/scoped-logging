package com.jcondotta.scopedlogging;

import com.jcondotta.scopedlogging.application.logscope.LogScopeExecutor;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;

@Aspect
public class LogScopeAspect {

    private final LogScopeExecutor executor;

    public LogScopeAspect(LogScopeExecutor executor) {
        this.executor = executor;
    }

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
