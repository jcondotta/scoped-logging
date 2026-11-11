package com.jcondotta.scopedlogging.infrastructure.slf4j;

import com.jcondotta.scopedlogging.application.port.output.LogEventPublisher;
import com.jcondotta.scopedlogging.domain.logging.LogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

/**
 * Publishes completed log events through the SLF4J 2 API.
 * Failures propagate to the caller; LogScopeExecutor provides completion failure isolation
 * when this adapter is used through the executor. No retries are performed here.
 */
public final class Slf4jLogEventPublisher implements LogEventPublisher {

    @Override
    public void publish(LogEvent event) {
        var logger = LoggerFactory.getLogger(event.loggerName());
        var declaration = event.declaration();
        LoggingEventBuilder builder = switch (declaration.severity()) {
            case DEBUG -> logger.atDebug();
            case INFO -> logger.atInfo();
            case WARN -> logger.atWarn();
            case ERROR -> logger.atError();
        };

        builder.setMessage(declaration.message());
        if (declaration.cause() != null) {
            builder.setCause(declaration.cause());
        }
        event.attributes().forEach(builder::addKeyValue);
        builder.log();
    }
}
