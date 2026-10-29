# skill-deck-management Specification

## Purpose

Lets a learner organize what they study: create skills and decks, manage the practice items inside a deck, and choose which deck to practice. It owns the local item model every other capability reads.

## Requirements

### Requirement: Learner can create a deck

The system SHALL let the learner create a deck with a name, and SHALL persist the deck locally so it is available immediately and after the app restarts.

#### Scenario: Create a deck with a valid name
- **WHEN** the learner submits a new deck with a non-empty name
- **THEN** the system persists the deck, shows it in the deck list, and selects it as the active deck

#### Scenario: Reject a deck with an empty name
- **WHEN** the learner submits a new deck whose name is empty or whitespace only
- **THEN** the system rejects the submission, shows a validation message, and creates no deck

### Requirement: Decks belong to a skill

The system SHALL let the learner create a skill with a non-empty name and SHALL associate each deck with exactly one skill.

#### Scenario: Create a deck inside a new skill
- **WHEN** the learner creates a deck and supplies a skill name that does not exist yet
- **THEN** the system creates the skill and associates the deck with it

#### Scenario: A deck always has a skill
- **WHEN** the deck list is displayed
- **THEN** every deck is grouped under the skill it belongs to, and no deck is shown without a skill

### Requirement: Learner can add and edit practice items in a deck

The system SHALL let the learner add practice items to a deck, where each item has a required prompt, a required expected answer, and an optional explanation, and SHALL let the learner edit those fields afterwards.

#### Scenario: Add a valid item
- **WHEN** the learner saves an item with a non-empty prompt and a non-empty expected answer
- **THEN** the system persists the item in that deck, marks it as not yet reviewed, and makes it eligible for practice sessions

#### Scenario: Reject an incomplete item
- **WHEN** the learner saves an item with an empty prompt or an empty expected answer
- **THEN** the system rejects the submission, shows which field is missing, and persists no item

#### Scenario: Edit an existing item
- **WHEN** the learner changes the prompt, expected answer, or explanation of an existing item and saves
- **THEN** the system persists the new values and keeps the item's existing review history and schedule unchanged

### Requirement: Learner can select the active deck

The system SHALL let the learner select one deck as active, and SHALL remember that selection across app restarts.

#### Scenario: Select a deck
- **WHEN** the learner selects a deck from the deck list
- **THEN** the system marks it active, highlights it in the list, and uses it as the default target for starting a practice session

#### Scenario: Selection survives a restart
- **WHEN** the app is restarted after a deck was selected
- **THEN** the same deck is still the active deck

### Requirement: Learner can rename and archive a deck

The system SHALL let the learner rename a deck and archive a deck. Archiving MUST hide the deck from the deck list and from practice selection while preserving its items, review history, and schedule.

#### Scenario: Rename a deck
- **WHEN** the learner renames a deck to a non-empty name
- **THEN** the system persists the new name and shows it in the deck list

#### Scenario: Archive a deck
- **WHEN** the learner archives a deck
- **THEN** the system removes it from the deck list, excludes it from practice selection and due-review counts, and keeps its items and review history in local storage

#### Scenario: Restore an archived deck
- **WHEN** the learner restores an archived deck
- **THEN** the system makes the deck visible in the deck list again with its items, review history, and schedule intact

### Requirement: Deck data is stored locally and survives restart

The system SHALL store skills, decks, and items in local on-device storage and MUST NOT require network access to read, create, or modify them.

#### Scenario: Offline deck management
- **WHEN** the device has no network connectivity
- **THEN** the learner can still create, view, edit, rename, archive, and restore skills and decks

#### Scenario: Restart preserves content
- **WHEN** the app process is terminated and relaunched
- **THEN** all previously created skills, decks, and items are still present with their saved values
