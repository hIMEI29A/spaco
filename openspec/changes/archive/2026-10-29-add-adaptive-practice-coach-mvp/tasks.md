# Tasks

## 1. Toolchain and project bootstrap

- [x] 1.1 Install the missing SDK packages with the `android` CLI — `android sdk install "platforms;android-37.2" "build-tools;37.0.0"` (accepting the SDK licenses if prompted) — and verify that `$ANDROID_HOME/platforms/android-37.2` and `$ANDROID_HOME/build-tools/37.0.0` both exist
- [x] 1.2 Generate the Gradle wrapper pinned to the installed Gradle 9.8.2 (`gradle wrapper --gradle-version 9.8.2`, then commit `gradlew`, `gradlew.bat`, `gradle/wrapper/*`) and verify `./gradlew --version` reports 9.8.2
- [x] 1.3 Create the Gradle skeleton — `settings.gradle.kts`, root `build.gradle.kts`, `gradle.properties` (capped Gradle/Kotlin daemon heap), `gradle/libs.versions.toml` pinning AGP 9.5.0 with the Kotlin and Compose compiler plugin versions, and the `:app` module with `compileSdk` 37 (minor 2) / `targetSdk` 36, `minSdk` 26, JDK 17 toolchain — and verify `./gradlew :app:assembleDebug` succeeds
- [x] 1.4 Add and pin the MVP dependencies in the version catalog (Compose BOM 2026.09.00 + Material 3, Navigation Compose 2.10.2, Lifecycle, Room 2.8.5 + KSP, WorkManager 2.12.0, Hilt 2.60.1, coroutines, JUnit/Turbine/Robolectric) and verify `./gradlew :app:assembleDebug` still succeeds and `./gradlew :app:dependencies` resolves them without conflict
- [x] 1.5 Add a minimal `SpaCoApplication` + `MainActivity` rendering a "SpaCo" Compose screen on the Material 3 theme, and verify `./gradlew :app:installDebug` installs it and `adb shell am start -n <appId>/.MainActivity` shows the screen on the `OpenTUI_Pixel_8_API_36` AVD
- [x] 1.6 Write `docs/dev-setup.md` documenting the exact bootstrap commands (SDK install, wrapper, build, install, emulator start) and verify each command runs as written on this machine

## 2. Domain model and scheduling core

- [x] 2.1 Add the pure domain models (`Skill`, `Deck`, `PracticeItem`, `ReviewEvent`, `LearningState`, `SessionState`, grading outcome) under `core/domain/model` with no Android imports, and verify `./gradlew :app:compileDebugKotlin` passes and a check confirms no `android.*` import exists under `core/domain`
- [x] 2.2 Implement `SchedulingParameters` plus the pure `applyReview(state, params, grade, answeredAt)` function, and verify unit tests cover: first correct answer schedules in the future, successive correct answers strictly grow the interval, the interval caps at `maximumInterval`, an incorrect answer on a reviewing item lapses it and sets it due soon, and an incorrect new item stays due
- [x] 2.3 Implement the pure `recompute(history, item, params)` rebuild and the pure due-order function (most overdue first, stable id tiebreaker), and verify unit tests assert `recompute` reproduces the same state and next-due time for the same history at the same reference instant, and that equal due times keep a stable order across repeated calls

## 3. Persistence layer

- [x] 3.1 Define the Room entities (`SkillEntity`, `DeckEntity`, `PracticeItemEntity` with the derived scheduling cache columns, `ReviewEventEntity`, `SessionStateEntity`, `ReminderStateEntity`) with `exportSchema = true`, and verify `./gradlew :app:assembleDebug` succeeds and `app/schemas/<db>/1.json` is produced
- [x] 3.2 Implement the DAOs including `observeDueItems(deckId, now)` ordered by `nextDueAt ASC, id ASC` and the `Flow`-returning reads used by progress, and verify DAO tests (Robolectric or instrumented) pass for insert/read, append-only review history, and due-order correctness
- [x] 3.3 Implement the repository interfaces' concrete Room-backed implementations plus entity/domain mappers and their Hilt modules, and verify repository unit tests pass for deck create/rename/archive/restore, item add/edit preserving review history, and persistence across a database reopen
- [x] 3.4 Wire review recording so a graded answer appends a `ReviewEvent` and recomputes the item's cached scheduling state in one transaction, and verify an integration test asserts the stored cache equals `recompute(history)` after a sequence of mixed correct/incorrect answers

## 4. AI coach port and deterministic implementation

- [x] 4.1 Declare the `CoachAgent` port and its plain request/response types (`CoachSelectionRequest`/`CoachSelection`, `ExerciseRequest`/`GeneratedExercise`, `ExplanationRequest`/`GeneratedExplanation`) under `core/domain/coach`, and verify compilation plus the no-Android-import check on `core/domain`
- [x] 4.2 Implement `DeterministicCoachAgent.selectNextItem` with the documented ranking (lapsed → most overdue → lowest recent accuracy → stable id) and verify unit tests for the ranking order, the empty-candidate case returning `null`, and never returning an item outside the supplied deck
- [x] 4.3 Implement `DeterministicCoachAgent.generateExercise` (seeded cloze deletion and seeded distractors drawn from sibling items) and `generateExplanation` (stored explanation or template), and verify tests assert the same `(item, seed)` yields identical output, every result carries the requested `targetItemId`, and a generated variant never overwrites stored item content
- [x] 4.4 Implement `CoachOutputValidator` (non-empty prompt/answer, parseable shape, `targetItemId` match) and the `TimeoutCoachAgent` decorator, and verify tests cover empty, malformed, and mismatched output being discarded plus a delegate that exceeds the timeout producing `null` rather than throwing
- [x] 4.5 Bind `CoachAgent` to `TimeoutCoachAgent(DeterministicCoachAgent())` via Hilt, and verify a test substituting a fake `CoachAgent` runs a full session with unchanged externally observable behavior (same review events and scheduling outcome)
- [x] 4.6 Implement coach-driven session selection with the fallback path in the session layer, and verify a test asserts the session still presents stored content and completes when the coach returns `null` or throws

## 5. Deck management UI

- [x] 5.1 Build the deck list screen (grouped by skill) with create, select-active, rename, archive, and restore, and verify Compose UI tests cover creating a deck, rejecting an empty name, selecting the active deck, and hiding an archived deck from the list
- [x] 5.2 Build the deck detail and item editor screens for adding and editing items, and verify Compose UI tests cover saving a valid item, rejecting a missing prompt or expected answer with the field-level message, and editing an item without changing its review history
- [x] 5.3 Verify the offline deck-management requirements end to end with a UI test run in airplane mode (create, view, edit, rename, archive, restore) and a restart-based test asserting all content is still present

## 6. Practice session

- [x] 6.1 Implement `SessionViewModel` with the session state machine — start (including nothing-due and empty-deck outcomes), next-item selection, answer submission, grading, feedback, self-grading for free-recall items, ending early, and resume/discard of an interrupted session — and verify unit tests cover each transition, the no-repeat-within-a-session rule, and rejection of an empty submission
- [x] 6.2 Build the session UI — item card, answer input, feedback/explanation panel, progress indicator — with the custom `AnimatedContent` item transition, the `rotationY` answer-reveal effect, and the grading pulse, and verify Compose tests assert the session's controls are enabled and answer submission is never gated on an animation completing
- [x] 6.3 Build the session summary screen showing items answered and correct for both planned completion and early exit, and verify Compose UI tests assert the counts and that results for answered items reach the persistence layer
- [x] 6.4 Verify resume behavior with an instrumented test that interrupts a session (background/recreate the activity mid-session), resumes it, and asserts the earlier answers are still recorded

## 7. Progress visualization

- [x] 7.1 Implement the progress queries — due count, per-state counts, accuracy over the configured recent window, and practice-day streak (local-zone day bucketing) — and verify unit tests cover counts matching the scheduler's due set, archived decks excluded from overall totals, sparse history, and no-history cases
- [x] 7.2 Build the progress screen with the animated count-up numbers and the `Canvas` progress ring, and verify Compose UI tests assert the resting values equal the current true values and that the summary updates after a session without an app restart
- [x] 7.3 Verify the empty and zero-history states render an explicit empty state (not zeros or placeholders) with a UI test, and verify a DST-boundary unit test keeps day bucketing and streaks correct

## 8. Background review scheduling and reminders

- [x] 8.1 Implement the notification channel, `ReviewReminderScheduler` (periodic sweep plus a one-time check delayed to the earliest `nextDueAt`), and the reminder-preference store, and verify tests using `WorkManagerTestInitHelper` assert both work requests are enqueued with the expected delays and policy after a session
- [x] 8.2 Implement `DueReviewWorker` — load due items, refresh in-app due state, post a notification only when the due set is non-empty, reminders are on, permission is granted, and the due signature differs from the last notified one, then reschedule the next one-time check — and verify worker tests cover the due/not-due cases, the duplicate-suppression rule, and rescheduling
- [x] 8.3 Build the settings screen with the reminder toggle and the `POST_NOTIFICATIONS` request plus the "blocked by system" state, and verify tests for reminders-off cancelling notifications and re-enabling restoring them, plus a manual AVD check that denying permission leaves the app fully usable with no notification attempted
- [x] 8.4 Verify reminder durability across a simulated reboot using the WorkManager test harness and document the manual verification procedure (`adb shell am broadcast -a android.intent.action.BOOT_COMPLETED` with the app's worker persisted) in `docs/dev-setup.md`

## 9. Integration verification

- [x] 9.1 Run the full offline end-to-end path on the `OpenTUI_Pixel_8_API_36` AVD with connectivity disabled (`adb shell cmd connectivity airplane-mode enable`): create a deck, add items, run a full session, and view progress — verify every core flow completes and the due count updates
- [x] 9.2 Run the complete test suite and lint (`./gradlew test lint`) and verify all tests pass and the build reports no lint errors
- [x] 9.3 Update `README.md` with the build, install, test, and lint commands plus the required SDK packages, and verify each documented command runs as written

## Workflow follow-up

- Sync the change's delta specs into `openspec/specs/` after implementation is verified and the project's review requirements are satisfied.
- Archive the change and verify the archived result.
