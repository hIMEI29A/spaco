# Proposal

## Why

There is no working product yet: the repository contains only an OpenSpec scaffold. Learners who want to drill a skill (language vocabulary, coding concepts, instrument drills) currently have to assemble their own flashcard tooling, and static decks do not adapt when they keep failing or over-drilling material they already know. This change establishes the first runnable Android MVP for SpaCo: an offline-capable spaced-repetition app whose practice session is chosen from the learner's own performance history and whose exercises and explanations are produced by a pluggable AI coach.

## What Changes

- Bootstrap a new Android application project (Kotlin, Jetpack Compose, Material 3, Gradle Kotlin DSL with a committed wrapper) that builds and runs on the existing local SDK/emulator toolchain.
- Introduce local, offline-first persistence (Room) for skills, decks, items, review history, and scheduling state. No server dependency in the MVP.
- Add skill/deck management: create, rename, archive a skill or deck, and pick the active one to practice.
- Add an adaptive practice session: the app selects the next items, presents an exercise, accepts an answer, and shows immediate feedback with an explanation.
- Add spaced-repetition scheduling: every graded answer is recorded and updates that item's next-due time and difficulty state.
- Add a pluggable AI coach port that (a) plans what to practice next from performance history and (b) generates exercises and explanations; the MVP ships a deterministic offline generator behind this port.
- Add background review scheduling and reminders via WorkManager, with a notification when items become due.
- Add basic progress visualization: per-deck counts of due, learning, and mastered items, plus a short recent-accuracy trend and streak.
- Custom Compose animations for session transitions, card reveal, and answer feedback.

Explicitly **not** in this change (see Scope Boundaries): offline media/audio handling, real LLM provider integration, multi-skill curricula, collaborative study groups, and export into an external learning-agent system.

## Capabilities

### New Capabilities

- `skill-deck-management`: creating, selecting, renaming, and archiving skills/decks, and the item model the learner practices.
- `adaptive-practice-session`: running a practice session — what is presented, how an answer is submitted, and how immediate feedback and explanations are shown.
- `spaced-repetition-scheduling`: recording each graded answer and deriving the next-due time and per-item learning state.
- `ai-coach`: the pluggable coach contract for planning what to practice next and generating exercises/explanations from performance history, including offline/degraded behavior.
- `offline-review-scheduling`: fully offline operation of the above, plus background scheduling of due reviews and user-facing reminders.
- `progress-visualization`: per-deck progress summary, recent accuracy trend, and streak displayed to the learner.

### Modified Capabilities

- None. This is the project's first change and `openspec/specs/` is empty.

## Impact

- **New application code**: an Android project at the repository root (`app/` module plus Gradle build files and a committed Gradle wrapper) that does not exist today.
- **Toolchain / dependencies introduced**: Android Gradle Plugin, Kotlin/Compose compiler plugin, Compose BOM + Material 3, Navigation Compose, Room + KSP, WorkManager, Kotlin coroutines/Flow, `kotlinx-datetime` or `java.time`, and a test stack (JUnit, Turbine, Room testing, Robolectric or instrumented Compose tests).
- **Toolchain gaps to close before the build runs**: SDK platform and SDK build-tools were not installed; this change installs `platforms;android-37.2` and `build-tools;37.0.0`, and generates the Gradle wrapper with the installed Gradle 9.8.2. The app compiles against API 37 (`targetSdk` stays 36) because current AndroidX libraries require `minCompileSdk 37`. See `design.md` and `tasks.md` for the concrete bootstrap steps.
- **APIs / contracts**: the only externally visible contract is the local `CoachAgent` port; no network API is called in the MVP.
- **Existing systems**: none — no prior code, specs, or integrations to preserve.
- **Out of scope this change**: remote media download/caching, audio playback, cloud sync, multi-user features, and analytics export.
