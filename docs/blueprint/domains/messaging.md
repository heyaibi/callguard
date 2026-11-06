# Messaging

Status: settled

Subdomain Class: supporting

## Purpose & Scope

This domain owns identifying and filtering SMS messages. It shows the sender's name when known and can silence or ignore messages from unknown numbers. It does not own call handling.

## Ubiquitous Language

- **SMS Message** — A text message received on the device.
- **Message Sender** — The phone number that sent a message.
- **Silence Messages** — Stop sound and vibration for messages from unknown numbers.
- **Ignore Unknown Senders** — Move messages from numbers not in the phonebook to a separate, quiet inbox.
- **Message Log Entry** — A record that a message was received and how it was handled.

## Concepts & Model

- **SMS Message** (entity): has a sender number, a resolved Caller Identity, a body, a timestamp, and a handling outcome.
- **Message Filter Policy** (aggregate): owns the rules that decide whether a message rings, is silenced, or is moved to the quiet inbox. It must keep the choice consistent with user settings.
- **Message Log Entry** (entity): has a sender number, a name, an outcome, and a timestamp.

## Capabilities & Operations

- Receive an SMS and ask Caller Identity to resolve the sender.
- Apply the Message Filter Policy to decide the handling outcome.
- Silence or move the message when the sender is unknown and the user enabled it.
- Show the sender's name in the message list when known.
- Record a Message Log Entry for every received message.

## Rules & Invariants

- A sender in the user's allow list always notifies normally.
- A sender in the user's block list is always moved to the quiet inbox and marked blocked.
- When silence mode is on, unknown senders do not trigger sound or vibration.
- When ignore mode is on, unknown senders go to the quiet inbox instead of the main inbox.
- The system reads messages only on the device; it never uploads them.

## Relationships

- Depends on [Caller Identity](caller-identity.md) for sender name resolution.
- Reads rules from [User Preferences](user-preferences.md).
- Reads reputation from [Spam Control](spam-control.md).
- Writes cache and log to [Local Data](local-data.md).
