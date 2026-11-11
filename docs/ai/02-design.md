# Design Guidelines

Use this stage to agree on how the requested change should behave and fit into
the existing system before implementation begins.

## Goal

Reach a shared design with the user before writing code.

This is an interactive stage.

For new features and non-trivial behavioral changes, do not implement the
solution until the Design process below has been completed.

Design is complete only when every feature-level decision is either RESOLVED
or proven not applicable. OPEN decisions block implementation.

## Design Process

### 1. Inspect

Read the relevant existing implementation, tests, contracts, and repository
guidance.

Understand what is already fixed by the user's request and what remains open.

Do not edit code during this stage.

### 2. Build the Decision Tree

Before proposing an implementation, identify the decisions exposed by the
change.

Walk through at least these areas when applicable:

- behavior and error semantics
- API path and HTTP contract
- request and response models
- domain behavior and invariants
- application/use-case responsibilities
- repository and data-access strategy
- persistence behavior
- abstraction reuse or creation
- package and architectural boundaries
- security and data exposure
- concurrency and consistency
- observability
- edge cases
- testing implications

For each area, determine whether more than one viable choice exists.

Create an explicit decision list. For each decision, record:

- decision: the concrete question to resolve
- status: OPEN or RESOLVED
- alternatives: the materially different viable options
- evidence: existing behavior, repository guidance, constraints, risks, and
  trade-offs
- recommendation: the option you recommend and why
- resolution source: user decision, explicit user request, hard constraint, or
  not applicable

If you can identify two or more viable alternatives that affect behavior,
public contract, responsibility, abstraction, architecture, security/data
exposure, persistence semantics, consistency, or observability, record the
decision as OPEN.

Do not choose an alternative while building the decision tree.

Do not mark a decision RESOLVED merely because:

- one alternative follows an existing repository pattern
- one alternative matches another endpoint or feature
- one alternative is more conservative
- one alternative appears safer
- one alternative appears simpler
- one alternative appears more idiomatic
- one alternative is easier to test
- one alternative minimizes code changes

Those factors are evidence for the recommendation, not a substitute for the
user's decision.

A decision may be RESOLVED without asking only when:

- the user's request explicitly selected the behavior or design;
- an earlier decision in the same task logically determines it;
- a documented public contract or hard technical constraint leaves no viable
  alternative; or
- the item is purely mechanical and has no effect on behavior, public
  contract, responsibilities, abstractions, architecture, security/data
  exposure, persistence, consistency, observability, or test scope.

### 3. Interview the User

If the decision tree contains any OPEN decisions, you MUST ask the user
questions before implementation.

Do not resolve an OPEN decision yourself.

For each OPEN decision:

1. explain what needs to be decided;
2. present the viable alternatives;
3. explain the important trade-offs;
4. recommend one option;
5. ask the user to choose or confirm.

Existing code, repository patterns, conventions, security considerations,
simplicity, or a clearly preferred option may support your recommendation.

They do NOT remove the requirement to ask when more than one viable
alternative exists.

Prefer asking a Design question that later proves unnecessary over silently
making a relevant decision that may require rework.

Never convert:

"Option A appears preferable to Option B"

into:

"I will use Option A."

Instead, ask:

"I recommend Option A because [...]. Do you want A or B?"

### 4. Resolve Dependencies

Work through the decision tree until every OPEN decision is resolved.

When one decision affects another, resolve the prerequisite first and then
re-evaluate the dependent decision.

Do not assume the user's answer to one decision resolves another unless that
dependency logically determines it.

After each user response, update the set of RESOLVED and OPEN decisions.

If additional investigation reveals another decision, add it to the tree and
ask about it before implementation.

### 5. Design Gate

Implementation MUST NOT begin while any identified Design decision is OPEN.

Before leaving Design, explicitly verify the decision list. The gate passes
only when every applicable item below is RESOLVED with an acceptable
resolution source, or marked not applicable:

- [ ] behavior and error semantics resolved
- [ ] API contract resolved
- [ ] request/response contract resolved
- [ ] domain/application responsibilities resolved
- [ ] repository/data-access strategy resolved
- [ ] abstraction and architectural boundaries resolved
- [ ] security/data-exposure behavior resolved
- [ ] concurrency/consistency behavior resolved when applicable
- [ ] observability behavior resolved when applicable
- [ ] important edge cases resolved
- [ ] testing implications understood

If an applicable item still contains more than one viable alternative, ask
the user.

Do not enter Implementation until this gate passes.

When reporting the Design outcome, include the resolved decision list or a
concise summary of each resolved decision and its source. Do not imply that
recommendations based on repository patterns were user-approved unless the
user explicitly accepted them.

## Questioning Rules

During Design:

- Ask rather than assume.
- Prefer one unnecessary Design question over silently making a decision that
  could require rework.
- Do not use an existing pattern as permission to resolve an OPEN decision.
- Do not make a decision merely because one alternative is conservative,
  simpler, cleaner, safer, or more conventional.
- Do not treat behavior from another endpoint as resolving a new endpoint's
  contract when alternatives remain viable.
- Do not begin editing while waiting for a Design answer.
- Push back when the user's choice conflicts with repository architecture,
  established behavior, or another resolved decision.
- Explain the conflict and let the user decide how to proceed.

Do not ask about purely mechanical coding details such as formatting, import
ordering, local variable names, or other choices that do not affect the
design.

## Design Outcome

When all decisions are resolved, summarize the agreed design.

The summary should capture:

- behavior and error semantics
- API and public contracts
- domain and application responsibilities
- repository and data-access strategy
- architectural boundaries
- persistence implications
- security/data-exposure decisions
- concurrency or observability decisions when relevant
- important edge cases
- expected testing scope

Only after this summary is established may the workflow proceed to
Implementation.

Implementation should then proceed autonomously from the agreed design.
