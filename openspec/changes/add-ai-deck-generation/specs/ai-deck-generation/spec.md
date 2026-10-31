# Spec Delta

## Purpose

Lets the learner create a complete practice deck from a free-form topic: the coach proposes a deck name and a bounded set of items, and nothing enters the practice model until the learner reviews and accepts it.

## ADDED Requirements

### Requirement: Coach can generate a deck from a topic

The system SHALL be able to ask the coach to produce a complete deck from a free-form topic - a deck name, an optional skill name, and a bounded set of items, each with a prompt and an expected answer - through a generation request that is separate from the practice-time coach.

#### Scenario: Generate from a valid topic

- **WHEN** the learner supplies a non-empty topic and a remote coach is configured
- **THEN** the coach returns a deck name and a set of items, each with a non-empty prompt and a non-empty expected answer, not exceeding the requested item cap

#### Scenario: Empty topic

- **WHEN** the topic is empty or whitespace only
- **THEN** the system sends no generation request and shows a validation message

### Requirement: Generated content is staged as drafts

The system SHALL store generated items as drafts and MUST NOT write them into the practice model until the learner accepts them. Drafts MUST NOT appear as practice items, in due counts, or in the deck list.

#### Scenario: Staged draft

- **WHEN** generation returns items
- **THEN** the system persists them as drafts that are not eligible for practice, not counted as due, and not shown as deck items

#### Scenario: Drafts survive a restart

- **WHEN** the app is restarted while drafts are pending review
- **THEN** the drafts are still present for review

### Requirement: Generated content is validated before staging

The system SHALL validate generated output structurally before staging it and MUST discard any output that is empty, malformed, or outside the requested bounds.

#### Scenario: Malformed output

- **WHEN** the coach returns an item with an empty prompt or an empty expected answer, or output that cannot be parsed into the expected fields
- **THEN** the system discards it and stages no such item

#### Scenario: Above the item cap

- **WHEN** the coach returns more items than the run's item cap
- **THEN** the system stages at most the cap

### Requirement: Generation requires a configured remote coach

WHEN no remote coach is configured or the configured endpoint cannot be reached, the system MUST report a typed "generation unavailable" outcome, SHALL offer a route to manual deck creation, and MUST NOT fabricate a deck.

#### Scenario: No remote coach configured

- **WHEN** generation is requested and no remote coach is configured
- **THEN** the system reports that generation is unavailable and offers manual deck creation

#### Scenario: Remote coach unreachable

- **WHEN** the configured endpoint cannot be reached within the timeout
- **THEN** the system reports that generation is unavailable instead of producing placeholder items

### Requirement: Generation is bounded, chunked, and cancellable

The system SHALL perform generation as bounded requests that expose progress, MUST complete each request within a bounded timeout, and SHALL let the learner cancel a run in progress.

#### Scenario: Bounded per-request wait

- **WHEN** a generation request does not return within the configured timeout
- **THEN** the system does not block indefinitely and surfaces the failure for that request

#### Scenario: Partial results

- **WHEN** some requests succeed and others fail
- **THEN** the system keeps the drafts from the successful requests and reports the partial outcome

#### Scenario: Cancel a run

- **WHEN** the learner cancels a generation run in progress
- **THEN** no further requests are made and the already-staged drafts remain available for review

### Requirement: Generation records provenance and prompt version

The system SHALL record for each run the topic, the model, the endpoint identity, the prompt version, and the run time, and SHALL associate each accepted item with its originating run.

#### Scenario: Provenance recorded

- **WHEN** a run completes
- **THEN** the run record holds the topic, model, prompt version, and time, and any item accepted from it references that run

#### Scenario: Prompt version guard

- **WHEN** the set of known prompt versions changes
- **THEN** a guard test fails until the expected version set is updated

### Requirement: Estimated cost is shown before a run

The system SHALL show an estimated token/cost for a generation before the request is sent.

#### Scenario: Estimate before sending

- **WHEN** the learner starts a generation
- **THEN** an estimated token/cost is shown before any request is sent
