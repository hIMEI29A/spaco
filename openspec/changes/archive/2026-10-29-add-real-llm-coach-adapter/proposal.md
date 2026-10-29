# Proposal

## Why

The shipped coach is fully offline and deterministic: it can only reorder the learner's own items and reshape their own text (cloze and reversal variants, templated explanations). It cannot produce genuinely new material, which is the point of an "AI coach". The `CoachAgent` port already exists and the `ai-coach` capability already requires that a different implementation can be substituted behind it, so this change is an isolated replacement of the implementation rather than a redesign of session flow.

## What Changes

- Add an **OpenAI-compatible chat-completions client** behind the existing `CoachAgent` port, covering all three operations: choosing the next item, generating an exercise, and generating an explanation.
- Configure the adapter **at build time** through `local.properties` (base URL, model name, API key), surfaced via `BuildConfig`. No in-app key entry, no settings UI, no runtime reconfiguration.
- Switch the composition to **LLM-first with a deterministic fallback**: when no endpoint or key is configured, or when a request fails, timeouts, or is unusable, the request is answered by `DeterministicCoachAgent`. The app behaves exactly as it does today when the adapter is not configured.
- Ask the model for **structured JSON matching the port's fields** and validate every response with the existing `CoachOutputValidator` before it can reach the learner; anything empty, malformed, or not attributable to the requested target item is discarded.
- Add the network and serialization ingredients this needs: `INTERNET` permission, an HTTP client, a JSON parser, and the `buildConfig` build feature.
- Keep the bounded wait: the adapter never makes a session wait longer than the configured timeout, and a timeout is still converted to "no opinion" rather than an error.
- **Show grading feedback before the explanation arrives**, so a coach that is slow by design (a remote model) cannot delay the learner's verdict. Today the grade is published only after the explanation request returns; with the default bound of two seconds that is unnoticeable, and with a remote model it is not. This is the one change to existing session behavior, and it exists to keep the `ai-coach` requirement that feedback is immediate and the wait bounded.

Explicitly **not** in this change: in-app API key entry or any LLM settings screen, streaming responses, tool use, multi-turn conversation memory, provider-specific adapters beyond the OpenAI-compatible shape, cost/quota accounting, and on-device inference.

## Capabilities

### New Capabilities

- `llm-coach-adapter`: the network-backed coach implementation — how it is configured, how requests map onto the `CoachAgent` port, how model output is constrained and validated, and how it degrades to the deterministic coach without changing session or scheduling behavior.

### Modified Capabilities

- None. The `ai-coach` capability describes the port's contract and its offline/degraded behavior, and neither changes: the port is untouched, and every `ai-coach` requirement (advisory selection, attribution, validation, offline operation, bounded wait, replaceability) must still hold when the LLM adapter is in place. The new behavior is the implementation-level subject of the new capability above.

## Impact

- **New application code**: an LLM client and `CoachAgent` implementation under `app/src/main/java/com/spaco/app/core/data/coach/` (alongside the existing adapter, which stays), a build-time configuration holder, and a fallback-composing implementation. `CoachModule` changes only to choose the composition root.
- **Build / dependencies**: `app/build.gradle.kts` gains `buildConfig = true` and `BuildConfig` fields sourced from `local.properties`; `gradle/libs.versions.toml` gains an HTTP client and a JSON stack (exact choice in `design.md`). `local.properties` is already gitignored, so the key itself never enters the repository.
- **Manifest**: `android.permission.INTERNET` is added — the app stops being a no-network app, which is a user-visible privacy-relevant change even though the request only leaves the device when the adapter is configured.
- **APIs / contracts**: none externally. The `CoachAgent` port, `CoachOutputValidator`, session flow, review recording, and scheduling are unchanged; no new OpenSpec spec for `ai-coach` behavior.
- **Tests**: new unit tests exercise the adapter against a fake HTTP layer, so no test makes a live network call or needs credentials. The existing `DeterministicCoachAgentTest` and `CoachOutputValidatorTest` stay as they are.
- **Docs**: `README.md` / `docs/dev-setup.md` need the `local.properties` keys documented, including that leaving them unset keeps the deterministic coach.
- **Existing behavior at risk**: `CoachWorks without network access` is still satisfied — the fallback path is what runs when the device is offline — but this must be verified rather than assumed, since the app now requests network access. The explanation/feedback ordering change is the only intentional behavior change: it makes feedback arrive sooner, never later, and the review event is still recorded before anything else happens.
