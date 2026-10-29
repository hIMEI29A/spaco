# Design

## Context

SpaCo is a greenfield Android application. The repository currently contains only the OpenSpec scaffold, `docs/PROMPT.md`, and a `README.md`; there is no source code, no Gradle build, and no spec beyond the deltas in this change (see `proposal.md` for motivation and `specs/**` for requirements).

The approach is shaped by four constraints:

1. **Offline-first.** `offline-review-scheduling` requires every core flow to work with no network, and `ai-coach` requires coach output offline. There is no server and no cloud sync in this change.
2. **A pluggable coach.** `ai-coach` requires the coach to be replaceable without changing session or scheduling behavior, and to degrade safely when absent. The MVP ships a deterministic local implementation.
3. **Deterministic scheduling.** `spaced-repetition-scheduling` requires state to be reproducible from review history, so scheduling must be a pure function of history plus explicit inputs.
4. **The existing machine's toolchain.** Verified on this workstation:

| Component | State |
|---|---|
| JDK | ✅ Zulu 17.0.21 (`java`, `javac`) |
| Kotlin CLI | ✅ 2.4.21 (not used by the build; AGP drives the Kotlin compiler) |
| Android SDK | ✅ `/opt/homebrew/share/android-commandlinetools` (`ANDROID_HOME`) with cmdline-tools `latest`, platform-tools 37.0.1, emulator 37.2.12 |
| System image / AVD | ✅ `system-images;android-36;google_apis;arm64-v8a`, AVD `OpenTUI_Pixel_8_API_36` |
| `android` CLI | ✅ `/opt/homebrew/bin/android` (the replacement for the deprecated `sdkmanager`) |
| **Gradle** | ✅ 9.8.2 (Homebrew) — at or above AGP 9.5.0's required Gradle 9.6.0; the project still needs its own committed wrapper |
| **SDK platform** | ✅ `platforms/android-36` and `platforms/android-37.2` (initially absent; installed as the first step of this change) |
| **SDK build-tools** | ✅ `build-tools/36.0.0` and `build-tools/37.0.0` (AGP 9.5.0 requires `build-tools` ≥ 36.0.0) |
| Android Studio | ❌ not installed; not required (CLI + emulator is sufficient) |

## Goals / Non-Goals

**Goals:**

- A single Android application module that builds, installs, and runs on the existing AVD via the command line.
- A coach boundary that a future LLM-backed implementation can satisfy with no change to session flow, review recording, or scheduling.
- A scheduling core that is a pure, unit-testable function of review history.
- Persistence that is the single source of truth for decks, items, review history, and in-progress session state.

**Non-Goals:**

- Offline **media** handling (audio, images, downloads). Deliberately deferred; see *Decisions → Media strategy is deferred behind an unwritten seam*.
- Any real LLM or provider integration, API-key handling, or network transport in this change.
- Multi-skill curricula, collaborative study groups, and export into an external learning-agent system (proposal growth path).
- Multi-module Gradle structure, feature-module dynamic delivery, and analytics/telemetry.
- Cloud sync or account systems.

## Decisions

### Stack and build configuration

Kotlin with Jetpack Compose and Material 3, built by AGP on a committed Gradle wrapper.

- **`compileSdk` = 37 (minor 2), `targetSdk` = 36, `minSdk` = 26.** The original plan was `compileSdk 36`, but current AndroidX libraries (`core-ktx 1.19.x`, Compose `1.12.x`) declare `minCompileSdk = 37`, and AGP 9.5.0 supports API levels up to 37, so the build compiles against `android-37.2`. `targetSdk` deliberately stays at 36: the runtime behavior tested here is the behavior of the API 36 system image and AVD this project targets, and `compileSdk` and `targetSdk` are independent knobs. `minSdk 26` keeps `java.time` and adaptive icons available without desugaring.
- **JDK 17 toolchain** — matches the installed Zulu 17 and AGP's supported range.
- **Version catalog** (`gradle/libs.versions.toml`) for all versions, so the toolchain is pinned in one file.
- **Kotlin DSL** build scripts.
- **AGP 9.5.0 on Gradle 9.8.2, pinned.** AGP 9.5.0 (stable, October 2026) requires Gradle ≥ 9.6.0, `build-tools` ≥ 36.0.0, and JDK 17, and supports API levels up to 37 — all satisfied here, so `compileSdk 36` sits inside the supported window rather than on its edge. Both versions are pinned rather than floated so the combination cannot drift.
- **Gradle wrapper committed to the repo, pinned to 9.8.2.** The wrapper (and its `gradle-wrapper.jar`) is what makes the build reproducible and independent of whatever Gradle a machine happens to have, and it pins the version for CI. It is generated once with the now-installed Gradle 9.8.2 (`gradle wrapper --gradle-version 9.8.2`) and then committed. **Alternative considered:** pinning to AGP 9.5.0's *default* Gradle 9.6.0 — rejected because 9.8.2 is already installed here, so matching it avoids downloading a second distribution while still clearing AGP's minimum.

Libraries: Compose BOM + Material 3, `androidx.navigation:navigation-compose`, Room + KSP, WorkManager, `androidx.lifecycle` (ViewModel, `collectAsStateWithLifecycle`), Kotlin coroutines/`Flow`, Hilt for dependency injection, `kotlinx-datetime` only if `java.time` proves awkward.

**Alternatives considered:** a multi-module layout (`:core:domain`, `:core:data`, `:feature:*`, `:coach`) — rejected for the MVP as build-configuration overhead that does not pay off at this size; the package layout below is shaped so the same boundaries can be extracted into modules later. Manual dependency injection instead of Hilt — rejected because Hilt's `@Binds` is exactly the mechanism that makes the coach implementation swappable in one place, and it keeps ViewModels constructor-injectable for tests.

### Package and source structure

One `:app` module, package-by-feature with an explicit inner layering:

```
app/src/main/java/<appId>/
  SpaCoApplication.kt              # Hilt + WorkManager configuration
  MainActivity.kt
  core/
    domain/                        # pure models + ports, no Android imports
      model/                       # Skill, Deck, PracticeItem, ReviewEvent, LearningState, SessionState
      scheduling/                  # Scheduler (pure functions), SchedulingParameters
      coach/                       # CoachAgent port + request/response types
      repository/                  # SkillRepository, DeckRepository, ItemRepository,
                                   #   ReviewRepository, SessionRepository, ProgressRepository
    data/                          # Room + repository implementations, Hilt modules
      db/                          # SpaCoDatabase, entities, DAOs, converters, migrations
      repository/                  # repo impls mapping entities <-> domain models
      coach/                       # DeterministicCoachAgent, CoachOutputValidator, TimeoutCoachAgent
      work/                        # DueReviewWorker, ReviewReminderScheduler, Notifications
    ui/                            # theme, shared components, custom animation primitives
  feature/
    decks/                         # deck list, deck detail, item editor
    session/                       # session host, item card, feedback, summary
    progress/                      # deck summary, accuracy, streak
    settings/                      # reminder toggle, notification-permission state
```

The rule that makes the coach pluggable enforceable: **`core/domain` may not import anything Android-specific**, and `core/domain/coach` declares only interfaces plus plain data. `feature/session` depends on the port, never on `DeterministicCoachAgent`.

### Persistence: entities, DAOs, and what is derived

Room is the single source of truth. Review history is authoritative; scheduling state on the item is a materialized cache.

**Entities:**

- `SkillEntity(id, name, createdAt, archivedAt?)`
- `DeckEntity(id, skillId → Skill, name, createdAt, archivedAt?)`
- `PracticeItemEntity(id, deckId → Deck, prompt, expectedAnswer, explanation?, createdAt, archivedAt?, nextDueAt, learningState, intervalIndex, correctStreak)` — the last four columns are **derived** from review history; they are a cache, not the source of truth.
- `ReviewEventEntity(id, itemId → PracticeItem, answeredAt, correct, gradedBy (SYSTEM|LEARNER), latencyMs?, promptVariant?)` — append-only.
- `SessionStateEntity(id, deckId, startedAt, itemBudget, answeredCount, correctCount, endedAt?, status (ACTIVE|ENDED|DISCARDED), plannedItemIds, nextItemId?)` — persists in-progress sessions for the resume requirement.
- `ReminderStateEntity(id, lastNotifiedDueSignature, lastNotifiedAt)` — supports not repeating a notification for the same due set.

**DAOs:** `SkillDao`, `DeckDao`, `ItemDao` (including `observeDueItems(deckId, now)` ordered by `nextDueAt ASC, id ASC` for the deterministic due order), `ReviewDao` (`insert`, `observeForItem`, `observeInWindow(deckId, since)`, `observePracticeDays`), `SessionDao`, `ReminderDao`. All reads return `Flow` so the UI and progress views update reactively without a restart.

**Why a derived cache and not a computed-on-read schedule:** the due-item query and the progress counts need to be indexable and cheap, and `WorkManager` must find the earliest `nextDueAt` without scanning every event. Correctness is protected by making the cache recomputable: one pure function writes it, and a rebuild path recomputes all items from history. Timestamps are stored as UTC epoch millis; the "practice day" boundary used by streaks is computed in the device's local zone at read time.

**Alternatives considered:** deriving state purely on read (simpler invariants, slower queries, awkward for background work) and storing only the latest state per item (loses the history the coach and progress views need). A materialized cache with a single writer plus a recompute path is the middle ground.

### Scheduling algorithm

A simplified SM-2-family algorithm, expressed as pure functions in `core/domain/scheduling`:

- `applyReview(state, parameters, grade, answeredAt) -> newState` — no clock access; `answeredAt` is an explicit input.
- Correct answers grow the interval by a fixed factor per state, capped by `SchedulingParameters.maximumInterval`; successive correct answers produce strictly growing intervals (required by the spec).
- Incorrect answers set the state to lapsed/learning and set `nextDueAt` within the current day, so the item reappears soon.
- All defaults (`initialInterval`, `growthFactor`, `maximumInterval`, `newItemCap`) live in `SchedulingParameters` with sane constants.

Because the function is pure and history is append-only, `recompute(history) == storedCache` holds; this is the property a unit test asserts directly.

### Compose UI structure and custom animation

Navigation Compose hosts four destinations: deck list → deck detail (with the item editor as a dialog or nested route) → session → summary, plus a progress destination and a settings destination. State flows one way: `ViewModel` exposes an immutable `StateFlow<UiState>`; composables are stateless and receive slots.

Custom animations, in `ui/animation`, are the deliberate visual signature of the app:

- **Session item transition** — `AnimatedContent` in the session host with a custom `ContentTransform` (slide + fade, direction-aware on "next" vs "back"), so consecutive items feel like cards moving through a rack.
- **Answer reveal** — a genuinely custom effect: the feedback card rotates in with `graphicsLayer { rotationY }` driven by an `Animatable` plus a custom `Easing` (a slight overshoot), rather than a stock transition.
- **Grading pulse** — a scale-and-glow pulse on correct/incorrect, built with `animateFloatAsState` over a custom spring spec, keyed by the grading result.
- **Progress motion** — count-up numbers via `animateFloatAsState` and a `Canvas`-drawn progress ring whose sweep angle is animated; the resting value always equals the true value.
- **Due-count badge** — a springy scale animation when the due count changes.

Because the spec forbids animations from blocking input, animations are presentation-only: grading and advancing are driven by state changes, and the animation observes those states rather than gating them. Respecting the system "remove animations" preference is a follow-up refinement, not part of this change.

### AI coach integration

The port is the contract, and everything else is a detail behind it.

```
interface CoachAgent {
  suspend fun selectNextItem(request: CoachSelectionRequest): CoachSelection?
  suspend fun generateExercise(request: ExerciseRequest): GeneratedExercise?
  suspend fun generateExplanation(request: ExplanationRequest): GeneratedExplanation?
}
```

Request types carry plain, serializable data: the deck's candidate items (id, prompt, expected answer, learning state, due time), recent `ReviewEvent`s, and — for generation — the single target item plus a `seed`. Responses are nullable, so "no opinion" is a first-class result and the caller's fallback is the normal path, not an error path.

Three decorators/participants, all in `core/data/coach`:

1. **`DeterministicCoachAgent`** — the MVP implementation, fully offline, no Android dependency beyond the domain model:
   - `selectNextItem` ranks candidates by a documented score: lapsed items first, then most overdue, then lowest recent accuracy, then stable id order; it returns the top candidate (or `null` if the list is empty).
   - `generateExercise` produces an exercise *variant* from the item's stored content with a seeded, deterministic transformation — e.g. a cloze deletion over the expected answer, or a deterministic distractor set drawn from sibling items in the deck. The same `(item, seed)` always yields the same exercise.
   - `generateExplanation` returns the item's stored explanation, or composes a template explanation referencing the expected answer.
2. **`CoachOutputValidator`** — enforces the spec's validation rules: non-empty prompt and expected answer, parseable shape, and `targetItemId` equal to the requested item. Failures are discarded, never surfaced.
3. **`TimeoutCoachAgent`** — a thin decorator applying `withTimeout` around any delegate, so a future slow implementation cannot stall a session.

Hilt binds `CoachAgent` to `TimeoutCoachAgent(DeterministicCoachAgent())`. Swapping in a future LLM adapter is a one-line binding change plus its own implementation — which is precisely the property `ai-coach` requires.

**The planning loop, described plainly:** the session asks the coach for the next item; the coach observes the deck's scheduling state and recent performance and answers; the session presents a generated variant for that item; grading records a `ReviewEvent`; the scheduler recomputes the item's cached state; the loop repeats. "Feedback incorporation" is thus not a special mechanism — it is the fact that both the coach and the scheduler read the same append-only history the previous answers wrote.

**Alternatives considered:** letting the coach own the loop (a long-running agent deciding everything) — rejected, because it would put session control inside the replaceable component and make offline determinism harder to guarantee; a coach that writes to the database — rejected, since the coach must not be able to corrupt review history.

### Background work strategy

WorkManager, configured in `SpaCoApplication` via `Configuration.Provider`.

- **`PeriodicWorkRequest`** (the due-review sweep), constrained to not require network, running roughly every 12 hours (12h suits a daily study habit and is well above the 15-minute floor).
- **`OneTimeWorkRequest`** with an `initialDelay` equal to `earliestNextDueAt − now`, rescheduled by `ReviewReminderScheduler` after every session and after every sweep, so the learner is not notified late. `ExistingWorkPolicy.REPLACE` for the one-time check keeps a single pending reminder.
- **`DueReviewWorker`** loads due items via `ItemDao`, and:
  - updates the in-app due state (which happens for free through the `Flow`-backed DAOs),
  - posts a notification **only if** the due set is non-empty, reminders are enabled, `POST_NOTIFICATIONS` is granted on API 33+, and the due set differs from `ReminderStateEntity.lastNotifiedDueSignature`,
  - then reschedules the next one-time check.
- Reboot/restart durability is WorkManager's own guarantee; no `BOOT_COMPLETED` receiver is written by hand.
- Reminder preference and permission state live in `feature/settings`; when reminders are off or permission is denied, the worker still runs and still refreshes in-app due counts, but never posts a notification.

**Trade-off:** WorkManager timing is inexact under Doze, so a "due at 09:00" reminder may land later. Mitigated by treating reminders as best-effort: the app recomputes due counts on every open, so the in-app experience is always accurate.

### Media strategy is deferred behind an unwritten seam

No audio or image handling ships in this change (`proposal.md` scope boundary). The design point worth recording is *where* media would attach so that the future change is additive: the domain already models an item as "prompt + expected answer + explanation", so a future `MediaRef` on `PracticeItem` plus a `MediaRepository` port (local asset first, then a download-and-cache path with eviction, keyed by a content hash and validated before playback) would slot in beside the existing repository ports without touching session flow or scheduling. The MVP intentionally writes neither the port nor the schema column: an unused abstraction is worse than an absent one, and the offline-media capability is expected to specify its own caching and eviction behavior.

## Risks / Trade-offs

- **The deterministic coach produces low-variety practice content.** → Accepted as an MVP limitation and made explicit in `ai-coach`'s fallback requirements; the transformation set is seeded and rotated so a given item does not always look identical, and the port makes a richer coach a drop-in later.
- **Missing toolchain pieces block the very first build.** The SDK platform, build-tools, and the Gradle wrapper did not exist, so `tasks.md` bootstraps them before any Kotlin compiles. → Mitigation: those bootstrap steps are the first tasks and use the `android` CLI (`android sdk install "platforms;android-37.2" "build-tools;37.0.0"`) rather than the deprecated `sdkmanager` or hand-downloading, and AGP 9.5.0 is pinned to Gradle 9.8.2 — a combination checked against AGP's published compatibility table.
- **AndroidX's `minCompileSdk` can outrun the chosen `compileSdk`.** Current libraries already require 37, so `compileSdk 36` no longer builds. → Resolved by compiling against `android-37.2` while keeping `targetSdk 36`; the residual risk is that a future library bump raises `minCompileSdk` again, so dependency pins in the version catalog are treated as deliberate choices rather than things to float.
- **The derived scheduling cache can drift from review history.** → Mitigation: a single pure scheduler function is the only writer, `recompute(history)` is asserted equal to the cache in a unit test, and a rebuild path recomputes every item from history (also the migration hook for parameter changes).
- **Notifications on API 33+ require runtime permission, and denial is a normal state.** → Mitigation: the design separates "compute due items" from "post a notification", so denial degrades to in-app counts only, and the settings screen surfaces the blocked state.
- **Clock and time-zone handling can make "due" and "streak" disagree with the learner's expectations** across DST or travel, because storage is UTC and days are local. → Mitigation: all scheduling math takes an explicit reference instant, tests cover DST boundaries, and day bucketing is computed in the current local zone at read time.
- **Emulator plus Gradle plus the Kotlin daemon on a 16 GB M1 can thrash memory.** → Mitigation: cap the Gradle JVM heap and the Kotlin daemon in `gradle.properties`, and run full builds and the emulator separately rather than concurrently.
- **Animations can hurt input latency if they gate state.** → Mitigation: the design drives all state transitions from the ViewModel and treats animation as an observer of state, and the specs state that controls must be usable while an animation runs.

## Migration / Rollout Plan

Greenfield, so there is no data migration and no rollback-to-previous-version problem: the app has no users and no prior schema. Rollout is therefore a bootstrap sequence, and each step is reversible by deleting the build outputs or uninstalling the app:

1. Install the missing SDK packages (`platforms;android-37.2`, `build-tools;37.0.0`) and generate/commit the Gradle wrapper — the only machine-level changes, and both are additive.
2. Land the Gradle skeleton and a "hello Compose" `MainActivity`; verify it builds and installs to the `OpenTUI_Pixel_8_API_36` AVD before any domain code exists.
3. Layer domain → data → UI as ordered in `tasks.md`, keeping the app runnable at each step.
4. Room ships schema version 1 with `exportSchema` enabled; subsequent changes add explicit migrations. Because there are no users yet, destructive fallback is acceptable *only* until the first release and is not relied upon.

## Open Questions

- **Which coach implementation ships next, and with what provider?** A real LLM adapter is a follow-up change by design; the MVP deliberately ships the deterministic one. Confirming the eventual provider does not change this change's specs, approach, or tasks.
- **Tuning constants.** Session item budget, the review window used for "recent accuracy", and the scheduling parameter defaults (`initialInterval`, `growthFactor`, `maximumInterval`) are chosen with sensible starting values and can be tuned from usage without changing any spec.
- **Whether `kotlinx-datetime` is needed at all** once `java.time` is in use under `minSdk 26` — resolvable during implementation.
- **When to split the single `:app` module** into the module boundaries the package layout already implies — deferred until the code grows enough to justify the build cost.
