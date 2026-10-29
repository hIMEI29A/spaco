# SpaCo

Adaptive Learning / Practice Coach Android app.

SpaCo is an offline, text-only spaced-repetition practice coach. You group
practice items into decks by skill, run short sessions where an AI coach picks
what to practise next, and track your due count, accuracy, and streak. Everything
works with no network connection.

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

## Install and run

```bash
./gradlew :app:installDebug       # build and install on the running device/emulator
adb shell am start -n com.spaco.app/.MainActivity
```

Start the emulator first if needed:

```bash
emulator -avd OpenTUI_Pixel_8_API_36 -no-boot-anim -gpu swiftshader_indirect
```

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
