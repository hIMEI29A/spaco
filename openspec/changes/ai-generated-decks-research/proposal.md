# Proposal

> **RESEARCH ONLY - BLOCKING.** This change produces a research document, not
> implementation work. No application source, build files, or production specs
> are modified. It blocks the five deck-evolution features (#7 Living Decks,
> #8 Hierarchical & linked decks, #9 Multi-mode cards, #10 Campaign Decks,
> #11 Personal annotations & sharing) until it is reviewed.

## Why

Every planned deck-evolution feature assumes the coach can synthesize content
that does not exist yet: a deck, its items, a skill tree, a campaign. Today's
coach cannot. `CoachAgent` only *varies an already-existing item*
(`generateExercise` is anchored to `request.item.id` and `CoachOutputValidator`
discards anything whose `targetItemId` does not match), and every `Deck` and
`PracticeItem` is created one at a time by hand through `DeckRepository.create`
and `ItemRepository.add`. Generating a complete skill deck from a free-form
topic (for example, "Low-level programming in C") is therefore a new class of
operation - entity creation from unstructured input - that no current contract
supports. Issue #16 requires this gap to be investigated and a contract chosen
before any of the five features may proceed, so that each feature does not
reinvent generation on its own.

## What Changes

- **Research only.** Adds a research document to `openspec/changes/`; no runtime
  behavior, build configuration, or production spec changes.
- Records a deep, code-grounded investigation of AI-generated skill decks:
  - the generation flow (synchronous vs background, streaming vs batch, offline
    fallback) and the contract between UI, the local Room layer, and the coach;
  - how generated items are validated and persisted to fit the existing
    `skill -> deck -> item` model and the derived spaced-repetition schedule;
  - the new entities and metadata (generation provenance, confidence, review
    decision, topic source) that generation requires;
  - hallucination and data-quality risk, and the viable mitigations;
  - token usage, cost per generation, and latency/UX patterns;
  - runtime load and behavior under poor or absent connectivity.
- **Confirms the review-gate decision (option (a)):** generated content is never
  persisted into the practice model directly. It is staged as drafts that require
  explicit acceptance before it can enter scheduling.
- **Lists the architectural decisions** that later deck-evolution work must
  respect, and a **go/no-go recommendation** with an MVP scope and open
  questions.
- **Sketches** (as prose in `design.md`, not as files) the delta-spec
  requirements that `skill-deck-management` and `ai-coach` would need if the
  feature is approved.
- Sets `skip_specs: true` in `.openspec.yaml`: this change creates no spec deltas
  by design, and `tasks.md` is intentionally not produced.

## Capabilities

### New Capabilities

None. This is a research-only change with `skip_specs: true`. A prospective
`ai-deck-generation` capability is sketched in `design.md` and is deferred to a
future, approved change.

### Modified Capabilities

None in this change. Candidate deltas for the existing `skill-deck-management`
and `ai-coach` specs are sketched in `design.md` and are deferred to a future,
approved change. No requirement in any existing spec changes here.

## Impact

- **Documentation only.** Adds `openspec/changes/ai-generated-decks-research/`
  (`proposal.md`, `design.md`).
- **No runtime impact:** no application code, no dependencies, no API changes,
  no Room schema or migration.
- **Downstream:** establishes the contract and the decisions that #7-#11 must
  build on; those features remain blocked until this research is reviewed and a
  follow-up implementation change is approved.
