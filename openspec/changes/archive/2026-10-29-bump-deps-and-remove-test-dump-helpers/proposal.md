# Proposal

## Why

The app builds cleanly, but two maintenance debts are left over from the MVP: Android Lint keeps flagging two outdated dependencies (`androidx.datastore:datastore-preferences` 1.2.0 and `androidx.test:runner` 1.5.0), and the instrumented tests still carry the temporary debugging scaffolding (`dumpTree` plus `SPACO_DUMP_BEGIN`/`SPACO_DUMP_END` output) that was added to diagnose flaky UI waits. That scaffolding has served its purpose and now only adds noise to test output and dead code to read.

## What Changes

- Bump `androidx.datastore:datastore-preferences` from `1.2.0` to `1.3.0` in `gradle/libs.versions.toml`.
- Bump `androidx.test:runner` from `1.5.0` to `1.7.0`, moving its version into the `[versions]` block (`androidxTestRunner`) instead of the inline literal currently used in `[libraries]`.
- Remove the temporary semantics-tree dump helpers from the instrumented tests:
  - delete `dumpTree(...)` and its `SPACO_DUMP_BEGIN` / `SPACO_DUMP_END` output,
  - drop the now-redundant `try`/`catch (ComposeTimeoutException)` wrappers around the wait helpers,
  - remove the imports that only existed to support the dump (`ComposeTimeoutException`, `onRoot`, `printToString`).
- No production code, behavior, or API changes.

Not breaking: dependency minor-version bumps and test-only deletions; no externally observable behavior changes.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- None. This is a maintenance change with no spec-level behavior change, so the change declares `skip_specs: true` in `.openspec.yaml` rather than inventing a requirement.

## Impact

- **Build configuration**: `gradle/libs.versions.toml` — two version entries (one already there, one moved out of an inline literal). Resolved artifacts for the app's runtime (`datastore-preferences`) and for instrumented tests (`androidx.test:runner`).
- **Test sources (androidTest only)**: `app/src/androidTest/java/com/spaco/app/feature/decks/OfflineDeckManagementTest.kt` and `app/src/androidTest/java/com/spaco/app/feature/session/SessionResumeTest.kt` — the two files that share the `dumpTree` / `SPACO_DUMP_*` scaffolding.
- **APIs / contracts**: none.
- **Specs**: none. `openspec/specs/` is unchanged.
- **Out of scope** (deliberately not addressed here):
  - the `OldTargetApi` warning (`targetSdk` stays at 36),
  - dependency updates beyond the two named above,
  - extracting the duplicated `awaitText`/`awaitTag`/`awaitNoTag` helpers shared by the three instrumented tests into a common test utility,
  - any change to what the instrumented tests assert or how they drive the UI.
