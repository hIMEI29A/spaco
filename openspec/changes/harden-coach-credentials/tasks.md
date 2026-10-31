# Tasks

## 1. Configuration port and credential storage

- [x] 1.1 Add the coach configuration model and its domain port: endpoint, model, optional credential, exposed as the current configuration; verify unit tests cover that an endpoint and a model are both required and that a credential is optional
- [ ] 1.2 Implement the keystore-backed store: AES-GCM under a non-exportable Android Keystore key, ciphertext in a dedicated private file; verify a round-trip test saves and reads the configuration back through a new store instance, and that the raw stored file does not contain the credential text
- [ ] 1.3 Implement replace and clear on the store; verify tests show that clearing yields no configuration, that later reads return empty, and that a replaced credential is the one used

## 2. Dynamic composition and removal of the build-time path

- [x] 2.1 Implement the runtime-configured coach composition that reads the current configuration per request; verify tests show that with no saved configuration every request is served by the deterministic coach with no network use, and that with a saved configuration the remote path is used
- [x] 2.2 Rewire `CoachModule` to the configuration port, keeping the overall timeout bound; verify the existing coach tests pass and the bound still applies to the composed agent
- [x] 2.3 Remove `coachConfiguration` and the three `buildConfigField` entries from `app/build.gradle.kts`; verify `./gradlew --offline :app:assembleDebug` succeeds and the generated `BuildConfig` contains no coach configuration field
- [x] 2.4 Add the guard test that fails if any build-time coach configuration is exposed, and replace `CoachModuleTest`'s assumption-based check with a real "no configuration means no request" test; verify both run under `./gradlew test` and the assumption-based check is gone
- [x] 2.5 Rewrite the remote-coach sections of `README.md` and `docs/dev-setup.md` around on-device entry and delete the `local.properties` instructions; verify no reference to a build-time coach credential remains and the documented build/test commands run as written

## 3. Coach settings surface

- [x] 3.1 Add configuration state to `SettingsViewModel` — nothing saved, endpoint and model saved without a credential, credential set — plus save and clear; verify unit tests cover all three states and that clearing returns the app to the offline path
- [x] 3.2 Add the settings UI: endpoint, model, masked credential, Save, Clear, with test tags; verify Compose tests show that a saved credential value is never displayed and that replace and delete both work
- [x] 3.3 Add "check connection" as a bounded probe of the saved endpoint that reports an authentication failure explicitly; verify a test with a 401 response reports a rejected credential and that the message contains no credential value
- [x] 3.4 Extend the remote-coach documentation with the settings flow — where the configuration is entered, what each state means, and what "check connection" reports; verify the documented steps match the implemented labels and test tags

## 4. Artifact and backup hardening

- [x] 4.1 Exclude the credential file from cloud backup and device-to-device transfer, for both the current and the legacy backup rule formats; verify the built artifact's manifest and packaged backup rules exclude that file
- [x] 4.2 Verify a built debug APK carries no coach configuration: build with no configuration file present, then confirm the artifact contains no coach configuration field and no key material, and record the checks used

## 5. Integration verification

- [ ] 5.1 Run `./gradlew --offline test lint`; verify BUILD SUCCESSFUL with zero test failures and no new lint errors
- [ ] 5.2 Run `:app:connectedDebugAndroidTest` on the API 36 AVD with a default install; verify all instrumented tests pass
- [ ] 5.3 Manual end-to-end on the emulator: an unconfigured install completes a session offline without a coaching request; a saved credential-less local endpoint serves a session remotely; clearing the configuration returns to the offline path; verify each observation by hand and record the results
- [ ] 5.4 Confirm the credential survives an app restart but not a restore onto a clean install: verify a restart keeps the saved configuration usable and that a restore without the Keystore key leaves the credential empty, and record the residual-risk reasoning for a rooted or instrumented device

## Workflow follow-up

- Archive the change after the project's review requirements are satisfied.
- Reference issue #6 in the release pull request so it closes on merge.
