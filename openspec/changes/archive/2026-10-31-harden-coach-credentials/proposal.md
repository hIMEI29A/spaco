# Proposal

## Why

The remote coach takes its endpoint, model, and credential from `local.properties` at
build time, so the credential is compiled into the APK as a `BuildConfig` constant. Any
copy of a configured build yields it with `strings` or `jadx`; one key is shared by every
installation of that build; and the only thing preventing publication is a documentation
rule — while this project's own release process attaches a freshly built debug APK to
every GitHub Release. The project is a personal pet-project today and may grow into a
public product, so the artifact must be provably key-free rather than key-free as long as
nobody forgets.

## What Changes

- **BREAKING (build configuration)**: `LLM_BASE_URL`, `LLM_MODEL`, and `LLM_API_KEY` are
  removed from `BuildConfig`, and the build no longer reads coach settings from
  `local.properties`. The credential cannot be present in a built artifact because no
  build-time path produces one.
- Coach settings — endpoint, model, credential — are entered on the device and take effect
  without rebuilding or restarting the app.
- The credential is stored encrypted with a key held in the Android Keystore and excluded
  from Android Auto Backup.
- The credential is write-only in the interface: it can be entered, replaced, and deleted,
  and is never displayed, logged, or included in diagnostics.
- An endpoint that needs no credential becomes a valid configuration (local
  OpenAI-compatible servers), replacing the current "all three or nothing" rule with an
  explicit connection check that surfaces authentication failures.
- Coach composition becomes dynamic: the current configuration is resolved per request
  rather than once at dependency-injection time, so clearing it returns the app to the
  built-in offline coach.
- Documentation that told readers to put a key in `local.properties` is removed.
- A guard test fails the build if any build-time coach configuration reappears.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `llm-coach-adapter`: remote coaching becomes opt-in through configuration supplied on the
  device instead of at build time; the credential requirement becomes a runtime credential
  lifecycle (device entry, encrypted at rest, excluded from backup, write-only in the
  interface, absent from the artifact and from diagnostics); and the capability gains
  requirements for credential-less endpoints, reconfiguration without a rebuild, and an
  artifact that carries no build-time coach configuration.

## Impact

- Build: `app/build.gradle.kts` loses the `coachConfiguration` reader and the three
  `buildConfigField` entries.
- Wiring: `CoachModule` resolves the coach through the runtime configuration port instead
  of `BuildConfig`.
- New code: a domain port for coach configuration, a Keystore-backed credential store, and
  a composition that reads configuration per request.
- UI: a coach section in `feature/settings` with endpoint, model, credential entry,
  connection check, and clear.
- Manifest/backup: the stored credential is excluded from backup while decks and progress
  stay backed up.
- Docs: the remote-coach sections of `README.md` and `docs/dev-setup.md` are rewritten
  around runtime entry.
- Tests: configuration resolution, credential storage, settings behavior, and the new
  artifact guard; the current `CoachModuleTest` assumption that the build is unconfigured
  is replaced by a real "no configuration means no request" check.
- No new dependencies and no new permissions; the offline path and the `CoachAgent`
  contract are unchanged.
