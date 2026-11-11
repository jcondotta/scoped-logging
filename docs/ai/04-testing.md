# Testing Guidelines

Use these guidelines when adding, modifying, reviewing, or refactoring tests.

Testing is an autonomous stage. Follow the agreed behavior and design rather than introducing new behavioral decisions through tests.

Testing must not be used to close an OPEN Design decision. If writing or updating a test requires choosing between viable behavior, public contract, ownership, data exposure, responsibility, abstraction, persistence, consistency, or observability alternatives that were not resolved in Design, stop testing work and return to Design before encoding the choice in tests.

Before introducing a new test pattern, inspect the nearest existing test for the same layer or component type. Prefer established local patterns over generic testing rules.

## Test Types

Use `*Test` for unit tests and `*IT` for integration tests.

Unit tests should cover:
- domain value objects, identities, aggregate behavior, ownership checks, exceptions, and failure reasons
- application command/query handlers, repository interactions, structured logging, concurrency-limit behavior, and failure propagation
- infrastructure mappers, DTO validation, controllers, exception handlers, properties, persistence mappers, and repository adapters

Integration tests should cover:
- HTTP flows with Rest Assured
- PostgreSQL persistence through Testcontainers
- Liquibase schema behavior
- concurrency behavior
- correlation IDs, API version headers, status codes, and `ProblemDetail` payloads when changed

Do not add browser E2E tests for this service unless a real browser-facing workflow is introduced.

## Naming And Structure

Follow the existing naming style:
- class names: `*Test` and `*IT`
- method names: `shouldExpectedBehavior_whenScenario`

Use Arrange-Act-Assert as the default structure, but keep the layout natural when a test needs setup helpers or multiple related observations.

Use `@ParameterizedTest` for input families such as blank values, invalid identifiers, domain-specific value validation, pagination bounds, and validation limits.

Do not add `@DisplayName` unless the surrounding test class already uses it.

## Assertions

Use AssertJ as the default assertion library.

Prefer AssertJ `SoftAssertions` for new or modified tests when a single behavior has multiple related outputs that should be evaluated together, such as:
- persisted aggregate state
- response status, headers, and body
- `ProblemDetail` fields
- structured log level, event type, and outcome

Existing tests may use JUnit `assertAll` with AssertJ assertions. Preserve that style when working in tests that already use it unless the test is being meaningfully reworked; do not refactor tests solely to replace `assertAll` with `SoftAssertions`.

Use direct assertions when there is only one relevant outcome or when grouping assertions would reduce readability.

Do not split one coherent behavior into many tests only to satisfy an arbitrary assertion count.

Prefer assertions that verify observable behavior. Avoid tests that only mirror implementation details or framework wiring.

For public API tests, assert the behavior and contract that were resolved in Design. Do not infer status codes, response shape, ownership semantics, or error payloads solely from a similar endpoint unless that decision was explicitly resolved.

## Mocks And Test Data

Use Mockito for application and controller unit tests when collaborators are ports, handlers, mappers, or servlet requests.

Prefer existing local fixtures and test support, especially for:
- domain fixtures and test data builders
- fixed time factories
- blank-value sources
- validator factories

Keep tests deterministic. Use fixed clocks and explicit fixtures instead of depending on wall-clock time or random data when the exact value matters.

## Integration Tests

Use the existing integration-test annotation or base setup when the project provides one. It should configure:
- random web port
- `test` profile
- Testcontainers initialization

Use the project's established HTTP test client and keep request setup close to the endpoint under test.

For HTTP integration tests, follow the existing request-specification pattern, including API versioning and request/response logging on validation failures.

Verify both HTTP behavior and the important persistence side effect when the endpoint changes state.

Clean or isolate state through existing repository/test support patterns. Do not introduce shared mutable state between tests.

## Structured Logging Tests

For application handlers that emit structured logs, follow the existing handler-test pattern.

- Capture log events with the project's existing log-capture support.
- Attach the appender during setup and detach it during teardown.
- Verify structured log semantics rather than formatted log messages.
- Verify the expected log level, event type, outcome, and relevant structured keys.
- Cover success, expected/domain failure, and unexpected failure logging when those paths exist.
- Follow the nearest handler test for the exact setup and assertions.

## Coverage And Test Selection

Keep meaningful coverage for changed behavior. Do not chase coverage by testing trivial constructors, generated framework behavior, or configuration binding that is already covered elsewhere.

For core business rules, prefer focused domain tests with mutation-resistant assertions.

For API, schema, persistence, logging, observability, or concurrency changes, update the matching integration coverage.

Use the local project or bounded-context playbook to choose the focused verification command for the change type.

## Test-First Guidance

Prefer test-first for new behavior and bug fixes when the expected behavior is clear.

Do not block small refactors or mechanical renames on writing a failing test first when existing focused tests already cover the behavior.

Do not use tests to silently decide behavior that requirements or design left intentionally unresolved.

If tests reveal that the implementation made a relevant decision that is absent from the resolved Design, treat it as a process finding and return to Design before expanding test coverage around that behavior.
