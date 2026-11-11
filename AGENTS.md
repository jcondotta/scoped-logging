# Repository Guidelines

## Project Structure & Module Organization

This is a Java 25 Maven project that publishes a standalone logging core and a separate Spring Boot integration.

- `scoped-logging-core/`: framework-independent scope API, domain model, output port, and SLF4J adapter.
- `scoped-logging-spring-boot/`: optional Spring Boot integration. Keep it separate from core responsibilities.
- `docs/ai/`: shared requirements, design, implementation, testing, and validation guidance.
- Root `pom.xml`: reactor, shared dependency versions, Java release, and plugin management.

Each module may provide a more specific `AGENTS.md`. Follow the most specific applicable guidance in addition to this file.

## Development Workflow

Use `docs/ai/` for requirements, design, implementation, testing, and validation. These stages describe responsibilities rather than ceremony: small mechanical changes may need little design work, while changes to public behavior, contracts, package boundaries, or architecture need decisions resolved before implementation.

Inspect the narrowest relevant module and its tests. Preserve unrelated files and do not modify a sibling module unless the request includes it or a resolved design requires coordinated changes.

## Build, Test, and Development Commands

Run commands from the repository root:

- `mvn -pl scoped-logging-core -am clean verify`: verify the standalone core and its required reactor parent.
- `mvn -pl scoped-logging-core test -Dtest=SomeTest`: run a focused core test.
- `mvn -pl scoped-logging-core dependency:tree -Dscope=compile`: inspect production dependencies.
- `mvn clean verify`: verify the full reactor when all modules are expected to be compatible.

Use JDK 25. The core uses `ScopedValue` without preview flags.

## Architecture & Coding Style

Use Java conventions and 4-space indentation. Preserve the package responsibilities documented in the module-specific `AGENTS.md`.

Keep dependencies pointing inward: infrastructure implements application output ports; application orchestration may depend on domain types and ports; domain code remains framework-free and independent of SLF4J. Do not add frameworks, providers, dependencies, layers, or abstractions without a demonstrated need.

The core must remain independently usable without Spring. SLF4J API is its only external production dependency; logging providers belong to consuming applications.

## Testing Guidelines

Use the test conventions in `docs/ai/04-testing.md` and the relevant module guide. Prefer focused JUnit Jupiter and AssertJ tests, matching production packages. Domain tests should cover each scenario explicitly and assert observable behavior and invariants.

Run tests when requested or when they are needed to verify an implemented change. Keep test dependencies test-scoped and do not add test frameworks without demonstrated need.

## Agent-Specific Instructions

Treat production code, tests, Maven configuration, and documentation as evidence for current behavior. Do not infer behavior from package names alone. Preserve the user's explicit module scope, and do not modify `banking-system` as part of work in this repository.
