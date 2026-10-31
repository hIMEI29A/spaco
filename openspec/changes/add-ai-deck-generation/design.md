# Design

## Context

See `proposal.md` for motivation and the research-only change
`openspec/changes/ai-generated-decks-research/` (issue #16) for the contract
decisions this change implements (referenced below as "research D…"). Relevant
current state:

- **Coach composition** (`core/data/coach/CoachModule.kt`): `TimeoutCoachAgent(8s)`
  -> `RuntimeConfiguredCoachAgent` -> `FailoverCoachAgent(remote, offline)`. The
  remote is BYOK, built per request from `CoachConfigurationStore`
  (`CoachConfiguration(endpoint, model, credential?)`); `LlmCoachAgent` speaks
  `kotlinx-serialization` JSON over `HttpURLConnection`.
- **Validation** is centralized in `CoachOutputValidator` (item-anchored).
- **Data** (`core/data/db`): Room v1 (`skills`, `decks`, `practice_items`,
  `review_events`, `sessions`, `reminder_state`). `DatabaseModule` explicitly
  expects later schema changes to add `Migration`s there.
- **Ambient state** is injectable (`Clock`, `IdGenerator`,
  `SchedulingParameters` in `CoreModule`), so new code must not reach for a clock
  or a random directly.
- **Item creation** goes through `ItemRepository.add`, which sets
  `Scheduler.newItemSchedule(createdAt)`; skills via `SkillRepository.getByName`
  (case-sensitive) and `create`; decks via `DeckRepository.create`.

## Goals / Non-Goals

**Goals:**

- Implement the research MVP: topic -> one flat deck, remote-only, drafts-first,
  with the review gate and a single atomic promotion.
- Integrate with the existing skill/deck/item model and scheduling without
  disturbing history or the active deck.
- Keep generation a separate, replaceable port from the advisory practice coach.
- Add the two provenance tables additively, with an explicit migration.

**Non-Goals:**

- Multi-mode item grading, deck mutation, trees, campaigns, annotations (#7-#11).
- Self-critique loops, RAG/grounding, embedding storage.
- Changing `CoachAgent` or the practice-session path.
- Prompt management UI; prompts stay versioned constants in the code.

## Decisions

### D1 - A dedicated `DeckGenerator` port, shared BYOK transport

Add `core/domain/coach/DeckGenerator` with `generateDeck(request):
GeneratedDeck?` and its request/response types (research D1). Implement
`LlmDeckGenerator(configuration, ...)` reusing the existing
`HttpURLConnection` + `kotlinx-serialization` pattern and the same bounded-timeout
+ `withTimeoutOrNull` approach as `LlmCoachAgent`. Compose behind a
`RuntimeConfiguredDeckGenerator` that reads `CoachConfigurationStore` and, when no
configuration exists, returns the typed "unavailable" outcome (research D6).

**Rationale:** mirrors the port separation the research fixed; reuses the proven
transport and the BYOK configuration path, so there is one place the credential
lives.

**Alternatives considered:** extend `CoachAgent` with `generateDeck` - rejected
(research D1: mixes lifecycles); a second HTTP stack - rejected (duplicate crypto,
config, and error handling).

### D2 - Two provenance tables, explicit additive migration

Add `GenerationRunEntity` (`id`, `topic`, `endpointId?`, `model`, `promptVersion`,
`startedAt`, `finishedAt?`, `status`, `errorKind?`, `itemCount`) and
`GeneratedItemEntity` (`id`, `runId`, `prompt`, `expectedAnswer`, `explanation?`,
`mode?`, `confidence?`, `createdAt`, `decision`, `acceptedItemId?`). Bump Room to
v2 with an explicit `Migration(1, 2)` creating both tables (research D5). Expose
them behind a domain port (working name `GenerationDraftStore`).

**Rationale:** keeps drafts entirely out of the practice model, so existing
queries (`observeDueItems`, counts, progress) are untouched; `DatabaseModule`
already expects migrations to be added there.

**Alternatives considered:** a `status` column on `practice_items` - rejected
(research D5: leaks drafts into every practice query); storing drafts as JSON -
rejected (loses per-draft review state and provenance queryability).

### D3 - Outline first, then bounded item chunks

One outline request returns the deck name, the optional skill name, and an item
plan; item content is then requested in chunks of ~5, with a total cap of <= ~12
(research D4/D8).

**Rationale:** bounds per-request latency and output size, allows partial success,
and lets the review UI populate before the whole deck is done.

**Alternatives considered:** a single completion for the whole deck - rejected
(research D4: latency, single point of failure, truncation risk).

### D4 - Structural validation for generation

Add a `DeckGenerationValidator` (data/coach) analogous to `CoachOutputValidator`:
schema-parsable output, non-empty prompt and answer, count within bounds,
near-duplicate prompt rejection. Discard invalid output rather than surfacing it.

**Rationale:** centralized validation matches the existing philosophy; the
human review gate (D6) is the correctness layer, so validation here is purely
structural (research D7.1).

**Alternatives considered:** model self-critique - deferred (non-goal).

### D5 - Draft decisions are per item; promotion is one transaction

Each draft carries `decision` (`pending`/`accepted`/`rejected`) and, when edited,
the edited content, persisted as the learner acts. Confirming the review runs a
single Room transaction (working name `PromoteGeneratedDeck`): get-or-create the
`Skill` by name, create the `Deck`, insert each accepted item with
`Scheduler.newItemSchedule(createdAt)` and a run reference, and mark each draft
accepted with its `acceptedItemId`.

**Rationale:** reuses `SkillRepository`/`DeckRepository`/`ItemRepository` semantics,
so generated decks are indistinguishable from manual ones; the single transaction
gives all-or-nothing promotion (research D2b).

**Alternatives considered:** incremental promotion - rejected (research D2b:
partial decks, orphan skills/decks).

### D6 - Review gate is the trust boundary

Nothing reaches `practice_items` without an explicit accept. The review surface
lets the learner accept, edit, or reject each draft; only accepted drafts are
promoted.

**Rationale:** with no target-item anchor, the human is the only layer robust to
model failure (research D2/D7.3).

**Alternatives considered:** confidence auto-accept - rejected as default
(research D2).

### D7 - Generation UI: entry point, progress, review, typed outcomes

Add a "Generate deck from topic" entry point in the decks feature and a generation
route with explicit states: `Idle` -> `Generating` (progress, partial results) ->
`Review` -> `Committing` -> `Done`, plus `Unavailable` (no reachable remote) that
routes to manual creation. A foreground coroutine job for the common case,
promoted to WorkManager if it must outlive the screen.

**Rationale:** the research latency budget (research D8) requires progress and
partial results; the typed `Unavailable` state keeps the offline promise honest
(research D6) and is not a dead end.

**Alternatives considered:** a blocking dialog - rejected (research D8).

### D8 - Versioned prompts with a guard test

Prompts are named constants carrying a version string (e.g. `deck-gen@1`); each run
records `promptVersion`. A test asserts the expected set of versions.

**Rationale:** minimal machinery for reproducible, comparable runs
(research D11).

**Alternatives considered:** a prompts table - rejected (research D11).

### D9 - Cost estimate is a client-side heuristic

Before a run, estimate tokens from the item cap and show an approximate cost from
a small, local price table; enforce the cap. No hard cost gate.

**Rationale:** on BYOK the sum is trivial (research D9); an estimate plus a cap
address the real risk (churn), without adding friction.

**Alternatives considered:** hard cost gate - rejected (research D9).

## Risks / Trade-offs

- **[Latency makes generation feel broken]** -> Chunked + backgrounded with
  progress and partial results (D3/D7).
- **[Hallucinated cards]** -> Mandatory review gate (D6); structural validation
  (D4); provenance for bulk-reject.
- **[Room migration breaks existing data]** -> Additive migration only (D2); a
  migration test; practice tables and queries unchanged.
- **[Partial chunk failure leaves a thin deck]** -> Partial outcome surfaced; the
  learner accepts what arrived or retries.
- **[Cost floats with the learner's provider]** -> Estimate + cap (D9).
- **[Drafts accumulate]** -> Per-run bounded item count; a run record makes stale
  drafts reviewable/discardable.

## Migration Plan

1. Bump Room to v2; add `Migration(1, 2)` creating `generation_runs` and
   `generated_items`. No changes to existing tables.
2. Add a migration test (open v1, migrate, assert both tables exist and existing
   rows survive).
3. Promotion writes `Skill`/`Deck`/`PracticeItem` through the existing
   repositories, so scheduling/active-deck/progress behavior is unchanged.
4. Rollback: drop the two new tables; only pending drafts and run history are
   lost. No backfill required.

## Open Questions

- **Exact defaults** for chunk size and item cap (working values ~5 and ~12) -
  tunable without changing the contract.
- **Regenerate** semantics for an existing run/topic - defer to a follow-up unless
  trivial during implementation.
- **`confidence` usage** - stored in the MVP but not acted on; decide later whether
  the UI surfaces it.
- **WorkManager escalation** - whether long runs need a worker in addition to the
  foreground job, or the foreground job is sufficient for the MVP.
