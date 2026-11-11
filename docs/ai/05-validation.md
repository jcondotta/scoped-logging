# Validation Guidelines

Use this stage after implementation and testing to independently review the completed change before considering it finished.

Validation is autonomous. Complete the review before asking the user about any findings or decisions.

## Goal

Determine whether the resulting change is correct, coherent with the repository, consistent with agreed decisions, and ready to be accepted.

Validation is not a continuation of implementation reasoning. Review the result with fresh eyes.

Do not assume that a decision made during implementation was correct merely because the implementation is complete or the tests pass.

## Review Inputs

Review:
- the original request
- requirements established for the change
- design decisions agreed with the user
- the Design decision list and gate outcome when the change is a new feature or non-trivial behavioral change
- relevant repository guidance
- the final working-tree diff
- affected production code
- affected tests
- configuration, schema, observability, or infrastructure when relevant

Inspect additional files only when needed to verify a finding.

## Validation Checks

Verify that:
- the requested behavior is fully implemented
- agreed requirements and design decisions are respected
- no behavior, public contract, responsibility, abstraction, architecture, security/data exposure, persistence, consistency, observability, or test-scope decision was implemented without being resolved in Design
- no important behavior was omitted
- no unrelated behavior was changed
- architectural boundaries and responsibilities remain coherent
- dependency direction remains correct
- existing abstractions were not broadened, moved, or repurposed without justification
- public API and error semantics are intentional
- persistence and schema changes are safe and incremental
- security and sensitive-data handling remain appropriate
- observability changes are consistent when affected
- tests cover the important behavior and regression risks
- the appropriate focused verification has passed
- unrelated user changes were preserved

## Decisions Introduced During Implementation

Identify meaningful behavioral, contract, or architectural decisions made during implementation that were not explicitly resolved during requirements or design.

Do not stop validation to ask about them.

Treat any such decision as a process finding. Passing tests or consistency with existing patterns does not make it accepted.

For each such decision:
- explain what was decided
- explain why the decision became necessary
- identify materially different alternatives when relevant
- state the rationale used by the implementation
- recommend returning to Design for user resolution before considering the change ready, unless the decision is purely mechanical

A decision made autonomously during implementation must not be presented as though it had been agreed with the user.

## Findings

Distinguish between:
- correctness or regression issues
- deviations from agreed requirements or design
- architectural or contract concerns
- decisions introduced during implementation
- missing or incomplete Design gate evidence
- optional improvements

Do not manufacture findings merely to make the review appear comprehensive.

Do not propose cleanup or refactoring unless it materially improves correctness, maintainability, architecture, behavior, or consistency with repository conventions.

## Validation Output

Complete the entire validation before reporting results.

The final result should:
- state whether the change is ready
- report blocking or important findings first
- list decisions introduced during implementation that deserve user attention
- state whether the Design gate was satisfied for non-trivial changes
- include relevant recommendations
- separate optional suggestions from issues that should be addressed

If no relevant issues or unresolved decisions are found, say so clearly.

Do not modify the implementation during validation unless the user explicitly asks for fixes.
