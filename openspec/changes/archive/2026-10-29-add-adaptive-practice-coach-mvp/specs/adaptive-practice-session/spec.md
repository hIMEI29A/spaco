# Spec Delta

## Purpose

Defines what the learner experiences during a practice session: how a session starts, how the next item is chosen, how answers are submitted and graded, and how feedback and a session summary are shown.

## ADDED Requirements

### Requirement: Learner can start a practice session

The system SHALL let the learner start a practice session for a deck, and the session MUST have a bounded number of items rather than running indefinitely.

#### Scenario: Start a session with due items
- **WHEN** the learner starts a session for a deck that has items available for practice
- **THEN** the system opens the session, shows the first item, and displays progress within the session

#### Scenario: Start a session with nothing to practice
- **WHEN** the learner starts a session for a deck whose items are all scheduled in the future
- **THEN** the system informs the learner that nothing is due, offers to practice ahead anyway, and starts no session until the learner chooses

#### Scenario: Empty deck
- **WHEN** the learner starts a session for a deck that has no items
- **THEN** the system informs the learner that the deck has no items and offers to add items instead of starting a session

### Requirement: The next item is chosen adaptively

The system SHALL determine the next item of a session from the deck's scheduling state and the learner's performance history, and MUST continue the session when the coach cannot provide a choice by falling back to the scheduler's due order.

#### Scenario: Coach provides the next item
- **WHEN** the session requests the next item and the coach returns an item belonging to the session's deck
- **THEN** the system presents that item next

#### Scenario: Coach unavailable
- **WHEN** the session requests the next item and the coach is unavailable, fails, or returns no usable item
- **THEN** the system presents the next item in due order without interrupting the session

#### Scenario: No item is repeated within one session
- **WHEN** the session selects the next item and that item has already been answered in the current session
- **THEN** the system selects a different item, or ends the session if no other eligible item remains

### Requirement: Learner submits an answer and receives immediate feedback

The system SHALL let the learner submit an answer for the presented item and MUST immediately show whether it was correct, the expected answer, and the item's explanation when one exists.

#### Scenario: Correct answer
- **WHEN** the learner submits an answer that the system grades as correct
- **THEN** the system shows a correct-feedback state, reveals the expected answer, and enables continuing to the next item

#### Scenario: Incorrect answer
- **WHEN** the learner submits an answer that the system grades as incorrect
- **THEN** the system shows an incorrect-feedback state together with the expected answer and the item's explanation, and enables continuing to the next item

#### Scenario: Empty submission is blocked
- **WHEN** the learner submits an empty answer for an item that is graded automatically
- **THEN** the system does not grade it, keeps the item on screen, and prompts the learner to provide an answer

### Requirement: Learner can self-grade a free-recall answer

When an item's answer cannot be checked automatically, the system SHALL let the learner grade their own attempt as correct or incorrect, and MUST record that grade as the session result for the item.

#### Scenario: Self-grade correct
- **WHEN** the learner reveals the expected answer for a free-recall item and marks their attempt correct
- **THEN** the system records the attempt as correct for that item

#### Scenario: Self-grade incorrect
- **WHEN** the learner reveals the expected answer for a free-recall item and marks their attempt incorrect
- **THEN** the system records the attempt as incorrect for that item

### Requirement: Session ends with a summary and recorded results

The system SHALL end a session when its item budget is exhausted or the learner ends it early, and MUST report how many items were answered and how many were correct.

#### Scenario: Complete the planned session
- **WHEN** the learner answers the last item of the session's item budget
- **THEN** the system ends the session and shows a summary with the number answered and the number correct

#### Scenario: End early
- **WHEN** the learner ends the session before answering every planned item
- **THEN** the system ends the session, shows a summary covering only the answered items, and keeps the results already recorded

#### Scenario: All results are persisted
- **WHEN** a session ends for any reason
- **THEN** every graded answer given during the session is available to the scheduling and progress capabilities

### Requirement: An interrupted session can be resumed

The system SHALL persist in-progress session state so that leaving the app during a session does not lose recorded answers, and MUST let the learner resume or discard the interrupted session.

#### Scenario: Resume an interrupted session
- **WHEN** the learner returns to the app after a session was interrupted before it ended
- **THEN** the system offers to resume the session at the next unanswered item with all earlier answers still recorded

#### Scenario: Discard an interrupted session
- **WHEN** the learner discards an interrupted session
- **THEN** the system ends it, keeps the results already recorded, and shows the session summary

### Requirement: Session presentation uses animated transitions

The system SHALL animate the transitions between requesting an item, revealing feedback, and advancing to the next item, and MUST NOT block or delay answer submission and grading while an animation is running.

#### Scenario: Animated feedback reveal
- **WHEN** an answer is graded
- **THEN** the feedback state is revealed with an animation and the session's controls become available without waiting for the animation to finish
