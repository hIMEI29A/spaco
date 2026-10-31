# Proposal

## Why

Today the AI coach can only *vary an existing practice item* (`CoachAgent.generateExercise`
is anchored to `request.item.id`), and every deck and item is created by hand one
row at a time. There is no way to turn a free-form topic into a deck. That
capability is the foundation every deck-evolution feature depends on (#7 Living
Decks, #8 Hierarchical decks, #9 Multi-mode cards, #10 Campaign decks, #11
Annotations & sharing), and it is exactly what the research-only change
`ai-generated-decks-research` (issue #16) investigated and specified at the
contract level. This change implements that research's MVP: **topic -> one flat
deck**, remote-only, drafts-first.

## What Changes

- **New domain port `DeckGenerator`** (separate from the advisory, item-anchored
  `CoachAgent`): `generateDeck(DeckGenerationRequest) -> GeneratedDeck?`, with
  `DeckGenerationRequest(topic, seed, constraints)` and
  `GeneratedDeck(name, skillName?, items)` where each `GeneratedItem` carries
  `prompt`, `expectedAnswer`, `explanation?`, `mode?`, `confidence?`. Nullable
  result, matching the existing "no opinion" convention.
- **New persistence, additive only:** Room schema bump with `generation_runs` and
  `generated_items` (draft) tables; the practice tables are untouched.
- **Generation service:** chunked requests with bounded per-request timeout and
  cancellation, progress and partial results, reusing the existing BYOK
  `CoachConfiguration` transport.
- **Validation:** a structural validator (parseable schema, non-empty prompt and
  answer, item-count bounds, dedupe), analogous to `CoachOutputValidator`. No
  self-critique and no RAG.
- **Drafts-first lifecycle:** generated content is never written into the practice
  model directly. Per-item decisions (accept / edit / reject) are persisted on the
  draft rows; a single **atomic commit** then creates the `Skill`, `Deck`, and
  accepted `PracticeItem`s (each `ItemSchedule.newItem`) through the existing
  repositories, linking generation provenance.
- **Offline behavior:** with no configured remote coach, generation reports a
  typed "generation unavailable" outcome and routes the learner to the existing
  manual deck-creation flow - it is not a dead end and does not fabricate a deck.
- **Cost and caps:** an estimated token/cost is shown before a run; a cheaper
  model is the default; a hard per-run item cap (MVP: <= ~12).
- **Prompt versioning:** prompts are versioned constants; each run records the
  exact `promptVersion`; a guard test asserts the expected version set.
- **New UI:** a "Generate deck from topic" entry point, a review screen
  (per-item accept/edit/reject), and progress/partial-result display.

## Capabilities

### New Capabilities

- `ai-deck-generation`: generating a complete deck and its items from a free-form
  topic - the generation port and its contract, draft staging, structural
  validation, provenance, offline outcome, chunked/bounded generation, and prompt
  versioning. This is a cohesive boundary mirroring the port separation decided in
  the research (`CoachAgent` advisory vs `DeckGenerator` generating), so the five
  dependent features can extend it without bloating `ai-coach`.

### Modified Capabilities

- `skill-deck-management`: a deck and its items may originate from generated
  drafts; generated items enter the deck only after explicit acceptance and then
  behave exactly like manually created ones (same scheduling, active-deck, and
  progress semantics). Requirement-level behavior changes, so a delta spec is
  included.

Note: the research `design.md` sketched some of this as `ai-coach` additions; this
proposal instead places the generation contract in the dedicated
`ai-deck-generation` capability (the research marked that sketch as deferred and
to be settled here). `ai-coach` itself is unchanged.

## Impact

- **Domain:** new `core/domain/coach` port and contracts (`DeckGenerator`,
  `DeckGenerationRequest`, `GeneratedDeck`, `GeneratedItem`); no change to
  `CoachAgent`.
- **Data:** `core/data/db` new entities/DAOs and a schema migration; a draft
  repository/store behind a domain port.
- **Coach/data layer:** a new implementation over the existing BYOK transport
  (reusing the `HttpURLConnection` + `kotlinx-serialization` pattern); DI wiring in
  `CoachModule` (or a sibling module).
- **Feature/UI:** a new generation entry point and review surface in the decks
  feature.
- **Specs:** new `openspec/specs/ai-deck-generation/spec.md` and a delta to
  `skill-deck-management`.
- **CLI/tooling:** tests only (unit + instrumented), no build-time configuration.
