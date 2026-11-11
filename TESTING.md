# Core test coverage

The core tests exercise `LogScopeExecutor.execute()` and `run()` directly, without Spring.
Existing context tests also use the public executor instead of reproducing its try/finally.

## Concurrency matrix

The matrix combines platform/virtual threads, 2/3 workers, 1/2/3 scopes per worker,
nested/sequential execution, and success/failure in one worker: 48 invocations.
Every invocation repeats twice on the same executor pool. All workers share the same
context, scope executor, and logger. Timed barriers force overlapping bindings.

Assertions cover exact event counts, per-worker completion order, unique keys, isolated
messages/levels/causes/outcomes, last-write-wins attributes, parent restoration, and clean
state after completion. Event capture uses a concurrent queue; no sleep-based scheduling
or global ordering between workers is assumed.

## Additional scenarios

- Return identity, null results, void operations, automatic completion before return.
- Default and explicit severities, classified failures, contradictory declarations.
- Checked exceptions, runtime exceptions and business AssertionError propagation.
- Three-level propagated failures, caught/wrapped failures and successful recovery.
- Successful child followed by failed parent; independent context instances.
- Required null executor arguments rejected before executing business code.
- Unicode/multiline messages, 1,000 attributes and logger filtering.
- Builder creation, attribute addition and emission failures, including a failing
  diagnostic logger, with business return/exception and parent context preserved.
- Cancellation after task entry, interruption, and waiting for actual scope exit.
- Raw platform/virtual child threads do not automatically inherit active scopes.

All executor pools are shut down in finally blocks and waits have timeouts. Duration
assertions allow zero and do not rely on exact wall-clock timing.

## Commands

Run the core tests (the initial version excludes Spring integration):

```sh
mvn test
```

Include the optional stress case (8 workers, depth 20, 20 rounds per thread kind;
6,400 scope events in total):

```sh
mvn test -Dscoped.logging.stress=true
```

Stress is disabled by default to keep ordinary builds small. It is reproducible and
bounded, and supplements the deterministic matrix rather than proving all interleavings.

## Contracts still requiring decisions

No new behavior is imposed by tests for null/blank attribute keys or messages, blank
operation names, overriding reserved keys, or mutable attribute snapshots. These require
explicit API decisions before dedicated assertions are added.

Concurrent mutation of a single inherited state remains unsupported. Java 25
StructuredTaskScope preview integration is not exercised by this non-preview core suite;
the platform/virtual tests open independent scopes in their workers.

Logging tests verify attempted emission and captured events with enabled levels and a
working provider. They do not guarantee delivery by a failing external logging backend.
