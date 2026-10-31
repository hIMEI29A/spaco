# llm-coach-adapter Specification

## Purpose
Defines how the app can serve its AI-coach requests from a remote, OpenAI-compatible language model instead of the built-in deterministic coach, and how that path is configured, constrained, and degraded so practice sessions keep working when the model is unavailable.

## Requirements

### Requirement: Network-backed coaching is opt-in through configuration supplied on the device

The system SHALL serve coach requests from a remote model only when the learner has saved an endpoint and a model name on the device, and MUST use the built-in deterministic coach until such a configuration exists.

#### Scenario: Configuration saved on the device
- **WHEN** the learner saves an endpoint and a model name in the app's coach settings
- **THEN** coach requests are served by the remote model at that endpoint

#### Scenario: No configuration
- **WHEN** the app is installed and the learner has saved no coach configuration
- **THEN** coach requests are served by the built-in deterministic coach, practice sessions behave exactly as they do without a remote coach, and no coaching request is sent over the network

#### Scenario: Incomplete configuration
- **WHEN** the learner supplies a model name without an endpoint, or an endpoint without a model name
- **THEN** the configuration is not saved and the system keeps serving requests locally

### Requirement: Build-time coach configuration is not a supported input

The build MUST NOT read coach endpoint, model, or credential values from the build environment, and MUST NOT expose such values in the artifact's generated configuration.

#### Scenario: Build does not consult build-time settings
- **WHEN** a build runs, whether or not a local configuration file is present on the machine
- **THEN** the produced artifact contains no coach configuration field and no coach credential

#### Scenario: Regression is caught mechanically
- **WHEN** a change reintroduces a build-time coach configuration value into the artifact
- **THEN** an automated check fails instead of the value shipping silently

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

### Requirement: The credential is supplied on the device and never reaches a built artifact

The system MUST NOT contain a coach credential in any built artifact, and MUST obtain the credential only from configuration the learner enters on the device.

#### Scenario: Built artifact is free of the credential
- **WHEN** an artifact is produced for any build variant
- **THEN** it carries no credential value, because no build input supplies one

#### Scenario: Credential entered on the device
- **WHEN** the learner enters a credential in the app
- **THEN** it is used for that device's coach requests and is not written back into the project or its tracked files

### Requirement: The credential is encrypted at rest and excluded from backup

The system MUST store a saved credential encrypted under a key protected by the platform keystore, and MUST exclude it from device backup and restore.

#### Scenario: Credential is persisted encrypted
- **WHEN** the learner saves a credential and the app is restarted
- **THEN** the credential is available to coach requests and is not readable as plain text from the app's stored data

#### Scenario: Credential is not restored on another device
- **WHEN** the app's data is restored from a backup onto another device or installation
- **THEN** the credential is absent and the learner must enter it again, while decks and progress are restored

### Requirement: The credential is write-only in the interface

The system SHALL let the learner enter, replace, and delete the credential, and MUST NOT display a saved credential value anywhere in the interface after it is saved.

#### Scenario: Entering a credential
- **WHEN** the learner enters a credential and saves the configuration
- **THEN** the interface confirms the configuration is saved and shows that a credential is set without showing its value

#### Scenario: Saved value is not readable in the app
- **WHEN** the learner opens the coach settings after saving a credential
- **THEN** no screen displays the credential's characters, and the learner can only replace or delete it

#### Scenario: Deleting a credential
- **WHEN** the learner deletes the saved credential
- **THEN** subsequent coach requests no longer carry any authorization value

### Requirement: Coach diagnostics never contain the credential

The system MUST NOT include the credential in anything the learner sees or in diagnostic output it produces when a coach request fails.

#### Scenario: Failure messages stay clean
- **WHEN** a remote coach request fails and its failure is surfaced or logged
- **THEN** the reported text does not contain the credential, in whole or in part

### Requirement: An endpoint that needs no credential is a supported configuration

The system SHALL accept a saved endpoint and model name without a credential, and MUST NOT send an authorization value to an endpoint configured without one.

#### Scenario: Local endpoint without a credential
- **WHEN** the learner saves an endpoint that requires no authentication
- **THEN** coach requests are sent to it without an authorization value and sessions are served by the remote model

#### Scenario: Endpoint rejects an absent or wrong credential
- **WHEN** the learner checks the connection and the endpoint answers with an authentication failure
- **THEN** the interface reports that the endpoint rejected the credential, and practice sessions continue to be served without interruption

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

### Requirement: Coach configuration changes take effect without a rebuild

The system MUST apply a saved coach configuration to subsequent coach requests in the running app, and MUST return to the built-in deterministic coach once the configuration is cleared.

#### Scenario: Configuration saved while the app runs
- **WHEN** the learner saves a coach configuration while the app is running
- **THEN** the next coach request uses it, without reinstalling or restarting the app

#### Scenario: Configuration cleared
- **WHEN** the learner clears the coach configuration
- **THEN** subsequent coach requests are served by the built-in deterministic coach and no coaching request is sent over the network
