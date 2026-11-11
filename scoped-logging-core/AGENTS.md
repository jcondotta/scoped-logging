# Scoped Logging Core Guidelines

## Responsibility & Package Structure

The core is a standalone Java 25 library. Keep its responsibilities limited to synchronous scoped structured logging.

- `domain.logging`: immutable logging concepts and values, including `LogEvent`, `LogEventDeclaration`, `LogKey`, `LogLevel`, and `LogOutcome`. Domain types must not depend on application, infrastructure, SLF4J, Spring, or other frameworks.
- `application.logscope`: `LogScopeExecutor`, `ScopedLogContext`, `LogContext`, `LogAction`, and per-scope state. The executor owns execution and automatic completion; the context exposes the current `ScopedValue` binding.
- `application.port.output`: output contracts such as `LogEventPublisher`.
- `infrastructure.slf4j`: the SLF4J adapter that turns completed domain events into structured SLF4J events.

Dependencies point inward: infrastructure implements application ports, application uses domain types and output ports, and domain is independent. Keep mutable `LogState` scoped to one synchronous invocation. Do not add thread-local storage or concurrent mutation support for inherited child-task bindings.

## Contracts To Preserve

- One completed event is published per scope.
- Normal return defaults to success. Outcome and severity remain independent.
- The latest explicit `LogEventDeclaration` replaces the previous severity, message, and cause together.
- An escaping exception determines final ERROR severity and cause; explicit business failure attributes remain available.
- `operation`, `outcome`, and `duration_ms` are reserved. `reason` remains a business classification attribute.
- Nested scopes isolate state and restore their parent.
- Publisher failures must not replace a business result or escaping exception.

Make changes to these contracts explicit, focused, and covered by tests. Validate record invariants at construction boundaries and defensively copy mutable collections exposed by immutable values. `LogEvent` copies its attribute map shallowly; consumers must keep mutable attribute values stable while the publisher reads them.

## Dependencies & Build

Keep `org.slf4j:slf4j-api` as the only external production dependency. Do not add logging providers, Spring, DI, AOP, or banking-system dependencies. JUnit Jupiter, AssertJ, and Logback are test-only dependencies.

Run from the repository root:

- `mvn -pl scoped-logging-core -am clean verify`
- `mvn -pl scoped-logging-core test -Dtest=SomeTest`
- `mvn -pl scoped-logging-core dependency:tree -Dscope=compile`

## Test Conventions

Follow `../docs/ai/04-testing.md` and the parent `AGENTS.md`. Place tests under matching packages in `src/test/java`. Keep each domain and application scenario in a separate test method; use parameterized tests for true input families that exercise the same behavior. Name tests `shouldExpectedBehavior_whenScenario`; when an exception is the behavior, name the exception explicitly, such as `shouldThrowNullPointerException_whenMessageIsNull`. Extract repeated test values into named constants.

Prefer direct AssertJ assertions for one behavior and grouped assertions only when they improve clarity. Test domain value invariants directly; test scope behavior through the public executor/context API. Do not introduce mocks or new test libraries when a small fake or direct value test is sufficient.

## Scope Discipline

Keep core changes within this module unless coordinated work is explicitly included. The Spring Boot integration consumes core APIs and may need a separate migration after a breaking core API change; do not edit that module under a core-only request. Update the core README examples when public constructors, packages, or composition change.
