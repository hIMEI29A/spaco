# Tasks

## 1. Domain contracts

- [ ] 1.1 Add the `DeckGenerator` port with `DeckGenerationRequest`, `GeneratedDeck`, and `GeneratedItem` in `core/domain/coach`; verify it compiles and a unit test constructs the types and asserts nullable-result handling
- [ ] 1.2 Add a `GenerationDraftStore` port (observe/save drafts, record a run, set a draft decision, discard a run) in `core/domain/repository`; verify the interface compiles and an in-memory fake satisfies it in a unit test
- [ ] 1.3 Add a typed generation outcome (`Generated` / `Unavailable` / `PartialFailure`) for the use case to return; verify a unit test covers each branch

## 2. Persistence

- [ ] 2.1 Add `GenerationRunEntity` and `GeneratedItemEntity` (plus a `Converters` entry for the draft-decision enum); verify the exported Room schema JSON lists both tables with their columns
- [ ] 2.2 Bump `SpaCoDatabase` to version 2, register the entities and a `GenerationDao`; verify the build succeeds and the exported schema is version 2
- [ ] 2.3 Add an explicit `Migration(1, 2)` in `DatabaseModule` creating both tables, and add the DAO provider; verify a migration test opens a v1 database, migrates, and asserts pre-existing rows survive
- [ ] 2.4 Implement `GenerationDraftStore` over `GenerationDao`; verify DAO/unit tests cover save-draft, observe-pending, set-decision, and discard-run
- [ ] 2.5 Bind the new store in the Hilt modules; verify the app builds and the dependency graph resolves

## 3. Generation

- [ ] 3.1 Implement `LlmDeckGenerator` over `HttpURLConnection` + `kotlinx-serialization` (outline request and item-chunk requests), reusing `CoachConfiguration`; verify unit tests parse valid, malformed, and empty fixtures into the expected result or discard
- [ ] 3.2 Implement chunking and limits (outline first, item chunks of 5, total cap 12, per-request timeout, cooperative cancellation); verify unit tests cover a partial-success run and a timed-out request
- [ ] 3.3 Implement `DeckGenerationValidator` (structural: schema, non-empty prompt/answer, count bounds, near-duplicate prompts); verify unit tests cover each rejection case
- [ ] 3.4 Implement `RuntimeConfiguredDeckGenerator` composition that reads `CoachConfigurationStore` and returns the typed `Unavailable` outcome when no configuration exists or the endpoint cannot be reached; verify unit tests for both the configured and unconfigured paths
- [ ] 3.5 Implement the generation use case (orchestrate chunk -> validate -> persist drafts, emit progress); verify a unit test with a fake `DeckGenerator` persists drafts and reports progress, and persists nothing on `Unavailable`

## 4. Prompt versioning and cost

- [ ] 4.1 Add versioned prompt constants (e.g. `deck-gen@1`) and record `promptVersion` on each run; verify a guard test asserts the expected set of prompt versions
- [ ] 4.2 Implement the cost estimate (token heuristic from the item cap plus a local price table); verify unit tests cover the estimator near the cap
- [ ] 4.3 Enforce the per-run item cap in the use case; verify a unit test shows an over-cap response is clamped to the cap

## 5. Promotion

- [ ] 5.1 Implement the `PromoteGeneratedDeck` atomic transaction (get-or-create skill by name, create deck, insert accepted items with `Scheduler.newItemSchedule`, link provenance, mark drafts accepted with `acceptedItemId`); verify a Room/instrumented test asserts the deck and items are created and that a failing insert promotes nothing
- [ ] 5.2 Verify promoted items behave like manual ones: verify an integration test shows accepted items become eligible for practice, appear in due queries, and count toward progress

## 6. UI

- [ ] 6.1 Add the "Generate deck from topic" entry point and a generation route with explicit states (`Generating`/`Review`/`Committing`/`Done`/`Unavailable`); verify with a UI test or documented observable behavior
- [ ] 6.2 Implement the review screen with per-item accept, edit, and reject, persisting each decision; verify UI tests cover reject, edit-then-accept, and confirming the accepted set
- [ ] 6.3 Implement progress and partial-result display, and route the `Unavailable` state to manual deck creation; verify UI tests cover the partial and unavailable states
- [ ] 6.4 Show the estimated token/cost before sending a run; verify with a UI test or documented observable behavior

## 7. Integration verification

- [ ] 7.1 Add an end-to-end instrumented test: supply a topic, stage drafts over a loopback endpoint, accept a subset, and assert the resulting deck and its eligibility for practice
- [ ] 7.2 Run `./gradlew --offline test lint` and `:app:connectedDebugAndroidTest` on an emulator; verify all tests pass and lint reports no new warnings
- [ ] 7.3 Update repository docs (README "Remote coach"/feature notes) for the new generation flow; verify the documented steps match observable behavior

## Workflow follow-up

- Archive the change once review requirements are satisfied, syncing the new `ai-deck-generation` spec and the `skill-deck-management` delta.
- Verify the archived result with `openspec validate --specs`.
