package com.jcondotta.scopedlogging.domain.logging;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Description of one completed logging scope with an unmodifiable attribute map.
 * The logger name, attributes map and declaration are required. Attribute values may be
 * null; attribute keys may not. The attributes map is copied, but its values are retained
 * by reference; this is a shallow snapshot, not deep immutability. A null mapping remains
 * distinct from an absent key. Mutable values should remain stable while the event is
 * being published; asynchronous publishers needing a stable snapshot must materialize them.
 */
public record LogEvent(String loggerName, Map<String, Object> attributes, LogEventDeclaration declaration) {

    public LogEvent {
        requireNonNull(loggerName, Errors.LOGGER_NAME_REQUIRED);
        var copiedAttributes = new LinkedHashMap<>(requireNonNull(attributes, Errors.ATTRIBUTES_REQUIRED));
        if (copiedAttributes.containsKey(null)) {
            throw new NullPointerException(Errors.ATTRIBUTE_KEY_REQUIRED);
        }
        attributes = Collections.unmodifiableMap(copiedAttributes);
        requireNonNull(declaration, Errors.DECLARATION_REQUIRED);
    }

    private static final class Errors {
        private static final String LOGGER_NAME_REQUIRED = "logger name must not be null";
        private static final String ATTRIBUTES_REQUIRED = "log attributes must not be null";
        private static final String ATTRIBUTE_KEY_REQUIRED = "log attribute key must not be null";
        private static final String DECLARATION_REQUIRED = "log event declaration must not be null";

        private Errors() { }
    }
}
