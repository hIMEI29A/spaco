# Tasks

## 1. Transport and serialization toolchain

- [x] 1.1 Add `kotlinx-serialization-json` to `gradle/libs.versions.toml` and to `app/build.gradle.kts` as a runtime dependency (no serialization compiler plugin: the adapter uses the `JsonElement` API only; verify the chosen version resolves from the local Gradle cache with `./gradlew --offline :app:dependencies --configuration debugRuntimeClasspath`). Verify with `./gradlew :app:assembleDebug` and `./gradlew test lint` — the existing suite must stay green with no new lint errors.
- [x] 1.2 Enable `buildConfig = true` in `app/build.gradle.kts` (explicitly, with a comment on why) and verify the generated `app/build/generated/source/buildConfig/debug/com/spaco/app/BuildConfig.java` appears after `./gradlew :app:assembleDebug`.

## 2. Build-time configuration and network permission

- [x] 2.1 Read `LLM_BASE_URL`, `LLM_MODEL`, `LLM_API_KEY` from `local.properties` in `app/build.gradle.kts` and expose them as `buildConfigField`s, defaulting to `""` when the file or any key is absent, so a checkout without `local.properties` still builds. Verify by building once with the three keys set and once without, and inspecting the generated `BuildConfig.java` for the expected values (`aapt2`-free check: grep the generated file).
- [x] 2.2 Add `core/data/coach/LlmCoachConfig.kt`: a value type holding base URL, model, and credential, plus a factory that returns `null` when any of the three is blank (all-or-nothing, per design D2). Verify with `LlmCoachConfigTest` covering fully configured, each single blank field, and all blank.
- [x] 2.3 Add `<uses-permission android:name="android.permission.INTERNET" />` to `app/src/main/AndroidManifest.xml`. Verify with `$ANDROID_HOME/build-tools/37.0.0/aapt2 dump permissions app/build/outputs/apk/debug/app-debug.apk` that `android.permission.INTERNET` is listed and `POST_NOTIFICATIONS` is still there.
- [x] 2.4 Document the `local.properties` contract in `README.md` and `docs/dev-setup.md`: the three key names, that leaving them unset keeps the deterministic coach and makes no network request, and that a configured build embeds the credential in the APK so it must not be published. Verify by following the documented steps from a clean checkout and confirming the build succeeds as written.

## 3. The LLM coach implementation

- [ ] 3.1 Add the OpenAI-compatible request/response DTOs and the JSON extraction that tolerates a fenced or prose-wrapped body (design D4). Verify with unit tests for extraction: clean object, code-fenced object, prose around the object, and no object at all (→ `null`).
- [ ] 3.2 Implement `LlmCoachAgent`: a `CoachAgent` sending `POST {baseUrl}/v1/chat/completions` over `HttpURLConnection` with a per-operation system prompt demanding a single JSON object, `temperature: 0`, the bearer credential, and the adapter's own bounds (connect 3 s, read 5 s, 6 s overall, per design D6); map each response to the port's types, requiring the echoed item identity. Verify with `LlmCoachAgentTest` against an in-process JDK `HttpServer` covering all three operations, a non-2xx status, a malformed body, a missing identity, a mismatched identity, and a response that arrives after the bound — each unusable case returning `null` and none throwing.
- [ ] 3.3 Verify the outgoing payload from the same tests: assert the recorded request body contains the target item's content and the sibling answers, and contains no field the port does not define (design D8), and that the credential travels only in the `Authorization` header.
- [ ] 3.4 Verify the credential is not echoed in failures: feed an error status and a transport failure through `LlmCoachAgent` and assert the resulting diagnostic text (message and cause chain) does not contain the configured credential.

## 4. Composition and wiring

- [ ] 4.1 Add `FailoverCoachAgent(primary, secondary)`: answers with the primary, falls back to the secondary only when the primary returns `null`. Verify with `FailoverCoachAgentTest` — primary answer wins and the fallback is not called, primary `null` yields the fallback's answer, sibling requests including `null` from both — plus a test that a `CancellationException` from the primary still propagates.
- [ ] 4.2 Rewire `CoachModule.provideCoachAgent()` to `TimeoutCoachAgent(FailoverCoachAgent(LlmCoachAgent(...), DeterministicCoachAgent()), timeout = 8 s)`, built from `LlmCoachConfig.from(BuildConfig...)`, falling back to the deterministic coach alone when the config is `null`; keep the timeout constants in one place with a comment explaining the relationship between the adapter and outer bounds (design D6). Verify with `./gradlew test` (existing `TimeoutCoachAgentTest` and coach tests still pass) and by confirming with a debug build plus a key set in `local.properties` that the app starts and serves sessions.

## 5. Feedback no longer waits for the explanation

- [ ] 5.1 Reorder `SessionViewModel.recordGrade` so the grade and the already-recorded review are published first, then resolve the explanation and merge it into `presentedExplanation` only while the session still shows the same item (design D7); record nothing new before the review event. Verify with updated `SessionViewModelTest`: with a coach whose explanation call suspends, the state exposes `grade` and the recorded review while the call is still pending, the explanation appears once it resolves, and a late explanation is not applied after the learner advances to the next item.
- [ ] 5.2 Confirm the existing session behavior is otherwise unchanged: `SessionViewModelTest`, `SessionItemSelectorTest`, `SessionScreenTest`, and `CoachOutputValidatorTest` all pass under `./gradlew test`, with no assertion weakened.

## 6. Integration verification

- [ ] 6.1 Run `./gradlew test lint` and confirm the full suite passes and lint reports no new errors beyond the pre-existing `OldTargetApi` and `MissingApplicationIcon` warnings.
- [ ] 6.2 Run `./gradlew :app:connectedDebugAndroidTest` on the API 36 AVD with an **unconfigured** build and confirm the instrumented tests pass — this is the offline guarantee: with no keys the app makes no request and sessions behave as before.
- [ ] 6.3 Manual offline check on the AVD with an unconfigured build: run a session in airplane mode and confirm the exercise, grading, and explanation still appear with no perceptible wait.
- [ ] 6.4 Optional manual smoke check with a configured build against the endpoint in `local.properties`: run one session and confirm a remotely generated exercise is presented, is attributable to the item it was generated for, and that the item's stored content is unchanged afterwards.

## Workflow follow-up

- Commit the change on the `real-llm-adapter` branch, archive it with `openspec archive add-real-llm-coach-adapter`, and open the pull request into `dev` for review (the change is not implemented by the planning workflow).
- Bump `versionCode`/`versionName` only if the reviewer asks for a release in the same cycle; this change does not decide that.
