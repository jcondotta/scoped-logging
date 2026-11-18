package com.jcondotta.scopedlogging;

import com.jcondotta.scopedlogging.application.logscope.LogScopeExecutor;
import com.jcondotta.scopedlogging.application.logscope.ScopedLogContext;
import com.jcondotta.scopedlogging.infrastructure.slf4j.Slf4jLogEventPublisher;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Registers scoped logging independently of the consumer's component scan and enables
 * Spring AOP proxying for {@link LogScope} methods.
 *
 * <p>Provides a {@link ScopedLogContext}, a {@link LogScopeExecutor} using the SLF4J adapter,
 * and a {@link LogScopeAspect}, backing off independently when a bean of the corresponding
 * type already exists. To customize event publication, supply a custom executor using
 * the same context as the application's collaborators. A publisher bean alone does not
 * replace the adapter created by the default executor factory.
 */
@AutoConfiguration
@EnableAspectJAutoProxy
public class ScopedLoggingAutoConfiguration {

    /** Creates the configuration whose bean factories are invoked by Spring Boot. */
    public ScopedLoggingAutoConfiguration() {
    }

    /**
     * Creates the shared context when no {@link ScopedLogContext} bean exists.
     *
     * @return context whose active state is bound independently for each scope
     */
    @Bean
    @ConditionalOnMissingBean
    ScopedLogContext scopedLogContext() {
        return new ScopedLogContext();
    }

    /**
     * Creates the default executor when no {@link LogScopeExecutor} bean exists.
     * Uses a new SLF4J adapter; the consuming application supplies its logging provider.
     *
     * @param context shared context used by scoped operations and their collaborators
     * @return executor publishing completed events through SLF4J
     * @throws NullPointerException if the context is null
     */
    @Bean
    @ConditionalOnMissingBean
    LogScopeExecutor logScopeExecutor(ScopedLogContext context) {
        return new LogScopeExecutor(context, new Slf4jLogEventPublisher());
    }

    /**
     * Creates the method interceptor when no {@link LogScopeAspect} bean exists.
     *
     * @param executor executor used to open and complete intercepted scopes
     * @return aspect delegating annotated invocations to the executor
     */
    @Bean
    @ConditionalOnMissingBean
    LogScopeAspect logScopeAspect(LogScopeExecutor executor) {
        return new LogScopeAspect(executor);
    }
}
