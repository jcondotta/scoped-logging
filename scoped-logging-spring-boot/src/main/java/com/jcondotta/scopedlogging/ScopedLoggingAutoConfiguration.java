package com.jcondotta.scopedlogging;

import com.jcondotta.scopedlogging.application.logscope.LogScopeExecutor;
import com.jcondotta.scopedlogging.application.logscope.ScopedLogContext;
import com.jcondotta.scopedlogging.infrastructure.slf4j.Slf4jLogEventPublisher;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/** Registers scoped logging independently of the consumer's component scan. */
@AutoConfiguration
@EnableAspectJAutoProxy
public class ScopedLoggingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ScopedLogContext scopedLogContext() {
        return new ScopedLogContext();
    }

    @Bean
    @ConditionalOnMissingBean
    LogScopeExecutor logScopeExecutor(ScopedLogContext context) {
        return new LogScopeExecutor(context, new Slf4jLogEventPublisher());
    }

    @Bean
    @ConditionalOnMissingBean
    LogScopeAspect logScopeAspect(LogScopeExecutor executor) {
        return new LogScopeAspect(executor);
    }
}
