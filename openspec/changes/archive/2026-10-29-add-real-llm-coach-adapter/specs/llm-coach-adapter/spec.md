# Spec Delta

## Purpose

Defines how the app can serve its AI-coach requests from a remote, OpenAI-compatible language model instead of the built-in deterministic coach, and how that path is configured, constrained, and degraded so practice sessions keep working when the model is unavailable.

## ADDED Requirements

### Requirement: Network-backed coaching is opt-in through build-time configuration

The system SHALL serve coach requests from a remote model only when an endpoint, a model name, and a credential are all supplied at build time, and MUST use the built-in deterministic coach when any of them is absent.

#### Scenario: Fully configured build
- **WHEN** the app is built with an endpoint, a model name, and a credential
- **THEN** coach requests are served by the remote model

#### Scenario: Unconfigured build
- **WHEN** the app is built without these values
- **THEN** coach requests are served by the built-in deterministic coach, and practice sessions behave exactly as they do today

#### Scenario: Partially configured build
- **WHEN** any one of the three values is missing or blank
- **THEN** the system treats the remote model as unavailable and serves requests locally

### Requirement: Every coach request type is supported remotely

The system SHALL be able to serve all three coach requests — choosing the next item, generating an exercise, and generating an explanation — from the remote model, producing the same response fields the coach contract defines.

#### Scenario: Remote selection
- **WHEN** the system asks the remote model to choose the next item and it names one of the items on offer
- **THEN** the response carries that item's identity and is subject to the same eligibility validation as any other coach

#### Scenario: Remote exercise
- **WHEN** the system asks the remote model for an exercise for a target item
- **THEN** the response names that target item and carries the prompt and expected answer the session presents

#### Scenario: Remote explanation
- **WHEN** an answer has been graded and the system asks the remote model for an explanation
- **THEN** the response names that target item and carries the explanation text shown with the feedback

### Requirement: Remote output is requested and validated in the coach's field shape

The system SHALL request a response that names the target item and contains only the fields the coach contract defines, and MUST discard any response that cannot be parsed into that shape or that names a different target item.

#### Scenario: Well-formed response
- **WHEN** the remote model returns a response naming the requested target item with the expected fields populated
- **THEN** the system accepts it

#### Scenario: Unparseable response
- **WHEN** the remote model returns prose, malformed structure, or is missing a field the contract requires
- **THEN** the system discards the response instead of presenting it

#### Scenario: Response with an unexpected extra field
- **WHEN** the remote model returns a response that includes fields the contract does not define
- **THEN** the system ignores the extra fields and uses only the defined ones

### Requirement: An unusable remote response never interrupts a session

The system SHALL answer a coach request that the remote model cannot serve — because the request failed, the model is unreachable, the response was discarded, or the wait expired — with the built-in deterministic coach before falling back to stored content, and MUST NOT surface an error state to the learner for that reason.

#### Scenario: Remote request fails
- **WHEN** a coach request to the remote model fails, returns an error status, or returns a discarded response
- **THEN** the request is answered by the built-in deterministic coach and the session continues

#### Scenario: Learner-facing behavior is unchanged on failure
- **WHEN** a remote request is answered by the fallback
- **THEN** the learner sees a usable exercise, explanation, or next item, and no error message caused by the remote model

### Requirement: Remote requests are bound by a wait limit

The system MUST bound every remote coach request by a configured timeout and MUST NOT make a practice session wait past that bound.

#### Scenario: Slow remote model
- **WHEN** the remote model does not respond within the configured timeout
- **THEN** the system stops waiting and serves the request through the fallback path

#### Scenario: Device is offline
- **WHEN** the device has no network connectivity while remote coaching is configured
- **THEN** the session completes with usable exercises, explanations, and grading within the same bound

#### Scenario: Practice stays responsive
- **WHEN** the learner submits an answer while a remote request is in flight
- **THEN** grading feedback appears immediately and is not delayed by the remote request

### Requirement: Only the fields a coach request needs are sent to the remote endpoint

The system SHALL send only the practice content a coach request requires — the target item's prompt and expected answer, the sibling answers available as distractors, scheduling state, recent grades, and the item identities involved — and MUST NOT send other learner data.

#### Scenario: Selection request payload
- **WHEN** a next-item selection is sent to the remote endpoint
- **THEN** the payload contains only the offered items, their scheduling state, and recent review outcomes for the deck in question

#### Scenario: Exercise request payload
- **WHEN** an exercise is requested from the remote endpoint
- **THEN** the payload contains only the target item's content and the sibling content used as distractor material

### Requirement: The credential never reaches the repository, the UI, or learner-visible text

The system MUST read the credential from a source that is excluded from version control, and MUST NOT expose it in the interface, in anything the learner can see, or in diagnostics.

#### Scenario: Credential source is untracked
- **WHEN** the credential is configured for a local build
- **THEN** the value is stored outside version control and does not appear in the repository's tracked files

#### Scenario: Failure messages stay clean
- **WHEN** a remote request fails and its failure is reported in diagnostics
- **THEN** the reported text does not contain the credential

#### Scenario: No interface for entering or viewing the credential
- **WHEN** the learner uses the app
- **THEN** no screen displays the credential, and the learner cannot view or edit it in the app

### Requirement: Remote coaching does not change session, review, or scheduling behavior

The system MUST keep practice-session flow, review recording, and scheduling state identical whether a request was served by the remote model or by the fallback, and MUST NOT let the remote model's answer become the item's stored content.

#### Scenario: Recorded answer
- **WHEN** an answer is graded for an item whose question came from the remote model
- **THEN** the recorded review event refers to that item and scheduling updates as usual

#### Scenario: Stored content is preserved
- **WHEN** the remote model generates an exercise for an item
- **THEN** the item's stored prompt and expected answer are unchanged and are what a later session presents if the model is unavailable

#### Scenario: Remote model answers a selection for an ineligible item
- **WHEN** the remote model names an item outside the session's deck or already answered in the session
- **THEN** the choice is rejected and the session falls back to its due order, as with any other coach
