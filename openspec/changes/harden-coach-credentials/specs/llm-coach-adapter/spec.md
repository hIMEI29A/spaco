# Spec Delta

## REMOVED Requirements

### Requirement: Network-backed coaching is opt-in through build-time configuration

**Reason**: Remote coaching is no longer configured at build time. Compiling an endpoint, model, and credential into the artifact put the credential in every copy of a configured build and shared one key across installations, which is unacceptable for a distributed artifact.

**Migration**: The learner saves the endpoint and model name in the app's coach settings on the device. A previously configured `local.properties` is ignored; the app serves coaching locally until the settings are saved.

### Requirement: The credential never reaches the repository, the UI, or learner-visible text

**Reason**: The credential is now entered on the device instead of being compiled in, so the interface must accept it. The requirement forbidding any interface for entering or viewing it cannot hold, and its remaining guarantees are restated as separate requirements covering the stored credential, diagnostics, and the built artifact.

**Migration**: No action needed. A build configured through `local.properties` is replaced by entering the credential in the app's coach settings once per device.

## ADDED Requirements

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

### Requirement: Coach configuration changes take effect without a rebuild

The system MUST apply a saved coach configuration to subsequent coach requests in the running app, and MUST return to the built-in deterministic coach once the configuration is cleared.

#### Scenario: Configuration saved while the app runs
- **WHEN** the learner saves a coach configuration while the app is running
- **THEN** the next coach request uses it, without reinstalling or restarting the app

#### Scenario: Configuration cleared
- **WHEN** the learner clears the coach configuration
- **THEN** subsequent coach requests are served by the built-in deterministic coach and no coaching request is sent over the network
