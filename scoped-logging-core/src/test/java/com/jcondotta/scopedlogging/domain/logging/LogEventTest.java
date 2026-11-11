package com.jcondotta.scopedlogging.domain.logging;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LogEventTest {

    private static final String LOGGER_NAME = "example";
    private static final String MESSAGE = "Operation completed";
    
    private static final String ATTRIBUTE_KEY = "post.id";
    private static final String ATTRIBUTE_VALUE = "post-123";
    private static final String LATER_ATTRIBUTE_KEY = "later";
    private static final IllegalStateException CAUSE = new IllegalStateException("manual review required");

    private static final LogEventDeclaration DECLARATION = new LogEventDeclaration(LogLevel.INFO, MESSAGE, null);
    private static final LogEventDeclaration DECLARATION_WITH_CAUSE =
        new LogEventDeclaration(LogLevel.ERROR, MESSAGE, CAUSE);

    @Test
    void shouldThrowNullPointerException_whenLoggerNameIsNull() {
        assertThatNullPointerException()
            .isThrownBy(() -> new LogEvent(null, Map.of(), DECLARATION))
            .withMessage("logger name must not be null");
    }

    @Test
    void shouldThrowNullPointerException_whenAttributesAreNull() {
        assertThatNullPointerException()
            .isThrownBy(() -> new LogEvent(LOGGER_NAME, null, DECLARATION))
            .withMessage("log attributes must not be null");
    }

    @Test
    void shouldThrowNullPointerException_whenDeclarationIsNull() {
        assertThatNullPointerException()
            .isThrownBy(() -> new LogEvent(LOGGER_NAME, Map.of(), null))
            .withMessage("log event declaration must not be null");
    }

    @Test
    void shouldCreateEvent_whenDeclarationCauseIsNull() {
        var event = new LogEvent(LOGGER_NAME, Map.of(), DECLARATION);

        assertThat(event.declaration().cause()).isNull();
    }

    @Test
    void shouldPreserveCause_whenEventIsCreatedWithDeclarationCause() {
        var event = new LogEvent(LOGGER_NAME, Map.of(), DECLARATION_WITH_CAUSE);

        assertThat(event.declaration()).isSameAs(DECLARATION_WITH_CAUSE);
        assertThat(event.declaration().cause()).isSameAs(CAUSE);
    }

    @Test
    void shouldPreserveNullAttributeValue_whenAttributeValueIsNull() {
        var attributes = new HashMap<String, Object>();
        attributes.put(ATTRIBUTE_KEY, null);

        var event = new LogEvent(LOGGER_NAME, attributes, DECLARATION);

        assertThat(event.attributes()).containsEntry(ATTRIBUTE_KEY, null);
    }

    @Test
    void shouldCopyAttributeMap_whenEventIsCreated() {
        var attributes = new HashMap<String, Object>();
        attributes.put(ATTRIBUTE_KEY, ATTRIBUTE_VALUE);

        var event = new LogEvent(LOGGER_NAME, attributes, DECLARATION);
        attributes.put(LATER_ATTRIBUTE_KEY, true);

        assertThat(event.attributes()).containsEntry(ATTRIBUTE_KEY, ATTRIBUTE_VALUE)
            .doesNotContainKey(LATER_ATTRIBUTE_KEY);
    }

    @Test
    void shouldRetainAttributeValueReference_whenMapIsCopied() {
        var value = new ArrayList<String>();
        value.add(ATTRIBUTE_VALUE);
        var attributes = new HashMap<String, Object>();
        attributes.put(ATTRIBUTE_KEY, value);

        var event = new LogEvent(LOGGER_NAME, attributes, DECLARATION);
        attributes.clear();
        value.add(LATER_ATTRIBUTE_KEY);

        assertThat(event.attributes()).containsOnlyKeys(ATTRIBUTE_KEY);
        assertThat(event.attributes().get(ATTRIBUTE_KEY)).isSameAs(value);
        assertThat(value).containsExactly(ATTRIBUTE_VALUE, LATER_ATTRIBUTE_KEY);
    }

    @Test
    void shouldThrowUnsupportedOperationException_whenAttributesAreModified() {
        var event = new LogEvent(LOGGER_NAME, Map.of(ATTRIBUTE_KEY, ATTRIBUTE_VALUE), DECLARATION);

        assertThatThrownBy(() -> event.attributes().put(LATER_ATTRIBUTE_KEY, true))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldThrowNullPointerException_whenAttributeKeyIsNull() {
        var attributes = new HashMap<String, Object>();
        attributes.put(null, ATTRIBUTE_VALUE);

        assertThatNullPointerException()
            .isThrownBy(() -> new LogEvent(LOGGER_NAME, attributes, DECLARATION))
            .withMessage("log attribute key must not be null");
    }
}
