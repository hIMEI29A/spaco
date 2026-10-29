# Tasks

## 1. Dependency refresh

- [ ] 1.1 Bump `datastore` from `1.2.0` to `1.3.0` in the `[versions]` block of `gradle/libs.versions.toml`. Verify with `./gradlew :app:assembleDebug` (BUILD SUCCESSFUL) and confirm the resolved artifact version with `./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep datastore-preferences`.
- [ ] 1.2 Add `androidxTestRunner = "1.7.0"` to `[versions]` and change the `androidx-test-runner` entry in `[libraries]` from the inline `version = "1.5.0"` literal to `version.ref = "androidxTestRunner"`. Verify with `./gradlew :app:assembleDebugAndroidTest` (BUILD SUCCESSFUL) and that no other library entry still uses an inline `version =` literal.
- [ ] 1.3 Confirm Lint no longer reports the two `GradleDependency` warnings: run `./gradlew :app:lintDebug` and check that `app/build/reports/lint-results-debug.txt` has no `GradleDependency` line for `datastore-preferences` or `androidx.test:runner`. Note any *new* `GradleDependency` warning the bump introduces in the change notes rather than fixing it (widening the update set is out of scope).

## 2. Remove the temporary test dump scaffolding

- [ ] 2.1 In `app/src/androidTest/java/com/spaco/app/feature/decks/OfflineDeckManagementTest.kt` delete the `dumpTree(expected)` function and its `SPACO_DUMP_BEGIN`/`SPACO_DUMP_END` output, collapse `awaitText`/`awaitTag` to plain `composeRule.waitUntil { ... }` calls (removing the `try`/`catch (ComposeTimeoutException)` wrappers), and remove the `ComposeTimeoutException`, `onRoot` and `printToString` imports. Verify there is no remaining reference in that file with `grep -nE "dumpTree|SPACO_DUMP|onRoot|printToString|ComposeTimeoutException" app/src/androidTest/java/com/spaco/app/feature/decks/OfflineDeckManagementTest.kt` (no output).
- [ ] 2.2 Apply the same removal to `app/src/androidTest/java/com/spaco/app/feature/session/SessionResumeTest.kt` (its `awaitText`/`awaitTag` use a `hasTag(tag)` helper — keep that helper and its `onAllNodesWithTag` import). Verify with `grep -nE "dumpTree|SPACO_DUMP|onRoot|printToString|ComposeTimeoutException" app/src/androidTest/java/com/spaco/app/feature/session/SessionResumeTest.kt` (no output) and `./gradlew :app:assembleDebugAndroidTest` (BUILD SUCCESSFUL).
- [ ] 2.3 Confirm the scaffolding is gone across the whole test source set and that no test asserts on it: `grep -rnE "dumpTree|SPACO_DUMP" app/src` returns nothing, and `OfflineEndToEndTest` remains untouched (`git diff --name-only` lists only the two files above plus `gradle/libs.versions.toml`).

## 3. Integration verification

- [ ] 3.1 Run the full JVM check: `./gradlew test lint` — BUILD SUCCESSFUL, no lint errors, and the two `GradleDependency` warnings from task 1.3 still absent.
- [ ] 3.2 Run the instrumented tests on the reference AVD (`emulator-5554`, AVD `OpenTUI_Pixel_8_API_36`) with `./gradlew :app:connectedDebugAndroidTest` — all three tests pass (`OfflineDeckManagementTest`, `SessionResumeTest`, `OfflineEndToEndTest`), demonstrating the `androidx.test:runner` 1.7.0 instrumentation runner behaves as before. Uninstall the app first if a stale install blocks the run (`adb uninstall com.spaco.app`).
- [ ] 3.3 Manually confirm the `datastore-preferences` 1.3.0 upgrade did not alter persisted preferences: on the AVD select an active deck and toggle review reminders in Settings, restart the app, and check both settings are still in effect.

## Workflow follow-up

- Archive the change after the project's review requirements are satisfied.
- Verify the archived result.
