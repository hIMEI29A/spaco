# Design

## Context

See `proposal.md` — Why. This is a maintenance-only change: two dependency versions in the Gradle version catalog and the removal of temporary debugging scaffolding from the instrumented tests. There are no spec-level behavior changes (`skip_specs: true`).

Relevant current state:

- `gradle/libs.versions.toml` is the single source of dependency versions; the module's `app/build.gradle.kts` consumes it through the `libs` accessor everywhere except `androidx-test-runner`, which is pinned with an inline literal `version = "1.5.0"` in the `[libraries]` block.
- `androidx.datastore:datastore-preferences` backs `SettingsDataStore` (`Context.settingsDataStore`), used by the active-deck store and the reminder preferences. There are no unit tests for the DataStore-backed stores; they are covered only by compilation and by instrumented/manual runs.
- `androidx.test:runner` is used only as `androidTestImplementation`; the instrumentation runner is the project's own `com.spaco.app.HiltTestRunner` extending `AndroidJUnitRunner`.
- Exactly two files carry the scaffolding:
  - `app/src/androidTest/java/com/spaco/app/feature/decks/OfflineDeckManagementTest.kt` — `dumpTree(expected)` plus `try`/`catch (ComposeTimeoutException)` in `awaitText`/`awaitTag`, and the imports `ComposeTimeoutException`, `onRoot`, `printToString`.
  - `app/src/androidTest/java/com/spaco/app/feature/session/SessionResumeTest.kt` — the same helper, the same wrappers, and the same three imports.
  - `OfflineEndToEndTest` has `awaitText`/`awaitTag` but no dump scaffolding, so it is untouched.
- Lint reports the two outdated dependencies as `GradleDependency` warnings (`datastore-preferences` 1.2.0 → 1.3.0; `androidx.test:runner` 1.5.0 → 1.7.0). Both are non-failing warnings today.

## Goals / Non-Goals

**Goals:**

- Resolve the two `GradleDependency` lint warnings for the dependencies named in the proposal.
- Remove the `dumpTree` / `SPACO_DUMP_*` scaffolding and everything that exists only to support it, leaving the instrumented tests with the same coverage and the same assertions.
- Keep the diff minimal and mechanical, so it is trivially reviewable and revertible.

**Non-Goals:**

- Extracting the duplicated `awaitText`/`awaitTag`/`awaitNoTag` helpers (shared by all three instrumented tests) into a common test utility. That is a refactor with a wider blast radius; it is deliberately left alone.
- Introducing any replacement diagnostic mechanism (system property, flag, custom reporter).
- Changing `targetSdk`, `compileSdk`, or any dependency other than the two named.
- Touching production code.

## Decisions

### D1 — Bump versions in the version catalog, not in the module script

Edit `[versions]`/`[libraries]` in `gradle/libs.versions.toml`. The catalog is the project's established convention and every other dependency is declared there; editing `app/build.gradle.kts` would break that convention.

*Alternative considered:* overriding the version in `app/build.gradle.kts` with a `strictly`/`force` constraint — rejected as it hides the real version in two places.

### D2 — Move the `androidx.test:runner` version into `[versions]`

Add `androidxTestRunner = "1.7.0"` to `[versions]` and reference it via `version.ref` in `[libraries]`, matching `androidxTestCore`/`androidxTestJunit`. The inline literal is the only outlier in the file and removing it is a one-line improvement that belongs to this change.

*Alternative considered:* keep the inline literal and just change `1.5.0` → `1.7.0` — smaller diff, but leaves an inconsistency the change is already touching.

### D3 — Delete the dump scaffolding outright, with no replacement

Remove the `dumpTree` function and its `SPACO_DUMP_BEGIN`/`SPACO_DUMP_END` output, collapse `awaitText`/`awaitTag` to plain `composeRule.waitUntil { ... }` calls, and delete the imports (`ComposeTimeoutException`, `onRoot`, `printToString`) that existed only for the dump. The user chose full removal over keeping a lightweight message.

Rationale: `waitUntil` already throws `ComposeTimeoutException`, whose message reports the timeout and the condition that never became true, so the dump added tree content but no new signal about *which* wait failed beyond what the helper names already convey. The scaffolding was added to diagnose flaky waits that have since been fixed.

*Alternative considered:* keep the dump behind a flag such as `SPACO_DUMP=1` or a system property — rejected: it keeps dead code and conditional branches in tests for a diagnostic that can be re-added locally in minutes if flakiness returns.

*Alternative considered:* keep the `try`/`catch` and rethrow with a message naming the awaited selector — rejected as unnecessary; it re-implements information the exception already carries and would touch the same lines twice.

### D4 — Leave the other two instrumented tests' helpers as they are

`OfflineEndToEndTest` and the remainder of `SessionResumeTest`/`OfflineDeckManagementTest` keep their own copies of `awaitText`/`awaitTag`/`awaitNoTag`. Deduplicating them is a separate concern (see Non-Goals) and would enlarge an otherwise mechanical diff.

## Risks / Trade-offs

- **`androidx.test:runner` 1.5.0 → 1.7.0 skips two minor releases and may change `AndroidJUnitRunner` defaults** (e.g. execution-order or process behavior for instrumented tests) → Mitigation: re-run all three instrumented tests on the reference AVD and confirm they still pass; the project's runner is a thin `HiltTestRunner` subclass, so any behavioral change is limited to the base class.
- **`datastore-preferences` 1.3.0 could shift behavior, and the DataStore-backed stores have no unit-test coverage** → Mitigation: the `Preferences` DataStore API is stable across 1.2 → 1.3; verify by compiling, then confirming on the AVD that the active-deck selection and the reminder toggle survive an app restart (the instrumented deck-management test already restarts the activity and re-reads persisted state).
- **Losing the tree dump makes a future timeout harder to diagnose** → Mitigation: accept it (the dump was temporary by intent); if flakiness reappears, re-add the dump locally without committing it.
- **The bump may surface new `GradleDependency` suggestions** (for example a newer `androidx.test:core` or `androidx.test.ext:junit` that Kotlin/test-runner 1.7.0 pulls alongside) → Mitigation: treat them as the pre-existing non-failing warnings they are; widening the update set is explicitly out of scope.
- **Lint's reported target versions might be superseded or unavailable in this environment** → Mitigation: the versions come from Lint's own dependency index; if resolution fails, fall back to the highest version that resolves and record the deviation in the task notes.

## Migration Plan

Not applicable in the data sense — no schema, storage-format, or API migration is involved; dependency resolution and test sources only.

- Deploy: merge as a normal change; no runtime rollout steps.
- Rollback: revert the single commit. Nothing below the build/test layer depends on the outcome.

## Open Questions

None.
