package example.consumer;

import com.jcondotta.scopedlogging.application.logscope.LogContext;
import com.jcondotta.scopedlogging.LogScope;
import com.jcondotta.scopedlogging.application.logscope.LogScopeExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AutoConfigurationDiscoveryTest {

    @Test
    void shouldDiscoverLibraryAndRestoreParent_whenConsumerOnlyEnablesBootAutoConfiguration() {
        new ApplicationContextRunner()
            .withUserConfiguration(ConsumerConfiguration.class)
            .run(application -> {
                assertThat(application).hasSingleBean(LogContext.class).hasSingleBean(LogScopeExecutor.class);
                var outer = application.getBean(OuterService.class);
                var inner = application.getBean(InnerService.class);
                assertThat(AopUtils.isAopProxy(outer)).isTrue();
                assertThat(AopUtils.isAopProxy(inner)).isTrue();
                assertThat(outer.publish()).isEqualTo("published");
                assertThatThrownBy(() -> application.getBean(LogContext.class).success())
                    .isInstanceOf(IllegalStateException.class);
            });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class ConsumerConfiguration {
        @Bean
        InnerService innerService(LogContext context) {
            return new InnerService(context);
        }

        @Bean
        OuterService outerService(LogContext context, InnerService inner) {
            return new OuterService(context, inner);
        }
    }

    public static class InnerService {
        private final LogContext context;

        InnerService(LogContext context) {
            this.context = context;
        }

        @LogScope("posts.store")
        public void save() {
            context.with("storage.saved", true).success();
        }
    }

    public static class OuterService {
        private final LogContext context;
        private final InnerService inner;

        OuterService(LogContext context, InnerService inner) {
            this.context = context;
            this.inner = inner;
        }

        @LogScope("posts.publish")
        public String publish() {
            context.with("post.id", "post-123");
            inner.save();
            context.info("Post published").success();
            return "published";
        }
    }
}
