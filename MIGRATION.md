# Proposed banking-system migration

This is a future change. Extraction did not modify banking-system.

1. Make `io.github.jcondotta:scoped-logging:0.1.0-SNAPSHOT` and its two child artifacts
   available to each developer and CI runner with `mvn clean install`. Local installation
   on one machine does not make the artifacts available to CI. Agree on distribution
   before merging a migration; publication is a separate approved action.
2. Update banking-system dependency management to group `io.github.jcondotta`, version
   `0.1.0-SNAPSHOT`, for both `scoped-logging-core` and `scoped-logging-spring-boot`.
3. Replace the old `com.jcondotta` integration coordinates in accounts, recipients,
   transfers, ledger and core/infrastructure-core. Replace the test-scoped core dependency
   in core/application-core. Preserve existing scopes and intentional direct dependencies.
   The intended application dependency is:

   ```xml
   <dependency>
       <groupId>io.github.jcondotta</groupId>
       <artifactId>scoped-logging-spring-boot</artifactId>
       <version>0.1.0-SNAPSHOT</version>
   </dependency>
   ```

4. Remove `scoped-logging` from core's reactor modules and retire the in-tree source in
   that same migration change, so duplicate `com.jcondotta.scopedlogging` classes never
   reach the application classpath. Existing Java imports and annotation usages remain
   valid because this extraction preserves package names and the public API.
5. Keep each application's selected SLF4J provider and structured output configuration.
   Review dependency trees for one provider, no old logging coordinates and no duplicate
   classes. Continue using the existing Boot dependency management; test the resolved
   dependency versions rather than adding library-wide overrides to applications.
6. Leave outbox's `logging.legacy` API and its `LogEvent` untouched. It has different
   manually emitted semantics; converting it requires a separate behavior review and tests.
7. Run focused logging, handler, request-context filter and outbox tests, then the full
   banking-system `./mvnw clean verify` with required local services. Inspect representative
   success, classified failure, exception and nested-operation events for unchanged keys,
   severity, outcome and duration. Include platform and virtual-thread execution.
8. Review and commit the migration separately. Roll back by reverting that migration and
   restoring the original reactor dependency if application-level verification fails.

The current working-tree POM includes accounts, recipients, transfers and ledger in the
root reactor, despite older repository guidance describing transfers separately. Base the
migration commands on the POM at migration time.
