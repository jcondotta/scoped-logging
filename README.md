# Scoped Logging

**One scope. One structured event. Full context.**

A small Java 25+ library that collects structured attributes throughout a synchronous
operation and attempts to publish one completion event through SLF4J. Nested operations receive isolated
scopes; completion restores the parent scope automatically. Java 25 requires no preview flags.

| Artifact | Purpose | Production dependencies |
| --- | --- | --- |
| `scoped-logging-core` | Programmatic scopes in plain Java | SLF4J API only |
| `scoped-logging-spring-boot` | `@LogScope` and automatic configuration | Core, Spring Boot, Spring AOP and AspectJ |

Both artifacts use `io.github.jcondotta` and version `0.1.0-SNAPSHOT`. The root
`scoped-logging` artifact is a parent POM, not a library JAR. Packages remain
`com.jcondotta.scopedlogging` to preserve the extracted API.

## Install locally

Requires JDK 25+ and Maven 3.9+. From the repository root:

```sh
mvn clean verify
mvn clean install
```

This installs the parent POM and both library JARs into your local Maven
repository. These artifacts are **not published to Maven Central**.

## Plain Java

```xml
<dependency>
    <groupId>io.github.jcondotta</groupId>
    <artifactId>scoped-logging-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Choose an SLF4J 2 provider in your application and configure it to render structured
key/value pairs. The library does not supply a provider or configure your logging output.

```java
import com.jcondotta.scopedlogging.application.logscope.LogContext;
import com.jcondotta.scopedlogging.application.logscope.LogScopeExecutor;
import com.jcondotta.scopedlogging.application.logscope.ScopedLogContext;
import com.jcondotta.scopedlogging.infrastructure.slf4j.Slf4jLogEventPublisher;
import org.slf4j.LoggerFactory;

class PublishExample {
    static final class PostStorage {
        private final LogContext context;

        PostStorage(LogContext context) {
            this.context = context;
        }

        void save(String id) {
            context.with("post.id", id).with("storage.saved", true);
        }
    }

    static final class PostService {
        private final LogContext context;
        private final PostStorage storage;

        PostService(LogContext context, PostStorage storage) {
            this.context = context;
            this.storage = storage;
        }

        void publish(String id) {
            storage.save(id);
            context.info("Post published").success();
        }
    }

    public static void main(String[] args) {
        var context = new ScopedLogContext();
        var scopes = new LogScopeExecutor(context, new Slf4jLogEventPublisher());
        var storage = new PostStorage(context);
        var service = new PostService(context, storage);
        var logger = LoggerFactory.getLogger(PublishExample.class);

        scopes.run(logger.getName(), "posts.publish", () -> service.publish("post-123"));
        String result = scopes.execute(logger.getName(), "posts.read", () -> "post-123");
    }
}
```

Share the same context instance with the executor and your manually constructed services.
The application is composed with an output port; `Slf4jLogEventPublisher` is the standard
adapter. Use `run` for void operations and `execute` for returned values. Overloads accept
a `LogLevel` default severity. No explicit close or `.log()` call is needed.

Normal return defaults to success and INFO severity. Outcome and severity are independent;
`success()` and `failure()` set the outcome, while event declarations set severity, message
and cause. Escaping exceptions determine failure outcome, ERROR severity and the original
cause. Supplied `reason` values, including null, are preserved; automatic fallback applies
only when the key is absent and failure was not explicitly declared.

See the [core README](scoped-logging-core/README.md) for event defaults, reason semantics,
attribute snapshots, concurrency, and the precise failure-isolation contract.

## Spring Boot

Add the integration artifact; Maven includes the core transitively:

```xml
<dependency>
    <groupId>io.github.jcondotta</groupId>
    <artifactId>scoped-logging-spring-boot</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```java
import com.jcondotta.scopedlogging.LogScope;
import com.jcondotta.scopedlogging.application.logscope.LogContext;
import org.springframework.stereotype.Service;

@Service
public class PostService {
    private final LogContext context;

    public PostService(LogContext context) {
        this.context = context;
    }

    @LogScope("posts.publish")
    public void publish(String id) {
        context.with("post.id", id).info("Post published").success();
    }
}
```

Boot discovers `ScopedLoggingAutoConfiguration` without scanning library packages. It supplies
`ScopedLogContext`, `LogScopeExecutor` with the SLF4J publisher, and `LogScopeAspect`, backing
off when the corresponding bean type is supplied by the application. To use another publisher,
provide a `LogScopeExecutor` bean constructed with the same context used by your services.

The aspect uses the target class's logger name. An empty annotation value derives the
operation as `ClassName.methodName`; `@LogScope(level = LogLevel.DEBUG)` selects a default
severity (`LogLevel` is in `com.jcondotta.scopedlogging.domain.logging`). All completion,
reason and failure-isolation behavior is delegated to the core executor.

Calls must pass through a Spring proxy: self-invocation, manually created objects, private
methods and non-interceptable final methods do not activate scopes. Future/reactive return
values do not extend the synchronous scope. This integration is tested with Boot 4.1.1.

## Execution and failure isolation

Scopes are synchronous: they end when the action returns, even if it returns a future or
reactive publisher. Nested scopes isolate their attributes and restore their parent binding.
Independent scopes work on platform and virtual threads. There is no automatic propagation
to arbitrary asynchronous execution. Concurrent mutation of mutable scoped state inherited
by structured child tasks is unsupported and has no isolation guarantee.

Business execution takes precedence over `RuntimeException` and `AssertionError` during
automatic completion/publication: the original result or exception is preserved. A single
best-effort `System.Logger` diagnostic is protected against the same failures. Other errors,
including `VirtualMachineError`, `ThreadDeath` and `LinkageError`, can propagate and override
the business outcome. There are no automatic business or publication retries.

One publication attempt per completed event does not guarantee delivery or exactly-once
behavior. Filtering, a missing provider or partial publication can affect delivery. Isolation
does not cover validation, scope setup, explicit context calls inside the action, deadlocks,
blocking or arbitrary JVM failures. See [core limitations](scoped-logging-core/README.md#failure-isolation).

Choose stable attribute values and configure redaction and output formatting in the consuming
application. The library does not sanitize values or impose JSON output. The event's attribute
map is an unmodifiable shallow copy; mutable values remain shared by reference.

The API is pre-release. Use the classpath; JPMS module-path integration is not supported.

See [core verification](scoped-logging-core/README.md#verification) for core and opt-in stress
commands, [VALIDATION.md](VALIDATION.md) for historical extraction evidence, and
[MIGRATION.md](MIGRATION.md) for the proposed banking-system migration.

## License and publication

The license is pending owner approval; `LICENSE` is a status placeholder, not a license grant.
The source repository is [jcondotta/scoped-logging](https://github.com/jcondotta/scoped-logging).
Before publishing to Maven Central, approve the license, configure project/SCM metadata,
attach sources and Javadocs, and configure signing and Central publication.
