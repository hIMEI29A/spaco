# Design

> **RESEARCH ONLY - BLOCKING.** This document is an investigation, not an
> implementation design. It contains no tasks and no spec deltas. See
> `proposal.md` for motivation and scope.

## Context

SpaCo is an offline-first Android app (Kotlin, Compose, Hilt, Room) whose
domain is `Skill -> Deck -> PracticeItem`. What exists today, and what constrains
generation:

- **The coach only varies existing content.** `CoachAgent` (domain port) exposes
  `selectNextItem`, `generateExercise`, and `generateExplanation`. `generateExercise`
  is anchored to a target that already exists: `ExerciseRequest.item` carries the
  item's identity and siblings, and `CoachOutputValidator.exercise` discards any
  result whose `targetItemId != request.item.id`. There is no call that produces
  a new entity.
- **No bulk or generative creation path.** `DeckRepository.create(skillId, name)`
  and `ItemRepository.add(deckId, prompt, expectedAnswer, ...)` each create one
  row, invoked by hand from the decks feature. A repo-wide search finds no
  `generateDeck`, no bulk insert, and no notion of a "topic".
- **Scheduling is derived, not stored as truth.** `PracticeItem.schedule` is a
  cache recomputed from the append-only `review_events` (`Scheduler.recompute`).
  A brand-new item starts `ItemSchedule.newItem` = due immediately. Editing an
  item's content deliberately leaves history and schedule untouched.
- **The coach is composed, replaceable, and bounded.** `CoachModule` builds
  `TimeoutCoachAgent(8s) -> RuntimeConfiguredCoachAgent -> FailoverCoachAgent(remote, offline)`.
  The remote is BYOK: `CoachConfiguration(endpoint, model, credential?)` is stored
  AES-GCM under a non-exportable Keystore key and read at request time. When no
  configuration is saved - or the endpoint has no usable answer - the request
  falls to `DeterministicCoachAgent`, which is fully offline and a *pure function*
  of its request.
- **Room is at schema version 1** (`skills`, `decks`, `practice_items`,
  `review_events`, `sessions`, `reminder_state`).
- **The offline fallback is a first-class participant.** The current design treats
  the offline coach as a complete substitute, so every request is answered
  identically-but-deterministically when remote is unavailable. Generation breaks
  this assumption (see Decision D6).

Issue #16 asks five questions (architecture, hallucination risk, tokens/cost/
latency, runtime/resources, feasibility) about one capability: **create a
complete skill deck from a free-form topic.** This document answers them.

## Goals / Non-Goals

**Goals:**

- Define the generation flow and the exact UI / Room / coach contract that
  supports "topic in, complete deck out".
- Define how generated content is validated and persisted so it integrates with
  the existing `skill -> deck -> item` model and the derived schedule without
  disturbing history or the active deck.
- Identify the new entities/metadata generation needs (provenance, confidence,
  review decision).
- Bound hallucination/data-quality risk and define how uncertainty reaches the
  learner.
- Quantify token usage, cost, and latency, and propose UX patterns that keep the
  experience acceptable.
- Give a go/no-go recommendation, an MVP scope, and the architectural decisions
  that #7-#11 must respect.

**Non-Goals:**

- No implementation, no tasks, no spec delta files (this change sets
  `skip_specs: true`).
- Not designing the five evolution features themselves; only the generation
  contract they will build on.
- Not choosing a specific model or vendor; not building retrieval infrastructure.
- Not changing existing coach behavior for item-anchored calls
  (`selectNextItem` / `generateExercise` / `generateExplanation` stay as they are).

## Decisions

### D1 - Generation is a separate port, not an extension of `CoachAgent`

Add a dedicated domain port (working name `DeckGenerator`) alongside `CoachAgent`
rather than widening `CoachAgent`.

```
  CoachAgent (unchanged)              DeckGenerator (new)
  ---------------------------         ------------------------------------------
  advisory, item-anchored             entity-creating, topic-anchored
  returns "opinion or null"           returns a structured deck or null
  target-match validation             structural + review-gate validation
  on the critical session path        off the critical session path
```

**Rationale:** the two have incompatible lifecycles. `CoachAgent` calls are on
the session's critical path and must be cheap and bounded (8s overall). Deck
generation is long, may be backgrounded, and creates rows. Merging them would
force the session path to inherit generation's latency and failure modes.

**Alternatives considered:** (a) add `generateDeck` to `CoachAgent` - rejected:
breaks the "advisory, item-anchored" invariant and drags entity creation into the
session timeout envelope; (b) put generation in a repository/feature layer with no
port - rejected: coupling the feature to a concrete LLM implementation, breaking
the replaceability the `ai-coach` spec already promises.

### D2 - Drafts first, never direct persistence (confirmed: option (a))

Nothing the generator produces is written into `skills`/`decks`/`practice_items`
until the learner explicitly accepts it. Generated output is staged as draft rows
holding the full proposed content plus provenance; acceptance promotes drafts into
the practice model (creating the `Skill`/`Deck` and `PracticeItem` rows, each with
`ItemSchedule.newItem`).

```
 topic --> [ generate ] --> draft(s) + provenance --> [ user review ] --accept--> Skill/Deck/Items
                                     |                        |                         (newItem schedule)
                                     |                        +--reject/edit--> discard or amend
                                     |
                             never touches practice tables,
                             never enters scheduling, never appears as "due"
```

**Rationale:** there is no target-item anchor to validate against, so the human is
the trust boundary. Drafts also give provenance a natural home and make "review
before it counts" the default, which matches the offline-first, no-surprise ethos.

**Alternatives considered:** (b) insert items immediately marked "unreviewed" -
rejected: an unreviewed, possibly-wrong card would enter due counts and sessions;
(c) auto-accept above a confidence threshold - rejected as the default: confidence
is a model self-report and is not trustworthy enough to bypass review on the
offline-first path. Threshold-based auto-accept is noted as a possible *opt-in*
refinement, not the default.

### D2b - Acceptance is a single atomic commit (confirmed: commit-on-finish)

Review decisions are made per item and persisted on the draft rows as they are
made; the *promotion* into the practice model happens once, at the end, in a
single transaction.

```
 generate --> drafts (persist as produced)
                  |
           review screen: per item -> accept / edit / reject
                  |            (decision persisted on the draft row)
                  v
            [Create deck]  <-- one transaction
                  |
    get-or-create Skill by name (SkillRepository.getByName/create)
    create Deck (DeckRepository.create)
    for each accepted draft: insert PracticeItem(newItem schedule)
                             mark draft accepted, set acceptedItemId
```

**Rationale:** per-item decisions are inherently personal (D2), so promoting
incrementally would produce half-built decks and create `Skill`/`Deck` rows even
when the learner abandons the flow. Drafts and their `decision` already persist
during generation, so a crash loses no review state; the materialization itself is
one atomic step. It reuses the existing repositories, so the result is
indistinguishable from manual creation.

**Alternatives considered:** incremental promotion (each accept immediately
inserts an item into a lazily created deck) - rejected: partial decks and orphan
`Skill`/`Deck` rows on abandonment, with no atomicity.

### D3 - Off-the-critical-path, backgrounded generation

Generation runs as its own operation (a `viewModelScope` job for a small
foreground case, promoted to WorkManager for the general case), decoupled from
practice sessions. A session never waits on generation.

**Rationale:** a useful deck is thousands of output tokens; at realistic
throughput that is tens of seconds (see the latency budget in D8). Nothing about
practising must depend on it.

**Alternatives considered:** synchronous blocking dialog - rejected on latency;
streaming into the UI - partial-fit: streaming helps *perceived* latency but the
output is structured JSON, which is awkward to stream card-by-card, and the drafts
still need a review step. Streaming is therefore a UX enhancement layered on the
background flow, not the flow itself.

### D4 - Chunked generation (batch of items), not one giant completion

Generate in bounded chunks (for example, 5 items per request) with a deck-level
name/outline request first, or a single request with a bounded item count.

**Rationale:** bounds per-request latency and output size, tolerates partial
failure (some chunks succeed), and lets the review UI start showing results before
the whole deck is done. Also keeps each completion inside typical model output
limits. Chunk size is chosen so the first chunk lands inside the first-result
budget in D8.

**Alternatives considered:** one completion for 40 items - rejected: high latency,
single point of failure, and more truncation risk.

### D5 - New entities: generation run + draft items with provenance

Two additions (sketched; final shape is a follow-up implementation decision):

```
  generation_runs                         generated_items (draft)
  ------------------------------          -------------------------------------
  id, topic, endpointId?, model,          id, runId, prompt, expectedAnswer,
  promptVersion, startedAt, finishedAt,   explanation?, mode?, confidence?,
  status, errorKind?, itemCount           createdAt, decision (pending/accepted/
                                          rejected/edited), acceptedItemId?
```

Provenance is persisted on the run and carried onto accepted items (at minimum,
a reference to the run) so a learner can later see where a card came from.

**Rationale:** "generation provenance, confidence, topic" (issue #16,
"Architectural Design") has no home in the current schema; separate tables keep
drafts out of the practice model entirely (D2) and make audit/history for #7
(Living Decks mutation history) a natural extension.

**Alternatives considered:** a `status` column on `practice_items` - rejected: it
leaks draft rows into every existing query (`observeDueItems`, counts, progress)
and complicates the append-only invariant; a separate table is cleaner and keeps
practice queries untouched.

### D6 - Offline fallback for generation is a typed outcome, not a fake deck (confirmed)

The offline path cannot synthesize a genuine deck: `DeterministicCoachAgent` can
only transform content it is given. When remote is unavailable, generation reports
a clear, typed outcome ("generation needs a configured remote coach") and offers a
link into the existing manual deck-creation flow, which already works offline.

**Rationale:** the current spec promises "the coach works without network access"
for *selection/exercise/explanation*; those remain true. Deck generation is a new
behavior, and pretending a template skeleton of empty placeholders is equivalent
would be dishonest to the learner. Confirmed choice: the typed outcome (not a
skeleton) is the default, and it is not a dead end because it routes to manual
creation.

**Alternatives considered:** (a) a clearly-labelled skeleton of empty/placeholder
items the learner fills in - rejected as the default: low value and confusable
with real generated content; retained only as a possible explicit convenience.
(b) reuse `DeterministicCoachAgent` as the offline generator - rejected: it has
nothing to generate *from*; any "generated" items would be meaningless, which is
exactly the hallucination-quality failure this research is meant to prevent,
merely self-inflicted.

### D7 - Validation = structural + optional self-critique + human gate

Because target-match validation (D1) does not apply, validation becomes layered:

1. **Structural (always):** parseable schema; non-empty prompt and expected
   answer; count within bounds; de-duplicate near-identical prompts; enforce the
   "prompt/answer/explanation" shape the item model already requires.
2. **Self-critique (optional, remote):** a second pass asking the model to flag or
   drop items that are unsupported, contradictory, or off-topic. Doubles cost.
3. **Human review gate (always, D2):** the learner accepts, edits, or rejects each
   item. This is the only layer that actually guarantees correctness, because it
   is the only one outside the model's own judgment.

**Rationale:** layered defense matches the existing philosophy (`CoachOutputValidator`
already centralizes validation), and the human gate is the layer that is robust to
model failure.

**Alternatives considered:** confidence thresholds alone - rejected as a sole
mechanism (D2); RAG/grounding - rejected for now (see D10): valuable for
factual accuracy but requires a retrieval corpus and is out of scope.

### D8 - Bounded timeouts and a latency/progress contract (confirmed budget)

Concrete budget (confirmed):

- First result (deck name + first ~5 items): **<= ~10 s** to appear on screen.
- Full MVP deck (<= 12 items): **<= ~60 s**, in the background, with progress and
  partial results shown as chunks arrive.
- Per-request hard timeout **~30 s**, then cancel and retry that chunk; the whole
  run is bounded by an overall budget.

Generation requests are cancellable (matching the existing `TimeoutCoachAgent`
pattern). The UI shows progress and partial results; the common case is a small
foreground job, promoted to WorkManager plus a notification for long/backgrounded
runs.

**Rationale:** a single batch of ~20 cards is ~2.5-4k output tokens, i.e. roughly
45-90 s at 30-60 tok/s - unacceptable on a blocking screen. Chunking (D4) plus a
background job deliver "first result fast, whole deck within a minute", and the UI
must never look hung.

**Alternatives considered:** no timeout - rejected: an unbounded network call on
mobile is a battery and UX hazard. A synchronous blocking dialog - rejected on the
latency above.

### D9 - Cost is visible and generation is capped (confirmed)

Cost is accepted as acceptable for BYOK (illustrative: ~800-1500 input + ~2.7k
output tokens; ~$0.002/deck on small models, ~$0.027/deck on mid models under
representative OpenAI-compatible pricing). The design therefore does not gate on
cost, but: it surfaces an estimated token/cost before a run, defaults to a cheaper
model, and enforces a hard per-run item cap (MVP: <= 12).

**Rationale:** on BYOK the learner pays their own endpoint, so cost is theirs and
visible; the cap also serves the anti-churn risk below. A cost gate would add
friction for a sum that rounds to nothing.

**Alternatives considered:** hard cost gate - rejected: unnecessary friction for
sub-cent sums; per-run caps and an estimate cover the real risk (churn).

### D10 - No retrieval/grounding; topic caching deferred (confirmed)

RAG/grounding is out of scope (and expected to stay out unless a specific
factual-accuracy requirement appears). Topic caching (keyed by normalized topic +
model + prompt version -> a prior run) is deferred as an optimization.

**Rationale:** RAG needs a corpus plus an embeddings store and adds significant
infrastructure to an offline-first pet project; the human review gate (D7.3) is
the MVP's correctness mechanism. Caching is pure optimization, not a foundation.

**Alternatives considered:** on-device embeddings - rejected: heavy footprint and
no corpus to embed; remote retrieval service - rejected: contradicts the BYOK,
minimal-dependency posture.

### D11 - Prompts are versioned strings recorded on each run (confirmed)

Prompts live in the repository as named constants with a version string (e.g.
`deck-gen@1`). Each run records the exact `promptVersion` on
`generation_runs.promptVersion`, so a run is reproducible and comparable. A guard
test asserts the expected set of prompt versions to catch a forgotten bump. No
prompt database is needed for the MVP.

**Rationale:** the repository already is the source of prompts; a version string
is the minimal addition that makes runs traceable. Least machinery, real
reproducibility.

**Alternatives considered:** a prompts table / runtime prompt management -
rejected: over-engineered for a client app whose prompts ship with the code.

## Risks / Trade-offs

- **[Hallucinated, confident-but-wrong cards poison a deck]** -> Human review gate
  (D2/D7) is mandatory; optional self-critique; provenance lets a learner trace and
  bulk-reject a bad run.
- **[Latency makes the feature feel broken]** -> Off-critical-path background
  generation with progress and partial results (D3/D8); chunking (D4).
- **[Cost surprises the user on BYOK]** -> Provider is the learner's own endpoint,
  so cost is theirs and visible; chunking bounds per-request size; an estimated
  token cost is surfaced before a run and a per-run cap applies (D9).
- **[Room migration risk]** -> Draft tables are additive; the practice tables are
  untouched, so the migration is low-risk (see Migration Plan).
- **[Offline expectations]** -> D6 keeps the "works offline" promise honest by
  scoping it to selection/exercise/explanation and giving generation a typed
  offline outcome.
- **[Scope creep into five features]** -> This document fixes the *contract* only;
  shaping trees, modes, and campaigns is explicitly deferred.
- **[Draft spam / regeneration churn]** -> Bounded item counts per run and
  per-topic run history; "regenerate" is a new run, old drafts remain reviewable.

## Migration Plan

Additive and low-risk:

1. Bump Room schema version; add `generation_runs` and `generated_items` (D5).
   No changes to existing tables or their queries; existing rows are untouched.
2. Acceptance path writes `Skill`/`Deck`/`PracticeItem` rows exactly as the manual
   path does (via the existing repositories), so scheduling, active-deck
   selection, and progress continue to behave identically.
3. Rollback: drop the two new tables (or simply stop using them); the practice
   data model is unchanged, so rollback loses only pending drafts and run history.

No data backfill is required.

## Feasibility & Recommendation (issue #16, "Overall Feasibility & Recommendation")

**GO, with conditions.** Generation is implementable within the offline-first
architecture, provided it stays off the session-critical path, stages results as
drafts, and treats offline as a typed outcome rather than a fake success. None of
these conflict with existing specs; they extend the coach with a new port.

**Recommended MVP scope:**

- Topic -> one deck (bounded to ~10-12 items) -> drafts -> review (accept/edit/
  reject per item) -> one atomic commit (D2b).
- Remote-only generation; offline returns a clear "configure a remote coach"
  outcome with a route to manual creation (D6).
- Structural validation only (D7.1); no self-critique, no RAG (D10).
- Chunked generation with progress (D4/D8); foreground job; estimated cost and a
  per-run cap (D9).

**Deferred enhancements:** skill trees (#8), multi-mode items (#9), campaigns
(#10), deck mutation history / living decks (#7), annotations as generation input
(#11), self-critique, topic caching/dedup, confidence-based auto-accept.

### Architectural decisions later work MUST respect

1. Generation is a **separate port** from advisory `CoachAgent`; the latter stays
   item-anchored and unchanged.
2. Generated content is always **staged as drafts**; the practice model is never
   written directly, and drafts never enter scheduling or due counts.
3. **Provenance is first-class** (run: topic, model, endpoint id, prompt version,
   time; item: run reference, decision, confidence).
4. **Acceptance reuses the existing repositories** so scheduling/history/active
   deck semantics are identical to manual creation.
5. **Offline generation is a typed outcome**, not a silently degraded deck.
6. **Validation is layered** and the human review gate is mandatory; target-match
   validation does not apply to generation.
7. Generation runs **off the practice-session critical path** with bounded
   timeouts.
8. **Acceptance is a single atomic commit** (`Skill`/`Deck`/`PracticeItem`),
   reusing the existing repositories so the result is indistinguishable from
   manual creation.
9. **Cost is estimated and capped**, and **prompts are versioned** with the version
   recorded on each run.

## Delta-spec Sketches (deferred - no files created)

These are sketches only; they would become real delta specs in a future, approved
change. They are recorded here per issue #16 ("required delta-spec sketches").

**`ai-coach` (prospective additions):**

- *Coach can generate a complete deck from a topic* - given a free-form topic and
  bounds, the coach returns a deck name and a set of items (prompt, expected
  answer, optional explanation); output is validated structurally and MUST be
  attributable to the run. Scenario: valid topic yields a bounded item set;
  malformed/empty output is discarded.
- *Generated content is staged, not persisted* - the system SHALL NOT write
  generated items into the practice model until the learner accepts them.
- *Generation without a remote coach* - WHEN no remote coach is configured, the
  system reports that generation is unavailable rather than producing a
  placeholder deck, and routes the learner to manual deck creation (D6).

**`skill-deck-management` (prospective additions):**

- *Learner can create a deck from a topic* - learner supplies a topic; the system
  produces draft items; on acceptance the deck and its items are persisted under a
  skill **in a single commit** and behave like manually created ones (same
  scheduling, active-deck, and progress semantics).
- *Learner reviews generated items before they count* - each draft can be
  accepted, edited, or rejected; only accepted items enter the deck and only then
  become eligible for practice.

## Open Questions

None remain for this research. The questions raised during exploration (Q1-Q6)
are now resolved decisions:

| Was | Now |
|-----|-----|
| Q1 - offline default | **D6** - typed "generation unavailable" outcome + route to manual creation |
| Q2 - acceptance atomicity | **D2b** - single atomic commit on finish |
| Q3 - latency budget | **D8** - <= ~10 s first result, <= ~60 s full deck, ~30 s per-request timeout |
| Q4 - cost ceiling | **D9** - cost accepted; estimate shown, cheaper model default, per-run cap |
| Q5 - retrieval/grounding | **D10** - RAG out of scope; topic caching deferred |
| Q6 - prompt/versioning | **D11** - versioned prompt constants recorded on each run |

Remaining unknowns are implementation details of a future, approved change (for
example, exact chunk size tuning and the opt-in confidence auto-accept noted in
D2); none of them changes the ports, the specs, or the approach above.
