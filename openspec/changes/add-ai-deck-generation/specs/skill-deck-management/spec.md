# Spec Delta

## ADDED Requirements

### Requirement: Learner can create a deck from a topic

The system SHALL let the learner create a deck by supplying a topic and accepting generated drafts. On acceptance, the deck and its accepted items MUST be created locally and MUST behave like manually created ones.

#### Scenario: Create a deck from a topic

- **WHEN** the learner supplies a topic, generation succeeds, and the learner accepts at least one draft
- **THEN** the system creates a deck locally - and a skill if a skill name was supplied - containing one item per accepted draft

#### Scenario: Generated items behave like manual ones

- **WHEN** a deck created from a topic is used
- **THEN** its items are eligible for practice and scheduling, and count toward progress, exactly like manually added items

### Requirement: Generated items are reviewed before they count

The system SHALL let the learner accept, edit, or reject each generated draft, and MUST add only accepted drafts to the deck.

#### Scenario: Reject a draft

- **WHEN** the learner rejects a generated draft
- **THEN** the system creates no item for it and it does not enter the deck

#### Scenario: Edit and then accept a draft

- **WHEN** the learner edits a draft's prompt or expected answer and accepts it
- **THEN** the persisted item uses the edited values

#### Scenario: Atomic promotion of accepted drafts

- **WHEN** the learner confirms the accepted set
- **THEN** all accepted drafts are persisted together, or none are persisted if the commit fails
