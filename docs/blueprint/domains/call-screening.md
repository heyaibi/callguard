# Call Screening

Status: settled

Subdomain Class: core

## Purpose & Scope

This domain owns what happens when a cellular call arrives or is placed. It decides whether the call rings, is silenced, or is blocked, and it shows caller identity. It does not own name resolution; it asks Caller Identity for the name.

## Ubiquitous Language

- **Incoming Call** — A cellular call that reaches the device.
- **Outgoing Call** — A cellular call placed from the device.
- **Screening Decision** — The choice to allow, silence, or block a call.
- **Silence** — Stop the phone from ringing and vibrating, while keeping the call in the call log.
- **Block** — Reject the call so it does not reach the user.
- **Allow** — Let the call ring normally.
- **Call Log Entry** — A record that a call happened, with its outcome.
- **Caller ID Card** — The on-screen card that shows the caller's name and number.

## Concepts & Model

- **Incoming Call** (entity): has a phone number, a resolved Caller Identity, a screening decision, and a timestamp. Its lifecycle runs from arrival to either answered, missed, or blocked.
- **Screening Policy** (aggregate): owns the rules that map a Caller Identity and user settings to a Screening Decision. It must decide within the platform time limit.
- **Call Log Entry** (entity): has a phone number, a name, a direction, an outcome, and a timestamp. It is created after every handled call.

## Capabilities & Operations

- Receive an incoming call and ask Caller Identity to resolve the number.
- Apply the Screening Policy to produce a Screening Decision.
- Silence, block, or allow the call based on the decision.
- Show a Caller ID Card when the number is known or when the user wants unknown callers shown.
- Record a Call Log Entry for every handled call.
- Screen outgoing calls for caller ID display purposes.

## Rules & Invariants

- The screening decision must be returned within the platform time limit, even if name resolution is slow.
- A number in the user's allow list always rings.
- A number in the user's block list is always blocked.
- When silence mode is on, unknown numbers are silenced, not blocked, unless the user chose block.
- Blocked calls still create a Call Log Entry marked as blocked.
- The app must be the active call screening app chosen by the user.

## Relationships

- Depends on [Caller Identity](caller-identity.md) for name resolution.
- Reads rules from [User Preferences](user-preferences.md).
- Reads reputation from [Spam Control](spam-control.md).
- Writes cache and log to [Local Data](local-data.md).
- Shares events with [Messaging](messaging.md) when a blocked caller also sends a message.
