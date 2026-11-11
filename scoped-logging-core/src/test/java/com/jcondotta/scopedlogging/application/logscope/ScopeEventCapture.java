package com.jcondotta.scopedlogging.application.logscope;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.stream.Collectors.toMap;

final class ScopeEventCapture extends AppenderBase<ILoggingEvent> implements AutoCloseable {

    private static final AtomicInteger IDS = new AtomicInteger();
    private final ConcurrentLinkedQueue<ILoggingEvent> events = new ConcurrentLinkedQueue<>();

    final Logger logger;

    ScopeEventCapture() {
        logger = (Logger) LoggerFactory.getLogger("scope-test-" + IDS.incrementAndGet());
        logger.setLevel(Level.DEBUG);
        logger.setAdditive(false);
        start();
        logger.addAppender(this);
    }

    @Override
    protected void append(ILoggingEvent event) {
        event.prepareForDeferredProcessing();
        events.add(event);
    }

    List<ILoggingEvent> events() {
        return List.copyOf(events);
    }

    static Map<String, String> attributes(ILoggingEvent event) {
        // Duplicate keys deliberately fail instead of being silently merged.
        return event.getKeyValuePairs().stream()
            .collect(toMap(pair -> pair.key, pair -> String.valueOf(pair.value)));
    }

    @Override
    public void close() {
        logger.detachAppender(this);
        stop();
        logger.setLevel(null);
        logger.setAdditive(true);
    }
}
