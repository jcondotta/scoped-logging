package com.jcondotta.scopedlogging.application.logscope;

import com.jcondotta.scopedlogging.domain.logging.LogKey;
import com.jcondotta.scopedlogging.domain.logging.LogLevel;
import com.jcondotta.scopedlogging.infrastructure.slf4j.Slf4jLogEventPublisher;

import org.junit.jupiter.api.Test;

import static com.jcondotta.scopedlogging.application.logscope.ScopeEventCapture.attributes;
import static org.assertj.core.api.Assertions.assertThat;

class ManualServicesTest {

    private static final String OPERATION = "posts.publish";
    private static final String POST_ID_KEY = "post.id";
    private static final String POST_ID = "post-123";
    private static final String STORAGE_SAVED_KEY = "storage.saved";
    private static final String COMPLETION_MESSAGE = "Post published";
    private static final String TRUE_ATTRIBUTE_VALUE = "true";
    private static final String SUCCESS_OUTCOME = "success";

    @Test
    void shouldEnrichOneCompletionEvent_whenManuallyConstructedServicesShareContext() {
        var context = new ScopedLogContext();
        var executor = new LogScopeExecutor(context, new Slf4jLogEventPublisher());
        var storage = new PostStorage(context);
        var publisher = new PostPublisher(context, storage);

        try (var capture = new ScopeEventCapture()) {
            executor.run(capture.logger.getName(), OPERATION, () -> publisher.publish(POST_ID));

            assertThat(capture.events()).hasSize(1);
            assertThat(attributes(capture.events().getFirst()))
                .containsEntry(LogKey.OPERATION, OPERATION)
                .containsEntry(POST_ID_KEY, POST_ID)
                .containsEntry(STORAGE_SAVED_KEY, TRUE_ATTRIBUTE_VALUE)
                .containsEntry(LogKey.OUTCOME, SUCCESS_OUTCOME);
            assertThat(capture.events().getFirst().getFormattedMessage()).isEqualTo(COMPLETION_MESSAGE);
        }
    }

    private record PostStorage(LogContext context) {
        void save(String id) {
            context.with(POST_ID_KEY, id).with(STORAGE_SAVED_KEY, true);
        }
    }

    private record PostPublisher(LogContext context, PostStorage storage) {
        void publish(String id) {
            storage.save(id);
            context.info(COMPLETION_MESSAGE).success();
        }
    }
}
