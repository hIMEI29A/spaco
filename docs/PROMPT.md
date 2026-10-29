You are the OpenSpec Plan / Architect agent. Work only on OpenSpec artifacts. Do not write or modify any application source code, build files, or non-spec files.

Project: Adaptive Learning / Practice Coach (Android)

High-level description
An Android app for spaced-repetition learning (languages, coding concepts, instruments, etc.) that:
- Adapts the next practice session based on the user’s performance history
- Generates personalized exercises and explanations on the fly
- Uses an AI coach agent that observes performance data, decides what to practice next, and produces content
- Supports offline media handling and background scheduling of reviews

Core technical focus (must be reflected in the specs and design):
- Jetpack Compose with custom animations
- Room for local progress / review history
- Background scheduling of review sessions (WorkManager or equivalent)
- Offline-first media handling
- Clean architecture that keeps the AI coach agent pluggable (planning loop + feedback incorporation)

Growth path (document as future capabilities, not in the first change):
- Multi-skill curricula
- Collaborative study groups
- Export of progress into a larger learning-agent system

Development environment notes (for context only):
- OpenCode v2.1.4 + OpenSpec v1.14.3
- Target: modern Android (Kotlin, Compose, Material 3)
- Machine: MacBook Air M1, 16 GB RAM

Task
Create the first OpenSpec change for the MVP of this app.

1. Choose a clear kebab-case change name (e.g. `add-adaptive-practice-coach-mvp` or similar).
2. Scaffold the change directory under `openspec/changes/<change-name>/` with the standard files:
   - `.openspec.yaml`
   - `proposal.md`
   - `specs/` (one or more capability specs with SHALL requirements + WHEN/THEN scenarios)
   - `design.md` (architecture, data model, key components, AI coach agent loop, offline & scheduling strategy)
   - `tasks.md` (ordered, concrete implementation checklist)

3. In `proposal.md` cover:
   - Why this change exists
   - What we are building in the MVP
   - Scope boundaries (what is explicitly out of scope for this change)
   - Success criteria

4. In the specs, focus on observable user-facing and system behavior for:
   - Creating / selecting a skill or deck
   - Running an adaptive practice session
   - Recording performance and updating the spaced-repetition schedule
   - Receiving AI-generated exercises / explanations
   - Offline operation and background review reminders
   - Basic progress visualization

5. In `design.md` outline:
   - Recommended package / module structure
   - Room entities & DAOs for progress and review history
   - Compose UI structure and key custom animations
   - Background work strategy
   - How the AI coach agent will be integrated (simple planning loop + performance feedback)
   - Offline media strategy
   - Key trade-offs and open questions

6. Keep tasks small, verifiable, and ordered so a later `/opsx:apply` (or equivalent) can implement them safely.
7. Define the technology stack for the project and check whether it is installed on the system. If it is not possible to perform this check, inform the user which libraries or frameworks are required and how to install them.

After creating the artifacts, summarize:
- The chosen change name
- Key decisions made
- Any open questions that need my input before we approve the change

Start by creating the change and writing the proposal. Ask clarifying questions only when they materially affect the MVP scope or architecture.
