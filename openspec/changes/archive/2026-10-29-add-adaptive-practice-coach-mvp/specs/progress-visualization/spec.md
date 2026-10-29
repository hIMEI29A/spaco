# Spec Delta

## Purpose

Shows the learner how their studying is going: what is due now, how each deck breaks down by learning state, and whether recent accuracy and their practice habit are improving.

## ADDED Requirements

### Requirement: Learner can see per-deck progress summary

The system SHALL show, for a selected deck, how many of its items are due now and how many are in each learning state, and MUST base those counts on the recorded review history and scheduling state.

#### Scenario: Deck summary is displayed
- **WHEN** the learner opens the progress view for a deck
- **THEN** the system shows the count of items due now and the counts of items in each learning state

#### Scenario: Counts match scheduling state
- **WHEN** the deck summary is shown
- **THEN** the number of items shown as due equals the number of items the scheduling capability currently reports as due for that deck

#### Scenario: Archived decks are excluded from activity totals
- **WHEN** the learner views an overall summary across decks
- **THEN** items belonging to archived decks are excluded

#### Scenario: Empty deck or no history
- **WHEN** the learner opens the progress view for a deck with no items or with no recorded reviews
- **THEN** the system shows zero counts and an explicit empty state instead of an error or placeholder numbers

### Requirement: Learner can see recent accuracy

The system SHALL show the learner's accuracy over a recent window of recorded reviews, and MUST state the window the accuracy is computed over.

#### Scenario: Accuracy over recent reviews
- **WHEN** the learner opens a progress view and reviews exist in the recent window
- **THEN** the system shows the percentage of correct reviews in that window together with the window description

#### Scenario: Accuracy with sparse history
- **WHEN** fewer reviews exist than the window size
- **THEN** the system computes accuracy over the reviews that exist and indicates that the history is limited

#### Scenario: No reviews yet
- **WHEN** no reviews have been recorded
- **THEN** the system shows that accuracy is not yet available instead of a zero or undefined percentage

### Requirement: Learner can see a practice streak

The system SHALL show the number of consecutive recent days on which the learner answered at least one review.

#### Scenario: Streak is shown
- **WHEN** the learner opens a progress view after answering reviews on consecutive days
- **THEN** the system shows the length of the current unbroken run of practice days

#### Scenario: Streak breaks
- **WHEN** a day passes with no answered reviews
- **THEN** the system resets the current streak to zero on the next day the learner practices

### Requirement: Progress reflects new answers immediately

The system SHALL update progress views as soon as a practice session records new answers, without requiring an app restart.

#### Scenario: Summary updates after a session
- **WHEN** the learner finishes a session and returns to the progress view
- **THEN** the due counts, learning-state counts, accuracy, and streak already include the answers from that session

#### Scenario: Progress reacts to background checks
- **WHEN** due items change because background work or time passage makes items due
- **THEN** the progress view reflects the updated due count on its next display without a restart

### Requirement: Progress presentation is animated

The system SHALL animate the progress view's numeric and chart updates, and MUST keep the values readable and complete while an animation is running.

#### Scenario: Animated count update
- **WHEN** a progress value changes after a session or a refresh
- **THEN** the system animates the change and the final resting value equals the current true value

#### Scenario: Animation does not hide data
- **WHEN** an animation is in progress
- **THEN** the learner can still read the current values and the view remains responsive
