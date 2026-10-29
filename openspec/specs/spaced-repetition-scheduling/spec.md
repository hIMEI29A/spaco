# spaced-repetition-scheduling Specification

## Purpose

Turns each graded answer into durable review history and a next-due time, so the app always knows what the learner should practice next and when, without network access.

## Requirements

### Requirement: Every graded answer is recorded as a review event

The system SHALL record one review event per graded answer, containing at least the item identity, the time it was answered, whether it was graded correct, and whether the learner or the system graded it.

#### Scenario: Record a graded answer
- **WHEN** an answer is graded during a practice session
- **THEN** the system appends a review event for that item with the answer time and the correctness outcome

#### Scenario: History is append-only
- **WHEN** a later answer is recorded for an item that already has review events
- **THEN** the system adds a new event and does not modify or delete the earlier events

#### Scenario: History survives restart
- **WHEN** the app is restarted after reviews were recorded
- **THEN** all review events are still present and produce the same scheduling state

### Requirement: Each item exposes a next-due time and a learning state

The system SHALL maintain, per item, a learning state and a next-due time derived from that item's review events.

#### Scenario: New item
- **WHEN** an item has no review events
- **THEN** the system reports it in the new state and treats it as due immediately

#### Scenario: State is reported to the learner
- **WHEN** an item is displayed in a deck or practice list
- **THEN** the system shows the item's current learning state and whether it is due

### Requirement: A correct answer lengthens the interval

The system SHALL increase the time until an item is next due when that item is answered correctly, such that successive correct answers produce strictly growing intervals up to a configured maximum.

#### Scenario: First correct answer
- **WHEN** an item with no prior correct answer is answered correctly
- **THEN** the system sets a next-due time in the future and moves the item into a learning or reviewing state

#### Scenario: Repeated correct answers
- **WHEN** an item is answered correctly again on a later review
- **THEN** the system sets a next-due time further in the future than the previous interval

#### Scenario: Interval cap
- **WHEN** an item's computed interval exceeds the configured maximum
- **THEN** the system caps the interval at that maximum instead of growing it further

### Requirement: An incorrect answer shortens the interval and can lapse the item

The system SHALL set the item due again soon after an incorrect answer and MUST lower its learning state so the item reappears in upcoming sessions.

#### Scenario: Incorrect answer on a reviewing item
- **WHEN** an item in a reviewing or mastered state is answered incorrectly
- **THEN** the system marks it lapsed, sets its next-due time within the current session window or the next day, and stops counting it as mastered

#### Scenario: Incorrect answer on a new item
- **WHEN** a new item is answered incorrectly
- **THEN** the system keeps it due and schedules it for review again soon rather than at a long interval

### Requirement: Due items are exposed in a deterministic order

The system SHALL expose the items due for a deck ordered by how overdue they are, with the most overdue first, and MUST break ties deterministically so the same history always yields the same order.

#### Scenario: Overdue items come first
- **WHEN** a deck contains items whose due times have passed
- **THEN** the system lists them with the most overdue item first

#### Scenario: Order is stable for equal due times
- **WHEN** two items have the same due time
- **THEN** the system orders them by a stable tiebreaker and returns the same order on repeated reads

#### Scenario: Future items are excluded
- **WHEN** a session requests due items
- **THEN** the system excludes items whose next-due time is in the future unless the learner chose to practice ahead

### Requirement: Scheduling is reproducible from history

The system SHALL compute an item's state and next-due time purely from its review events, the item's stored content, and the configured scheduling parameters, with no dependence on the current time other than as an explicit input.

#### Scenario: Same history yields the same schedule
- **WHEN** the same review events and the same scheduling parameters are evaluated at the same reference time
- **THEN** the system produces the same learning state and the same next-due time

#### Scenario: Scheduling is deterministic across runs
- **WHEN** scheduling is recomputed for a deck without any new reviews
- **THEN** no item's state or next-due time changes
