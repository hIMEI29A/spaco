# Design

## Context

See `proposal.md` — Why. The relevant current state:

- `com.spaco.app.core.domain.coach.CoachAgent` is a three-method port (`selectNextItem`, `generateExercise`, `generateExplanation`) over framework-free request/response types that are nullable by design — `null` means "no opinion", not an error.
- The only implementation is `DeterministicCoachAgent`, wrapped by `TimeoutCoachAgent` (default bound 2 s, turns timeouts and thrown exceptions into `null`), composed in one place: `CoachModule.provideCoachAgent()`.
- Callers already validate: `SessionItemSelector` and `SessionViewModel` pass coach output through `CoachOutputValidator`, which discards output that is empty, malformed, or attributable to a different item, and falls back to stored content or scheduler due order.
- The app has no network stack: no `INTERNET` permission, no HTTP client dependency, `buildConfig` is not enabled, and `local.properties` is gitignored (it already holds `sdk.dir`).
- `SessionViewModel.recordGrade` records the review, then *awaits* the explanation before publishing the grade — grading feedback is currently as slow as the coach's explanation request. That is invisible at a 2 s bound and unacceptable at a remote model's bound.
- `DomainPurityTest` enforces that `core/domain` never imports Android; new network code must stay out of that package.

## Goals / Non-Goals

**Goals:**

- Serve all three coach requests from an OpenAI-compatible endpoint, with configuration supplied at build time and no runtime UI.
- Make the remote path strictly additive: with no configuration, or with any failure, the app behaves exactly as it does today.
- Keep the wait bound and the "immediate feedback" guarantee true now that a coach can be slow.
- Keep credentials out of the repository, the UI, and diagnostics; keep everything except the fields a request needs on the device.

**Non-Goals:**

- In-app key entry, provider selection screens, or any LLM settings UI.
- Streaming, tool/function calling, conversation memory, retries with backoff, response caching, cost accounting.
- Provider-specific adapters (Anthropic Messages, Gemini `generateContent`) — only the OpenAI-compatible shape.
- Making remote responses reproducible: `ExerciseRequest.seed` stays a deterministic-coach concept and is not sent.

## Decisions

### D1. Another implementation behind the same port, composed as a chain

```
CoachModule:  TimeoutCoachAgent( FailoverCoachAgent(primary = LlmCoachAgent, fallback = DeterministicCoachAgent) )
```

`LlmCoachAgent` performs the request and returns `null` for every unusable outcome. `FailoverCoachAgent` answers with its primary and falls back to the secondary only when the primary returns `null`. Nothing outside `CoachModule` learns that a network exists, which is the property `ai-coach` requires.

Alternatives considered: making `LlmCoachAgent` call `DeterministicCoachAgent` internally (couples two implementations that have nothing to do with each other, and makes each layer's tests need the other); replacing the deterministic coach outright (breaks the offline requirement); a `BuildConfig` switch inside a single hand-written coach (same coupling, worse separation).

### D2. Build-time configuration through `local.properties` → `BuildConfig`

Three fields — `LLM_BASE_URL`, `LLM_MODEL`, `LLM_API_KEY` — are read from `local.properties` in `app/build.gradle.kts` and exposed as `buildConfigField`s, each defaulting to `""` when the file or key is missing. `buildConfig = true` is enabled explicitly. The adapter is selected only when all three are non-blank; a partial configuration is treated as unconfigured, so a half-filled file cannot silently produce requests without a credential.

Alternatives considered: in-app entry stored in DataStore (rejected by the user; also puts the key in an ordinary app file with no encryption); `~/.gradle/gradle.properties` (a second config location with no advantage over the file AGP already reads); an environment variable (Android Studio builds would not see it). The cost is that the key is compiled into the APK — acceptable because this is a local/debug workflow, and documented as such.

### D3. OkHttp + kotlinx.serialization for transport and JSON

`okhttp` and the serialization compiler plugin (version tracking `kotlin`, i.e. `2.4.21`) go into `libs.versions.toml`; DTOs are `@Serializable`. Tests use `mockwebserver` at the same version, running in `app/src/test` on the JVM.

Alternatives: Ktor client (an engine plus more transitive surface for one endpoint); `HttpURLConnection` + `org.json` (no new dependencies, but hand-rolled serialization and a weaker seam for asserting request bodies); Retrofit (a codegen stack for a single call). The chosen pair is the smallest set that gives a real request-shape assertion in tests.

### D4. One request shape: chat completions with a strict-JSON instruction

`POST {baseUrl}/v1/chat/completions` with a per-operation system prompt that demands a single JSON object containing exactly the port's fields plus the requested item identity, and `temperature: 0`. The adapter does not depend on `response_format` being honoured: the parsing step extracts the first balanced JSON object from the content and tolerates a fenced code block, because support for JSON mode varies across OpenAI-compatible servers.

Alternatives: `response_format: json_schema` (server support varies); function/tool calling (varies more, and adds a schema-negotiation path to maintain); free-text parsing with regexes (fragile). Safety note: item text is learner-authored and could contain instructions; a strict output shape, a required identity echo, and the absence of any tool access mean the worst case is unusable text that gets discarded.

### D5. Identity is echoed by the model, and structural parsing lives in the adapter

Each response must name the requested item. Missing or mismatched identity makes the response unusable, which is the model-confusion signal the spec asks for. The adapter converts JSON into domain types (structure); `CoachOutputValidator` keeps doing semantic validation at the existing call sites, so there is exactly one place that decides what is fit to show a learner.

Alternatives: forcing `targetItemId` to the requested item (guarantees attribution but hides model confusion and makes the spec's discard rule unobservable); duplicating `CoachOutputValidator`'s rules inside the adapter (two places to keep in sync).

### D6. Two nested bounds, sized for a remote model

The adapter bounds its own call (connect 3 s, read 5 s, and a 6 s `withTimeout` around transport plus parsing). The outer `TimeoutCoachAgent` keeps a bound on the whole chain and is given 8 s instead of the 2 s default, so it covers a slow model plus the fast deterministic fallback. Both are constants in one file with a comment explaining the relationship.

Alternatives: leaving the outer bound at 2 s (the remote path would essentially never succeed); removing `TimeoutCoachAgent` (loses the guarantee that a future delegate cannot stall a session indefinitely); a per-call timeout passed in per request type (more knobs, no benefit).

### D7. Grading feedback stops waiting for the explanation

`recordGrade` publishes the grade and the recorded-review state first, and resolves the explanation afterwards, merging it into `presentedExplanation` only if the session still shows the same item and has not moved on. Order of durability does not change: the review event is still written before anything else.

Alternatives: keeping the current sequence and accepting a multi-second delay before the learner sees whether they were right (violates the immediate-feedback scenario in `ai-coach` once the coach is remote); pre-fetching explanations before the answer is submitted (impossible — the grade is part of the request).

### D8. Only request fields leave the device

The payload contains the target item's prompt and expected answer, sibling answers used as distractors, scheduling state, recent review outcomes, and the identifiers of the items involved — nothing else. `INTERNET` is added to the manifest, and both `README.md` and `docs/dev-setup.md` state that configuring the adapter sends practice content to the configured endpoint.

## Risks / Trade-offs

- [The credential is compiled into the APK] → treat configured builds as local/debug only; keep the default empty; document that a release built with a key must not be published, and that rotating the key is the remedy if one leaks.
- [No test exercises a real provider, so response-format drift is invisible] → defensive parsing, required identity echo, and the deterministic fallback keep the app working; a manual smoke check against the configured endpoint is documented as optional, not part of the suite.
- [Latency: up to the bound per coach request, and the session budget is 10 items] → bounded waits, immediate grading feedback (D7), and the fallback path; caching and quotas are explicitly out of scope.
- [Prompt injection through item content] → strict output shape, required identity, no tool access, and `CoachOutputValidator` at the call site; the worst case is a discarded response.
- [New dependencies against a pinned toolchain (AGP 9, forced KGP 2.4.21)] → add the dependencies and the serialization plugin first and run `:app:assembleDebug` plus `./gradlew test lint` before writing adapter code, so a resolution or plugin incompatibility surfaces on its own.
- [Determinism is lost for remotely served requests (D5: the seed is not sent)] → tests use a fake transport and assert mapping, validation, and failure paths, never model output; the deterministic coach remains the reproducibility story.
- [A half-configured file could send content without a key, or with the wrong endpoint] → D2's all-or-nothing rule, plus the unconfigured build making no request at all.

## Migration Plan

Additive and reversible; no data migration.

1. Dependencies and `installDebug`-safety first (D3), verified by a clean build and the existing suite.
2. `BuildConfig` fields and the `local.properties` contract (D2) — with the defaults, the change is inert.
3. Adapter, failover composition, and `CoachModule` wiring (D1, D4, D5, D6).
4. Grading-feedback decoupling (D7) with its ViewModel tests, which is the only change to existing behavior.
5. Docs.

Rollback: reverting the change restores the previous composition. A softer rollback is clearing the three `local.properties` keys, which leaves the deterministic coach as the only implementation without touching code.

## Open Questions

- Whether a debug-only indicator of the active coach ("configured: model X") is worth adding later. It changes no requirement and no task here.
- Whether release builds should be blocked or warned when a key is configured. Today this is a documentation rule; enforcing it mechanically is a separate, small change.
