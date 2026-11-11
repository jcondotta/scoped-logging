# Scoped Logging Core

Standalone synchronous scoped logging for Java 25, without preview flags. SLF4J API is
its only external production dependency; the consuming application supplies a provider.
Share a `ScopedLogContext` between `LogScopeExecutor` and collaborators that enrich events.

```java
var context = new ScopedLogContext();
var scopes = new LogScopeExecutor(context, new Slf4jLogEventPublisher());
var result = scopes.execute("orders", "orders.create", () -> {
    context.with("order.id", "123");
    return "123";
});
```

The classes above are in `com.jcondotta.scopedlogging.application.logscope` and
`com.jcondotta.scopedlogging.infrastructure.slf4j`. Normal return defaults to success;
severity, outcome and business `reason` retain their independent semantics. Nested scopes
isolate attributes and restore their parent on exit.

## Event semantics

- Normal return defaults to `outcome=success`, INFO (or the executor's supplied default
  severity), and `Operation completed`.
- `success()` and `failure()` replace the current outcome without ending the scope or
  changing severity. On normal return, a current failure outcome defaults to WARN and
  `Operation failed` when there is no explicit event declaration.
- The latest event declaration replaces severity, message and cause together. Outcome is
  independent: `error(message, cause)` followed by normal return still defaults to success.
- An escaping exception determines failure outcome, ERROR severity and the original cause,
  even after `success()`. The latest declared message is retained; otherwise the message is
  `Operation failed with an exception`. Completion does not stringify the business exception.
- `operation`, `outcome` and `duration_ms` are reserved; `with()` rejects them with
  `IllegalArgumentException`. `duration_ms` measures through completion, excludes publication,
  and is truncated to whole milliseconds (possibly zero).
- Attribute keys must be non-null; values may be null. Later writes replace earlier values.
  Blank attribute keys and blank messages are accepted; null messages are rejected.
  `reason` is an ordinary business attribute, with the fallback below.

| Completion | Supplied reason | Completed reason |
| --- | --- | --- |
| Escaping exception, no current explicit failure outcome | Absent | `"internal_error"` |
| Escaping exception | Explicit null | Null; key remains present |
| Escaping exception | Non-null value | Supplied value |
| Normal return | Absent | Absent |
| Normal return | Explicit value, including null | Supplied value |
| Escaping exception after `failure()` (not superseded by `success()`) | Absent | Absent |

Explicit null is distinct from absence. This corrects the pre-release behavior that replaced
null with `"internal_error"` on exceptional exit without a current failure declaration.
Consumers relying on that replacement must now omit the key to request the fallback.
The existing suppression of the fallback by a current explicit failure outcome is unchanged.

`LogEvent` exposes an unmodifiable copy of the attribute map, retaining values by reference.
This is a shallow snapshot: use immutable values or keep mutable values stable while read by
the publisher. An asynchronous publisher needing a stable snapshot must materialize mutable
values before enqueueing. The library does not sanitize, redact, limit or serialize values;
avoid secrets and use simple, stable values under the consuming application's logging policy.
The SLF4J adapter forwards structured key/value pairs; the configured provider controls
filtering and rendering, including how null values appear. No JSON format is imposed.

## Failure isolation

Business execution takes precedence over recoverable logging completion failures.
`LogScopeExecutor` isolates exactly `RuntimeException` and `AssertionError` thrown while
completing or publishing the event. A successful action returns its exact original value;
a failed action propagates its exact original exception instance. The executor never
re-executes the action and never retries completion or publication. Completion failure
means publication is not attempted. Each completed event receives one publication attempt;
publication failure may occur after partial delivery. Filtering or a missing provider can
also prevent delivery, so neither delivery nor exactly-once behavior is guaranteed.

The executor attempts one `System.Logger` WARNING with the operation name and logging
failure. This bypasses the event publisher and does not open another logging scope.
`RuntimeException` and `AssertionError` in that diagnostic fallback are also isolated,
without further diagnostics or retries. Diagnostics are best effort: logger configuration,
filtering or a failing backend can prevent them from appearing. Custom publishers and
logging backends must avoid uncontrolled recursive instrumentation.

Other errors propagate, including potentially fatal `VirtualMachineError`, `ThreadDeath`
and `LinkageError`. An error of this kind from completion, publication or the diagnostic
fallback can override a successful business result or an original business exception.
The executor does not indiscriminately swallow `Throwable`.

This guarantee applies to automatic completion after action execution. It does not suppress
argument validation, scope setup failures or exceptions from explicit context calls made
inside the action (for example, using a reserved attribute key). Direct calls to a publisher,
including `Slf4jLogEventPublisher`, do not receive the executor's isolation policy. Publication
and diagnostics remain synchronous and contribute to caller latency. There is no protection
against arbitrary JVM failures, deadlocks, blocking publishers, infinite loops, or publisher
side effects. `duration_ms` excludes publication latency.

## Concurrency contract and stress coverage

Scopes end when the action returns, including when it returns a future or reactive publisher.
There is no automatic propagation to arbitrary executor tasks, raw child threads or deferred
callbacks. Open a new scope for each independent asynchronous execution. Nested scopes have
independent attributes and restore the parent binding, including on exceptional exits.
Context access outside a scope throws `IllegalStateException`.

Independent scopes may execute concurrently on platform or virtual threads using the same
context and executor. A shared custom publisher must support concurrent publication.
Concurrent mutation of one inherited mutable scope state by structured child tasks is
unsupported; the tests below do not claim to validate that scenario.

The regular concurrency matrix has 48 cases, each repeated twice: platform/virtual threads,
2/3 workers, 1/2/3 scopes, sequential/nested scopes, and successful/exceptional execution.
The optional `shouldKeepEventsIndependent_whenManyDeepScopesRunConcurrently` test uses:

| Dimension | Workload |
| --- | --- |
| Thread modes | Platform, then virtual |
| Workers | 8 concurrent tasks per round |
| Rounds | 20 per mode; rounds execute sequentially |
| Tasks | 160 per mode, 320 total |
| Threads | 8 reused platform pool threads; 160 virtual task threads over the virtual phase |
| Nesting | 20 scopes per task; up to 160 active bindings per phase |
| Completed events | 3,200 per mode, 6,400 total |

Virtual-thread carrier counts are JVM-managed, not fixed by the test. Worker zero throws an
`IOException` at the deepest scope each round, unwinding all its parent scopes. Other workers
complete successfully and enrich their restored parents. Timed barriers force overlapping
bindings at each depth. Assertions check exact counts, per-worker inner-to-outer event order,
attribute isolation, parent restoration, last-write-wins enrichment, messages, severity,
outcome, reason, causes, nonnegative duration and absence of a binding after exit. All workers
share a context, executor and SLF4J publisher; capture uses a concurrent queue.

This can expose regressions in ScopedValue binding/restoration, independent state isolation
and concurrent publication. It increases depth and repetition of guarantees already covered
by the regular matrix; it does not introduce a new concurrency contract or prove every
possible interleaving. Inputs and expected results are deterministic, but scheduling is not.
There is no assertion about global event order. Barrier waits (10 seconds), future waits
(30 seconds) and shutdown waits (10 seconds) can fail under severe machine load, so it is not
immune to environmental flakiness.

The stress case is opt-in via `@EnabledIfSystemProperty(named = "scoped.logging.stress",
matches = "true")`. It appears as skipped in ordinary builds. Enable it in CI and release
verification to supplement the regular matrix while keeping local builds small.

## Verification

Run from the repository root with JDK 25:

```sh
mvn -pl scoped-logging-core -am clean verify
mvn -pl scoped-logging-core -am verify -Dscoped.logging.stress=true
```

The suite covers event semantics, shallow snapshots, reason presence/null handling, independent
concurrency, and recoverable/fatal completion failures. Fatal-error tests throw synthetic
instances synchronously without inducing resource exhaustion or terminating threads.
Diagnostic-fallback tests use the JDK's default JUL backend and restore its configuration;
they do not validate arbitrary third-party System.Logger providers.
