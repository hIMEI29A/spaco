# Design

## Context

Motivation is in `proposal.md`. Current state that shapes the approach:

- The remote coach is configured at build time: `app/build.gradle.kts` reads three keys from
  `local.properties` into `buildConfigField`s, and `CoachModule` decides once, at
  dependency-injection time, between the remote-first chain and the offline-only one.
- `LlmCoachConfig.from()` is all-or-nothing: a partially filled file is treated as "no remote
  coach", so a half-configuration cannot send practice content without a credential.
- The app is offline-first and account-less: no server, no user identity, no sync. Decks,
  reviews, and settings live in a Room database and one preferences DataStore.
- `AndroidManifest.xml` has `allowBackup="true"` with no backup rules, so any file the app
  stores is eligible for Auto Backup and device-to-device transfer.
- Releases are debug APKs attached to GitHub Releases; there is no CI and no release signing
  configuration yet.
- Precedent: an earlier design rejected in-app credential entry because it would have put the
  key in an unencrypted app file. That objection is what this design answers, not the
  preference for build-time configuration.

## Goals / Non-Goals

**Goals:**

- Make "no credential in the artifact" a property of the build, not a rule people follow.
- Keep the credential per device and per learner, with a working path to change or remove it
  without rebuilding.
- Preserve the offline-first property: an install with no coach configuration behaves exactly
  as today and makes no coaching request.
- Keep decks, progress, and reminders restorable from backup while the credential is not.
- Leave the `CoachAgent` contract, timeouts, failover, and session behavior untouched.

**Non-Goals:**

- No backend, proxy, or hosted key. It is the only way to keep a provider key off clients, and
  it is not warranted while there is a single user; adding one later is an additive change.
- No on-device model and no provider-specific auth (OAuth device flow); both are separate
  capabilities rather than variants of this one.
- No key-rotation tooling, no secret-scanning pipeline, no release signing setup. There is no
  CI to host them, and none of it is required to close the issue.
- No multi-provider abstraction: the configuration stays endpoint + model + optional
  credential against the OpenAI-compatible shape.

## Decisions

### D1. One configuration source: the device

Coach configuration lives in exactly one place — a store on the device, written from the app's
settings — and is resolved per request. Alternatives considered: build-time configuration
(current, fails the artifact requirement); hybrid, where a build default and a runtime value
both exist (rejected in D2); a proxy that holds the key (rejected in Non-Goals); on-device
inference (a different capability, not a credential fix).

### D2. Remove all three `BuildConfig` fields, not just the credential

The endpoint and model are not secrets, so they could stay as build defaults. They are removed
anyway:

- Two sources would need a precedence rule (build default vs saved value vs none), with its own
  tests, to save one-time typing on a developer machine.
- Leaving `LLM_*` fields in the artifact keeps the cleanliness claim conditional: everyone
  reading the project — a reviewer, a scanner, the author in six months — has to verify the
  fields are empty. Removing them makes the claim unconditional and mechanically checkable.
- The gain from build defaults is small: a saved configuration persists across app restarts, so
  a local endpoint is entered once per install.

The alternative considered and not taken: keep endpoint/model as build-time defaults used only
to pre-fill empty settings fields, never auto-saved and never used before the learner saves.
That is a legitimate additive change later; it is deliberately out of scope now so the delta
stays small and the invariant stays simple.

Defaults for the endpoint and model therefore live as constants in the app's source and are
used only as input hints in the settings form. Nothing is saved or sent until the learner
presses save.

### D3. The credential is optional

`LlmCoachConfig.from()` keeps requiring an endpoint and a model, and stops requiring a
credential: an endpoint without authorization is a valid configuration (local
OpenAI-compatible servers, `adb reverse`-forwarded proxies, LAN servers). The Authorization
header is sent only when a credential is set.

The all-or-nothing rule existed to prevent a half-configured file from silently sending
practice content without a credential. With on-device entry that risk moves to a user action,
and it is answered differently: a "check connection" action performs a bounded probe of the
configured endpoint and reports an authentication failure in the settings screen. A
misconfiguration becomes visible where it is made instead of failing silently per request, and
sessions keep being served by the fallback meanwhile.

Local endpoints are commonly reached over cleartext HTTP, which Android blocks by default
(targetSdk 28+). A `network_security_config.xml` keeps the secure default for the internet
(`cleartextTrafficPermitted="false"`) and permits cleartext only for `localhost`, `127.0.0.1`,
and the emulator host alias `10.0.2.2` — the names documented for a local server, an `adb
reverse`-forwarded proxy, and the emulator's host. A local endpoint on any other host must use
HTTPS or be listed in that config; opening cleartext globally was rejected as disproportionate
for a security change.

### D4. At-rest protection: platform keystore + AES-GCM, no new dependency

The credential is encrypted with AES-GCM under a key generated in the Android Keystore (alias
owned by the app, non-exportable), and the ciphertext is written to a dedicated private file
rather than into the shared preferences DataStore — a separate file keeps the backup-exclusion
boundary (D5) obvious and keeps the secret out of the general settings blob.

Alternatives considered:

- `androidx.security:security-crypto` (`EncryptedSharedPreferences`): the library is
  deprecated and unmaintained; adding a stale dependency is worse than ~40 lines over
  `javax.crypto` and `KeyGenParameterSpec`, which `minSdk 26` fully supports.
- Plain DataStore: rejected; plaintext at rest, and the previous review already rejected it.
- In-memory only, no persistence: strictly stronger (no secret on disk at all), but it forces
  re-entry after every process death, which is friction without a matching reduction in a
  single-user threat model. Kept as a possible refinement, not the default.

### D5. Backup stays on; only the credential file is excluded

`allowBackup` remains `true` so decks, reviews, and reminders survive a device migration, and
the credential file is excluded through backup rules for both cloud backup and device-to-device
transfer (the `dataExtractionRules` attribute is required from API 31; the legacy
`fullBackupContent` applies below it). A Keystore-wrapped ciphertext is useless on another
device anyway — the wrapping key is non-exportable and never leaves the device — so the
exclusion is a second layer rather than the only one.

### D6. Composition becomes dynamic, the contract does not

```
CoachAgent (unchanged port)
  ^
  |  TimeoutCoachAgent (unchanged bound)
  |    ^
  |    |  RuntimeConfiguredCoachAgent
  |    |      reads the current configuration per request
  |    |      +-- endpoint + model saved --> FailoverCoachAgent(LlmCoachAgent, DeterministicCoachAgent)
  |    |      +-- nothing saved         --> DeterministicCoachAgent
```

The configuration port exposes a `Flow<CoachConfiguration?>`, and the composed agent consults
the current value per request. Saving or clearing settings therefore takes effect without a
process restart, and the test seams stay as they are: the adapter is still constructed from an
immutable `LlmCoachConfig` value.

Alternative considered: rebuild the object graph when settings change. Hilt does not support
graph replacement at runtime, and the mutable-provider workarounds are more machinery than
reading a flow per request.

### D7. Settings surface

A "AI coach" section in the existing settings screen with: endpoint, model, credential
(masked), Save, Check connection, Clear. States exposed to the UI: nothing saved / endpoint and
model saved without a credential / saved with a credential set. The stored credential's
characters are never read back into the form; replacing means typing a new value, and clearing
removes it. Values are not written to logs, and failure text never includes the credential — the
adapter already avoids echoing response bodies, and the new code keeps endpoint and credential
out of its error messages.

### D8. The invariant is enforced by a test, not by documentation

A unit test asserts that the app exposes no build-time coach configuration (no `BuildConfig`
field for endpoint, model, or credential) and that an install with no saved configuration
issues no coach request. The existing `CoachModuleTest` — which assumes the build is
unconfigured and skips itself otherwise — is replaced by a real check against the runtime store.
This is a test in the existing JVM suite, not a CI pipeline, because the project has no CI.

### D9. No key rotation is required

Evidence: `local.properties` was never committed (`git log -- local.properties` is empty), the
two published artifacts carried no keys (`v0.1.1-alpha` predates the adapter; `v0.2.0-alpha` was
built with empty `LLM_*` fields), and the adapter's live smoke check ran against a local
keyless endpoint. The record is part of the change so a future reader does not have to re-derive
it — and so nobody rotates a key unnecessarily.

## Risks / Trade-offs

- [A credential can still be extracted from a rooted or instrumented device] → Accepted residual
  risk of any client-side BYOK; documented. Nothing on the device is plaintext, and the value
  never belongs to anyone but that device's user.
- [The credential could leak into a log, crash report, or failure message] → No value in
  messages; masked, write-only UI; a test asserts a failure message excludes the credential.
- [The credential file could still be picked up by backup] → Both backup paths are excluded, and
  the wrapping key is non-exportable, so a restored ciphertext is useless.
- [Configured developers lose the remote coach on upgrade] → Expected and harmless: the offline
  coach keeps sessions working, and the design documents where to re-enter the configuration.
- [Developers lose the build-time convenience for local endpoint testing] → Input hints and a
  documented one-time entry; a persisted configuration survives app restarts.
- [Two credential-management paths during the transition (docs still describing
  `local.properties`) could confuse] → The build-time path is removed and its documentation
  rewritten in the same change; nothing reads `local.properties` for coach settings afterwards.
- [Removing `BuildConfig` fields could break something that reads them] → Today only
  `CoachModule` and one test do; the change updates both, and the guard test covers regressions.

## Migration Plan

1. Remove the build-time path (`coachConfiguration`, the three `buildConfigField`s, the docs
   that instruct its use).
2. Add the configuration port, the keystore-backed store, and the backup exclusion.
3. Add the settings surface and the dynamic composition.
4. Replace the affected tests, add the guard test, and update the remote-coach documentation.
5. Rollback: reverting the change restores the previous composition. There is no persisted data
   to migrate in either direction, and the offline coach is available at every point, so the app
   is never left in a non-functional state.

Deployment ordering has no constraint: this is a single-artifact app, and the previous build's
configuration simply stops being read.

## Open Questions

- Whether to offer an in-memory-only mode ("do not remember the credential on this device") in
  addition to the stored one. It changes no requirement and no task here.
- Whether a debug-only indicator naming the active coach (offline vs configured model) is worth
  adding. Earlier notes raised the same question; it stays deferred.
- Whether the artifact guard should also run in CI once the project has a pipeline.
