# Development setup

Everything here is verified on the reference machine (MacBook Air M1, macOS, Homebrew).
Commands are listed in the order you need them the first time.

## 1. Toolchain

| Component | Version used | Notes |
|---|---|---|
| JDK | 17 (Zulu 17.0.21) | Required by AGP 9.5.0 |
| Android SDK | `/opt/homebrew/share/android-commandlinetools` | `ANDROID_HOME` / `ANDROID_SDK_ROOT` |
| Gradle | **not installed system-wide** | The committed wrapper pins and downloads Gradle 9.8.2 |
| Android Gradle Plugin | 9.5.0 | Pinned in `gradle/libs.versions.toml` |
| Kotlin (KGP) | 2.4.21 | Forced in the root `build.gradle.kts` (see “Why the Kotlin version is forced”) |
| Android platform | `android-37.2` | `compileSdk` 37; `targetSdk` is 36 |
| Build tools | `37.0.0` | |
| Android Studio | not required | The CLI plus an emulator is enough |

## 2. One-time SDK setup

```bash
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

android sdk install "platforms;android-37.2" "build-tools;37.0.0"
```

`android` is the supported successor to the deprecated `sdkmanager`. If the SDK lives
elsewhere, either export `ANDROID_HOME` as above or create an untracked
`local.properties` containing `sdk.dir=/path/to/sdk`.

## 3. Build, install, run

```bash
./gradlew :app:assembleDebug              # build the debug APK
./gradlew :app:installDebug               # build and install on the running device/emulator
adb shell am start -n com.spaco.app/.MainActivity
```

The wrapper downloads Gradle 9.8.2 on first use, so no system Gradle is needed.

## 4. Emulator

```bash
emulator -avd OpenTUI_Pixel_8_API_36 -no-boot-anim -gpu swiftshader_indirect
adb wait-for-device
adb shell getprop sys.boot_completed      # prints 1 once booted
```

The AVD runs API 36, which is why `targetSdk` is 36 even though the project compiles
against API 37.

## 5. Tests and lint

```bash
./gradlew test                # JVM unit tests (JUnit4 + Truth + MockK + Turbine + Robolectric)
./gradlew connectedAndroidTest  # instrumented tests (Compose UI, Room) on the emulator
./gradlew lint
```

## 6. How the build is wired (and why)

- **Built-in Kotlin.** AGP 9 compiles Kotlin itself, so `org.jetbrains.kotlin.android` is
  never applied in this project. AGP 9's notes state that plugin is incompatible with the
  new DSL; opting out of built-in Kotlin is therefore not a route this project takes.
- **Why the Kotlin version is forced.** AGP 9.5.0 has a runtime dependency on KGP 2.2.10.
  Current Hilt releases ship Kotlin 2.3 class metadata, which a 2.2.10 compiler cannot
  read, so the root `build.gradle.kts` adds `kotlin-gradle-plugin:2.4.21` to the buildscript
  classpath. Gradle's usual “newest version wins” resolves KGP and `kotlin-stdlib` to
  2.4.21. Keep that hardcoded version in sync with `kotlin` in `gradle/libs.versions.toml`.
  The version catalog accessor is not available inside a `buildscript {}` block, which is
  why the number is repeated there.
- **`compileSdk` 37, `targetSdk` 36.** Current AndroidX libraries (`core-ktx 1.19.x`,
  Compose `1.12.x`) declare `minCompileSdk = 37`. `targetSdk` stays at 36 so runtime
  behavior matches the API 36 emulator this project tests on.
- **KSP, not KAPT.** `kotlin-kapt` is incompatible with built-in Kotlin. Room and Hilt both
  run through KSP (see `ksp(...)` dependencies in `app/build.gradle.kts`).
- **Memory.** `gradle.properties` caps the Gradle and Kotlin daemon heaps so a build and the
  emulator can run on a 16 GB machine; running a full build and the emulator at the same
  time is still best avoided.

## 7. Review reminders (manual verification)

Background reminders are scheduled with WorkManager: a periodic sweep (every
12 hours) plus a one-time check delayed to the earliest `nextDueAt`. WorkManager
persists this work and its own `RescheduleReceiver` re-registers it after a
reboot, so nothing extra is needed in the app manifest.

To verify by hand on the emulator:

```bash
# 1. Schedule reminders by running any session to completion (or re-open the app).
#    Then confirm the work is registered:
adb shell dumpsys jobscheduler | grep -i spaco

# 2. Simulate a reboot; WorkManager restores the persisted work:
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -p com.spaco.app

# 3. Force the periodic sweep to run now and check for the notification:
adb shell cmd jobscheduler run -f com.spaco.app -1
```

To verify the "blocked by system" path, deny the notification permission when
prompted on first launch. The settings screen then shows the blocked state, no
notification is posted, and every other screen keeps working normally.

## 8. Troubleshooting

- **“Dependency X requires libraries and applications that depend on it to compile against
  version 37 or later”** — a dependency raised its `minCompileSdk` above the project's
  `compileSdk`. Install the required platform and raise `compileSdk` (keeping `targetSdk`
  where it is).
- **“Class file was compiled with a newer version of Kotlin”** — a dependency was built with
  a Kotlin version newer than the KGP in use. Raise the forced KGP version in the root
  `build.gradle.kts` and the `kotlin` entry in the version catalog together.
- **“Cannot add extension with name 'kotlin'”** — something applied
  `org.jetbrains.kotlin.android`. Remove it; AGP 9's built-in Kotlin already provides the
  `kotlin` extension.
