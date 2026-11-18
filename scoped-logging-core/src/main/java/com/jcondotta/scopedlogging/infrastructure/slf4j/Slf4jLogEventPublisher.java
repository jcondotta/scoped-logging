package com.jcondotta.scopedlogging.infrastructure.slf4j;

import com.jcondotta.scopedlogging.application.port.output.LogEventPublisher;
import com.jcondotta.scopedlogging.domain.logging.LogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

/**
 * Publishes completed log events through the SLF4J 2 API.
 *
 * <p>Resolves the logger by the event's logger name and forwards its severity, message,
 * cause and structured attributes. The consuming application's provider controls filtering,
 * delivery and rendering; this adapter does not impose a JSON format or sanitize values.
 * No mutable per-event state is retained by the adapter, so independent scopes can share it.
 *
 * <p>Failures propagate to the caller;
 * {@link com.jcondotta.scopedlogging.application.logscope.LogScopeExecutor}
 * provides completion failure isolation
 * when this adapter is used through the executor. No retries are performed here.
 */
public final class Slf4jLogEventPublisher implements LogEventPublisher {

    /**
     * Creates an adapter using SLF4J's logger factory and the consuming application's provider.
     * Destination loggers are resolved when events are published.
     */
    public Slf4jLogEventPublisher() {
    }

    /**
     * Forwards one event through an SLF4J fluent logging builder without retrying.
     * Attributes are supplied as key/value pairs and a non-null cause is attached separately.
     * A disabled level or missing provider may result in no output.
     *
     * @param event completed event to forward
     * @throws NullPointerException if {@code event} is null
     */
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
