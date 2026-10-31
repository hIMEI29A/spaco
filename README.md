# SpaCo

Adaptive Learning / Practice Coach Android app.

SpaCo is an offline, text-only spaced-repetition practice coach. You group
practice items into decks by skill, run short sessions where an AI coach picks
what to practise next, and track your due count, accuracy, and streak. Everything
works with no network connection.

## Current version

`0.3.0-alpha` — see the [releases](https://github.com/hIMEI29A/spaco/releases)
page for the download.

## Requirements

- **JDK 17** (required by AGP 9.5.0)
- **Android SDK** with:
  - `platforms;android-37.2` (the project's `compileSdk`)
  - `build-tools;37.0.0`
  - `platform-tools` and `emulator`
- An API 26+ device or emulator. The reference emulator is
  `OpenTUI_Pixel_8_API_36`.

Install the SDK packages with the `android` CLI and point `ANDROID_HOME` at the
SDK:

```bash
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

android sdk install "platforms;android-37.2" "build-tools;37.0.0"
```

Gradle itself does not need to be installed: the committed wrapper downloads the
pinned version. See `docs/dev-setup.md` for the full toolchain and for how the
build is wired.

## Build

```bash
./gradlew :app:assembleDebug      # build the debug APK
```

The APK is written to:

```
app/build/outputs/apk/debug/app-debug.apk
```

## Install and run

```bash
./gradlew :app:installDebug       # build and install on the running device/emulator
adb shell am start -n com.spaco.app/.MainActivity
```

Start the emulator first if needed:

```bash
emulator -avd OpenTUI_Pixel_8_API_36 -no-boot-anim -gpu swiftshader_indirect
```

## Install on a physical phone

Build the debug APK, copy it to the phone, and open it there (or install it over
USB). The debug APK is self-contained and does not need the SDK or a network
connection.

**1. Build the APK**

```bash
./gradlew :app:assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

**2a. Install over USB (recommended)**

Enable *Developer options → USB debugging* on the phone, connect it, and verify
the device is visible:

```bash
adb devices            # your phone should appear as "device"
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**2b. Install by transferring the file**

If USB debugging is not an option, transfer `app-debug.apk` to the phone
(USB storage, cloud drive, messenger, etc.), then open it from the phone's Files
app and confirm the install prompt. Android will ask you to allow installing
unknown apps for the app you open it from — allow it once, install, then it is
safe to turn back off.

Requirements on the phone:

- Android 8.0 (API 26) or newer.
- "Install unknown apps" permitted for the file manager / browser you use to
  open the APK.

Notes:

- This is a **debug** build, signed with the debug keystore. It is fine for
  personal sideloading but not for distribution (Play Store or handing to
  others). A release build would use `./gradlew :app:assembleRelease` with a
  proper signing config.
- Since the app is offline by default and text-only, after installing it works
  with no network and stores everything on the device. See *Remote coach* below
  for the optional setting that changes that.

## Remote coach (optional)

Practice is served by a built-in offline coach until you configure a remote one
**in the app**: open *Settings → AI coach* and enter the endpoint, a model, and —
only if your endpoint asks for one — a credential. Saving applies immediately: no
rebuild, no reinstall.

```text
Endpoint    https://api.openai.com    server root, not the full path
Model       gpt-4o-mini
Credential  sk-...                    optional; sent as the Authorization bearer value
```

The app then asks that OpenAI-compatible endpoint (`POST
{endpoint}/v1/chat/completions`) to choose what to practise, generate an
exercise, and explain an answer. Behaviour notes:

- A fresh install has nothing configured and makes no network request until you
  save a configuration.
- An endpoint that needs no credential is a valid configuration: leave the
  credential empty and no authorization value is sent. A local server reached over
  cleartext HTTP must be `localhost`, `127.0.0.1`, or `10.0.2.2` (the emulator's
  host); any other endpoint must use HTTPS.
- **Check connection** probes the endpoint once and tells you when it rejected the
  credential, so a wrong or missing credential shows up where you set it rather
  than silently failing every request.
- The credential is stored encrypted under a key held in the Android Keystore,
  is excluded from device backups, is never shown back to you after saving, and
  is never part of the APK. It exists only on the device where you entered it;
  **Clear** removes it and returns the app to the offline coach.
- Whenever the endpoint is unreachable, slow, or returns something unusable, the
  request is answered by the built-in coach and the session continues.
- Grading feedback is shown before the explanation arrives, so a slow model
  never delays the verdict.
- Only the content a request needs leaves the device: the item's prompt and
  expected answer, sibling answers used as distractors, scheduling state, and
  recent review outcomes.

## Test

```bash
./gradlew test                    # JVM unit tests (JUnit4 + Truth + Robolectric)
./gradlew connectedAndroidTest    # instrumented UI tests on the connected device/emulator
```

The instrumented tests exercise the offline flows end to end on a device:
deck management (including a restart), the full offline practice path with a
progress check, and resuming an interrupted session.

## Lint

```bash
./gradlew lint                    # Android Lint; fails the build on errors
```
