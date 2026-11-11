# Requirements Guidelines

Use this stage to understand what should be built before deciding how it should be implemented.

## Goal

Establish the requested behavior, scope, constraints, and relevant open questions.

Requirements analysis should discover what needs to be decided. It should not prematurely turn implementation choices into requirements.

Requirements does not approve a design. It produces evidence and a list of decisions that Design must resolve.

## Analysis

Inspect the relevant existing behavior before concluding what the requested change requires.

Identify, when relevant:
- expected user-visible behavior
- success and failure scenarios
- scope boundaries
- edge cases
- compatibility with existing behavior
- API contract implications
- persistence implications
- security or data-exposure concerns
- observability expectations
- concurrency implications
- assumptions that are not explicit in the request

Use existing production code and tests to understand current behavior.

Do not infer new requirements solely from what would be convenient to implement.

When existing behavior or tests show a pattern, record it as evidence. Do not treat it as resolving the new feature's behavior unless the user's request or an existing public contract explicitly requires the same behavior.

## Open Questions

Actively identify ambiguities or missing decisions that could materially affect the resulting behavior or design.

Examples include:
- behavior for missing or conflicting resources
- ownership or authorization semantics
- whether existing behavior must remain backward compatible
- whether an operation is scoped by another resource
- whether returned data should match an existing public representation
- edge cases that would materially affect the contract

Do not stop analysis at the first open question if useful investigation can continue.

Collect the relevant open questions and carry them into the design stage.

For each potential decision, note whether it could affect:
- externally visible behavior or status codes
- API path, method, headers, request, or response shape
- resource ownership, authorization, or data exposure
- application/domain/infrastructure responsibilities
- repository port choice or abstraction boundaries
- observability, metrics, logging, or correlation behavior
- persistence semantics or consistency guarantees
- test scope for public contracts

If two or more viable alternatives exist in any of these areas, classify the item as a Design decision to be resolved interactively. Do not close it in Requirements because one alternative follows an existing pattern, appears safer, simpler, more conservative, more idiomatic, or matches another endpoint.

Do not ask about routine implementation details.

## Output

Requirements analysis should make clear:
- what behavior is already determined
- what constraints already exist
- what remains ambiguous
- which decisions need to be resolved during design
- which existing patterns are evidence for recommendations rather than decisions

Do not silently resolve meaningful behavioral or product ambiguity merely to produce a complete-looking requirement.
