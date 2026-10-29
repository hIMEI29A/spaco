# Spec Delta

## Purpose

Defines the behavior the app relies on from its AI coach — choosing what to practice next and generating exercises and explanations — behind a replaceable contract that works offline and degrades safely.

## ADDED Requirements

### Requirement: Coach selects the next item to practice

The system SHALL be able to ask the coach to choose the next item for a practice session given the deck's items, their scheduling state, and the learner's recent performance, and MUST treat the coach's choice as advisory by validating it against the session's deck.

#### Scenario: Coach returns an eligible item
- **WHEN** the system asks the coach for the next item and the coach returns the identity of an item that belongs to the session's deck and has not been answered in the session
- **THEN** the system accepts that item as the next item

#### Scenario: Coach returns an ineligible item
- **WHEN** the coach returns an item that is not in the session's deck, does not exist, or was already answered in the session
- **THEN** the system rejects the choice and falls back to the scheduler's due order

#### Scenario: Coach is not consulted when unnecessary
- **WHEN** a session has no eligible items left to present
- **THEN** the system ends the session instead of requesting a further choice from the coach

### Requirement: Coach generates an exercise for a target item

The system SHALL be able to ask the coach to produce the exercise presented for a target item — its prompt, its expected answer, and optional hints — and the generated exercise MUST remain attributable to that target item.

#### Scenario: Generating an exercise
- **WHEN** the system asks the coach to generate an exercise for a target item in a deck
- **THEN** the coach returns a prompt, an expected answer, and the identity of the target item the exercise was generated for

#### Scenario: Generated variant is used for presentation only
- **WHEN** a generated exercise is presented and graded
- **THEN** the recorded review event refers to the target item, and the item's stored content is not overwritten by generated text

#### Scenario: No generation requested
- **WHEN** the coach does not return a usable exercise for the target item
- **THEN** the system presents the item's stored prompt and expected answer instead

### Requirement: Coach generates an explanation shown after grading

The system SHALL be able to ask the coach for an explanation of why the expected answer is correct for a target item, and MUST show it with the post-answer feedback.

#### Scenario: Explanation available
- **WHEN** an answer has been graded and the coach returns an explanation for the target item
- **THEN** the system shows that explanation alongside the expected answer

#### Scenario: Explanation unavailable
- **WHEN** an answer has been graded and the coach returns no explanation
- **THEN** the system shows the item's stored explanation if one exists, and otherwise shows feedback without an explanation

### Requirement: Coach output is validated before use

The system SHALL validate coach output before presenting it and MUST discard any output that is empty, malformed, or not attributable to the requested target item.

#### Scenario: Empty or malformed output
- **WHEN** the coach returns an exercise with an empty prompt or an empty expected answer, or returns output that cannot be parsed into the expected fields
- **THEN** the system discards it and falls back to the item's stored content

#### Scenario: Mismatched target
- **WHEN** the coach returns an exercise whose target item identity does not match the requested item
- **THEN** the system discards it and falls back to the item's stored content

#### Scenario: Feedback is always shown
- **WHEN** any coach request fails validation or times out
- **THEN** the learner still receives immediate grading feedback, and the session is not interrupted

### Requirement: Coach works without network access

The system SHALL be able to satisfy every coach request while the device is offline, and MUST NOT make the practice session wait on a coach response for longer than a bounded timeout.

#### Scenario: Offline session
- **WHEN** the device has no network connectivity and the learner runs a practice session
- **THEN** the system completes the session with usable exercises, explanations, and grading

#### Scenario: Bounded wait
- **WHEN** a coach request does not return within the configured timeout
- **THEN** the system proceeds with its fallback content instead of blocking the session

### Requirement: The coach is replaceable without changing session or scheduling behavior

The system MUST depend on the coach only through its request-and-response contract, so that a different coach implementation can be substituted without changing practice-session flow, review recording, or scheduling.

#### Scenario: Substitute another coach
- **WHEN** the coach implementation is replaced with another that satisfies the same contract
- **THEN** practice sessions, recorded review events, and scheduling state are produced with the same externally observable behavior

#### Scenario: Coach is unavailable at startup
- **WHEN** no coach implementation can be initialized
- **THEN** the app still starts and supports deck management, practice with stored content, scheduling, and progress
