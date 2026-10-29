# Spec Delta

## Purpose

Guarantees the app works with no network at all, and reminds the learner when reviews become due by scheduling background work that survives restarts and reboots.

## ADDED Requirements

### Requirement: Core flows work with no network connectivity

The system SHALL support deck management, practice sessions, review recording, scheduling, and progress viewing while the device has no network connectivity, and MUST NOT make any of them require a network request to succeed.

#### Scenario: Airplane mode
- **WHEN** the device has no network connectivity for the whole time the app is used
- **THEN** the learner can manage decks, run practice sessions, and view progress with no degraded core flow

#### Scenario: Network requests are never required
- **WHEN** a core flow is performed
- **THEN** its successful completion does not depend on the outcome of any network request

#### Scenario: Offline cold start
- **WHEN** the app is launched for the first time after installation with no network connectivity
- **THEN** it opens to a usable state and the learner can create a deck and practice it

### Requirement: Background work checks for due reviews

The system SHALL schedule background work that determines whether any practice items are due, and MUST also schedule a check for the time at which the earliest item becomes due.

#### Scenario: Periodic check is scheduled
- **WHEN** the app is used and background scheduling is enabled
- **THEN** the system has registered recurring background work that evaluates which items are currently due

#### Scenario: Check scheduled at the next due time
- **WHEN** the earliest next-due time of any practice item is known
- **THEN** the system schedules a one-time background check for that time so the learner is not notified late

#### Scenario: Scheduling survives restart and reboot
- **WHEN** the device is rebooted or the app is not running
- **THEN** the background work that evaluates due items remains registered and still runs

### Requirement: Learner is notified when reviews become due

The system SHALL notify the learner when due items exist, and MUST NOT notify the learner when no items are due.

#### Scenario: Due items exist
- **WHEN** a background check finds one or more items due for practice
- **THEN** the system posts a notification that identifies the number of items due and opens the app to the relevant deck when tapped

#### Scenario: Nothing is due
- **WHEN** a background check finds no due items
- **THEN** the system posts no notification

#### Scenario: Notification is not repeated needlessly
- **WHEN** a background check runs and the same due items were already notified without new reviews in between
- **THEN** the system does not post a duplicate notification for that same due set

### Requirement: Learner controls review reminders

The system SHALL let the learner turn review reminders off, and MUST NOT schedule reminder notifications while they are off.

#### Scenario: Reminders disabled
- **WHEN** the learner turns review reminders off
- **THEN** the system cancels pending reminder notifications and posts no further reminders until they are turned back on

#### Scenario: Reminders re-enabled
- **WHEN** the learner turns review reminders back on while due items exist
- **THEN** the system restores reminder scheduling and the due items are cleared by the next check

### Requirement: Notification permission is respected

On platform versions that require the user to grant notification permission, the system SHALL request it and MUST NOT post reminders when permission is denied.

#### Scenario: Permission granted
- **WHEN** the learner grants notification permission
- **THEN** the system posts review reminders as items become due

#### Scenario: Permission denied
- **WHEN** the learner denies notification permission
- **THEN** the app continues to work fully, the settings screen shows that reminders are blocked by the system, and no notification is attempted

#### Scenario: Background work still runs without permission
- **WHEN** notification permission has not been granted
- **THEN** due-review evaluation still runs and updates in-app due counts even though no notification is posted
