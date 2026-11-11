# Implementation Guidelines

Use this stage to implement behavior and design that are already sufficiently defined.

## Goal

Implement the requested change autonomously while preserving the agreed behavior, repository architecture, and established local conventions.

## Entry Condition

Do not begin implementation merely because the user used words such as `implement`, `add`, `create`, `change`, or `fix`.

For a new feature or non-trivial behavioral change, first determine whether the relevant requirements and design decisions have already been resolved.

If meaningful behavioral, public-contract, responsibility, abstraction, architectural, security/data-exposure, persistence, consistency, observability, or test-scope decisions remain unresolved, return to the corresponding earlier stage before editing code.

Implementation may begin only after the Design gate has no OPEN decisions for the change.

Once implementation begins, it is autonomous.

## Before Editing

Review the relevant requirements and design decisions when they are available.

Inspect only the production code, tests, configuration, and resources necessary to understand the affected implementation.

Use the local project or bounded-context playbook when the change affects multiple architectural areas.

Do not reopen decisions that were already explicitly resolved by the user unless the implementation reveals a concrete contradiction or impossibility.

## Implementation

Follow the closest established local pattern when it is compatible with the agreed design.

Keep changes focused on the requested behavior.

Preserve:
- dependency direction
- framework-free domain code
- existing contracts unless intentionally changed
- established package responsibilities
- unrelated user changes

Prefer the smallest coherent change that fully implements the agreed behavior.

Do not introduce new abstractions, generalized infrastructure, or architectural movement without a concrete need from the requested change.

## Decisions Discovered During Implementation

Implementation is autonomous for the design that has already been resolved.

If implementation exposes a decision that was not resolved during Requirements or Design and two or more viable alternatives affect behavior, public contract, responsibility, abstraction, architecture, security/data exposure, persistence semantics, consistency, observability, or test scope:
- stop implementation before making further code changes for that decision;
- leave already-made changes untouched unless they prevent the project from being inspected or the user explicitly asks to revert them;
- document the newly discovered decision, alternatives, trade-offs, and recommendation;
- return to Design and ask the user to resolve it;
- resume implementation only after the Design gate has no OPEN decisions again.

Do not convert an implementation-time discovery into an autonomous choice because one option is conservative, simpler, safer, more idiomatic, consistent with existing behavior, or easier to implement.

If the newly discovered item is purely mechanical and does not affect behavior, public contract, responsibilities, abstractions, architecture, security/data exposure, persistence, consistency, observability, or test scope, resolve it autonomously using local conventions.

Do not silently treat a decision made autonomously during implementation as having been agreed with the user.

If an unresolved issue makes correct implementation impossible rather than merely requiring a choice between viable alternatives, stop and explain the blocker.

## Scope Discipline

Do not perform unrelated refactors merely because nearby code could be improved.

Do not move or generalize existing abstractions unless required by the implemented behavior or agreed design.

Do not change public behavior, persistence semantics, or architectural ownership as incidental cleanup.

Preserve unrelated user changes.

## Completion

After implementation:
- update or add the necessary tests
- run the focused verification appropriate to the affected areas
- proceed to validation for non-trivial changes

Do not consider passing tests alone sufficient evidence that the implementation is ready.
