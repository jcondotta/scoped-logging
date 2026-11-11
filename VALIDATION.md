# Extraction validation

Validated on 2026-11-08 with Amazon Corretto 25.0.2 and Maven 3.9.9.

## Design and scope

No library behavior changes were required. The current source already supplied the
programmatic core executor and thin Spring adapter requested by the owner.

| Decision | Resolution | Source |
| --- | --- | --- |
| Artifacts and dependency direction | Independent parent POM, core JAR, Spring JAR depending on core | Explicit extraction request |
| Public API, package names, outcomes and exception semantics | Preserve existing implementation byte for byte | Explicit preserve-behavior/no-redesign request |
| Scope lifecycle | Existing core executor owns binding, completion and error handling | Existing implementation satisfies explicit core requirement |
| Providers | Only SLF4J API in core; replace integration starter with direct framework dependencies | Explicit provider-free packaging requirement |
| Concurrency and nested scope semantics | Preserve existing implementation and tests | Explicit preservation request |
| License and remote | License placeholder; no remote, push or publication | Explicit approval boundary |

The design gate had no open behavioral or architectural decisions. HTTP, persistence and
request/response contracts are not applicable. No new attribute validation, propagation,
configuration properties or public abstractions were introduced.

## Directory structure

```text
scoped-logging/
├── .git/
├── .gitignore
├── .editorconfig
├── LICENSE                 # approval-pending placeholder
├── README.md
├── TESTING.md
├── VALIDATION.md
├── MIGRATION.md
├── pom.xml                 # io.github.jcondotta:scoped-logging:pom:0.1.0-SNAPSHOT
├── scoped-logging-core/
│   ├── pom.xml
│   ├── src/main/java/com/jcondotta/scopedlogging/
│   ├── src/test/java/com/jcondotta/scopedlogging/
│   └── target/scoped-logging-core-0.1.0-SNAPSHOT.jar
└── scoped-logging-spring-boot/
    ├── pom.xml
    ├── src/main/java/com/jcondotta/scopedlogging/
    ├── src/main/resources/META-INF/spring/
    │   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
    ├── src/test/java/com/jcondotta/scopedlogging/
    ├── src/test/java/example/consumer/
    └── target/scoped-logging-spring-boot-0.1.0-SNAPSHOT.jar
```

Generated `target/` contents are ignored by Git.

## Extracted classes

All production classes retain package `com.jcondotta.scopedlogging`.

| Artifact | Public classes/interfaces | Internal classes |
| --- | --- | --- |
| Core | `LogAction`, `LogContext`, `LogKey`, `LogLevel`, `LogScopeExecutor`, `ScopedLogContext` | `LogOutcome`, `LogState` |
| Spring Boot | `LogScope`, `LogScopeAspect`, `ScopedLoggingAutoConfiguration` | None |

The legacy outbox `LogContext`, `LogEvent` and `LogOutcome` were excluded. That API uses
manual `.log()` emission and is not part of the scoped implementation. No banking-specific
production code or dependencies were copied. Existing banking-themed test data was retained.
All 20 original production, test and resource files were copied byte for byte.

## Builds and tests

| Command/check | Result |
| --- | --- |
| `mvn clean verify` | PASS: 109 tests passed, 1 optional stress test skipped |
| `mvn clean install` | PASS: same 109 tests passed, 1 skipped; parent and both JARs installed |
| `mvn -pl scoped-logging-core test -Dscoped.logging.stress=true` | PASS: 94 core tests, no failures/errors/skips |
| Separate plain Java Maven consumer | PASS: only core dependency declared; runtime classpath contains exactly core and slf4j-api |
| Separate Spring Maven consumer | PASS: only integration dependency declared; auto-configuration discovered from installed JAR, LogContext injection and AOP work |
| JAR content inspection | PASS: two regular JARs, no nested dependencies or Boot launcher; parent produces no JAR |
| Source preservation | PASS: existing library source/tests/resources unchanged; banking-system file inventory and contents match initial snapshot |

Core default suite: 93 passing tests and 1 disabled stress case. Spring suite: 16 passing
tests. Stress adds 6,400 scope events across platform and virtual threads. The deterministic
concurrency matrix has 48 parameterized invocations, each repeated twice.

Migrated coverage includes completion, return identity, null results, checked/runtime/error
propagation, failure classification, structured attributes, nested isolation and parent
restoration, interruption/cancellation, concurrent and virtual-thread scopes, filtered
logging and failing backend/diagnostic logging. Added tests cover manually constructed
services sharing LogContext and Boot discovery from a consumer outside the library package.
Existing Spring tests verify emitted events and nested operations across beans.

The independent consumer fixtures were compiled outside this reactor, with no parent POM
or project source references, from `/private/tmp/scoped-logging-consumers`. They deliberately
omit a provider and exercise lifecycle/packaging; test-scoped Logback verifies actual event
contents in the library suite. SLF4J's no-provider warning is expected for those consumers.

The first sandboxed build could not populate the local Maven cache. The approved rerun
completed successfully; no unresolved build failures remain.

## Runtime dependency trees

Regenerate after installation with:

```sh
mvn dependency:tree -Dscope=runtime
```

Core:

```text
io.github.jcondotta:scoped-logging-core:jar:0.1.0-SNAPSHOT
\- org.slf4j:slf4j-api:jar:2.0.18:compile
```

Spring integration:

```text
io.github.jcondotta:scoped-logging-spring-boot:jar:0.1.0-SNAPSHOT
+- io.github.jcondotta:scoped-logging-core:jar:0.1.0-SNAPSHOT:compile
|  \- org.slf4j:slf4j-api:jar:2.0.18:compile
+- org.springframework.boot:spring-boot-autoconfigure:jar:4.1.1:compile
|  \- org.springframework.boot:spring-boot:jar:4.1.1:compile
+- org.springframework:spring-context:jar:7.0.9:compile
|  +- org.springframework:spring-beans:jar:7.0.9:compile
|  +- org.springframework:spring-core:jar:7.0.9:compile
|  |  +- commons-logging:commons-logging:jar:1.3.5:compile
|  |  \- org.jspecify:jspecify:jar:1.0.0:compile
|  +- org.springframework:spring-expression:jar:7.0.9:compile
|  \- io.micrometer:micrometer-observation:jar:1.16.7:compile
|     \- io.micrometer:micrometer-commons:jar:1.16.7:compile
+- org.springframework:spring-aop:jar:7.0.9:compile
\- org.aspectj:aspectjweaver:jar:1.9.25.1:compile
```

No Logback, Log4j2 or other SLF4J provider is introduced by either production artifact.
Logback is test-scoped only. Spring's transitive Commons Logging is an API used by Spring,
not a concrete SLF4J provider. Core contains no Spring/AOP classes or dependencies.

## Installed artifacts

```text
~/.m2/repository/io/github/jcondotta/
├── scoped-logging/0.1.0-SNAPSHOT/scoped-logging-0.1.0-SNAPSHOT.pom
├── scoped-logging-core/0.1.0-SNAPSHOT/
│   ├── scoped-logging-core-0.1.0-SNAPSHOT.pom
│   └── scoped-logging-core-0.1.0-SNAPSHOT.jar
└── scoped-logging-spring-boot/0.1.0-SNAPSHOT/
    ├── scoped-logging-spring-boot-0.1.0-SNAPSHOT.pom
    └── scoped-logging-spring-boot-0.1.0-SNAPSHOT.jar
```

## Review outcome

Ready for local consumption. No blocking extraction findings or unapproved behavioral
changes were found. Spring lifecycle delegation is unchanged: the aspect resolves the
logger/operation and invokes the core executor. Banking-system migration is proposed
separately in MIGRATION.md and has not been performed.

Publication remains pending license approval, real remote/project metadata, source/Javadoc
attachments, signing and repository setup. No Central publication or remote creation was
attempted. The README documents synchronous/AOP boundaries and the preserved split-package
limitation for JPMS consumers.
