# User Preferences

Status: settled

Subdomain Class: generic

## Purpose & Scope

This domain owns all user settings and lists. It holds toggles for silence and ignore modes, allow and block lists, and notification choices. It does not own any business rule about calls or messages; it only stores and returns what the user chose.

## Ubiquitous Language

- **Silence Mode** — A master toggle that silences calls and messages from unknown numbers.
- **Ignore Mode** — A toggle that moves messages from unknown senders to a quiet inbox.
- **Allow List** — Numbers that always ring and notify, even in silence mode.
- **Block List** — Numbers that are always blocked.
- **Quiet Inbox** — A separate message inbox for ignored senders.
- **Settings Profile** — The full set of user choices stored on the device.

## Concepts & Model

- **Settings Profile** (aggregate): owns every user setting and list. It is loaded at startup and saved after each change.
- **Allow List** (entity): a set of phone numbers that bypass silence rules.
- **Block List** (entity): a set of phone numbers that are always blocked.

## Capabilities & Operations

- Turn Silence Mode on or off.
- Turn Ignore Mode on or off.
- Add or remove a number from the Allow List.
- Add or remove a number from the Block List.
- Enable or disable the App Bridge for each Supported App.
- Enable or disable Online Lookup.
- Export or clear all stored data on the device.

## Rules & Invariants

- Silence Mode and Ignore Mode are independent; either can be on alone.
- A number cannot be on both the Allow List and the Block List at the same time.
- Changes take effect immediately for the next call or message.
- Settings are stored only on the device.
- Clearing data removes the cache and logs but keeps settings unless the user chooses to reset all.

## Relationships

- Configures [Call Screening](call-screening.md).
- Configures [Messaging](messaging.md).
- Configures [App Bridge](app-bridge.md).
- Configures [Spam Control](spam-control.md).
- Reads and writes through [Local Data](local-data.md).
