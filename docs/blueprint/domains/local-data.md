# Local Data

Status: settled

Subdomain Class: generic

## Purpose & Scope

This domain owns all on-device storage. It stores the cache, logs, bundled database, and settings. There is no backend or remote database. All data stays on the phone.

## Ubiquitous Language

- **On-Device Database** — A local store on the phone that holds cache, logs, and lists.
- **Bundled Database** — A read-only file shipped with the app that holds known business and spam numbers.
- **Cache** — Learned names and reputation entries stored for quick lookup.
- **Call Log** — Stored records of handled calls.
- **Message Log** — Stored records of handled messages.

## Concepts & Model

- **On-Device Database** (aggregate): owns all local tables and files. It is the only persistence layer. It must survive app restarts and must be removable by the user.
- **Bundled Database** (value object): a read-only file with a version number. It is replaced only by an app update.

## Capabilities & Operations

- Store and retrieve Caller Identity cache entries.
- Store and retrieve Reputation Cache entries.
- Store and retrieve Call Log entries.
- Store and retrieve Message Log entries.
- Load the Bundled Database at startup.
- Clear all user data on request.
- Export user data to a file on the device.

## Rules & Invariants

- No data leaves the device unless the user explicitly exports it.
- The Bundled Database is never modified at runtime.
- The cache can be cleared without losing settings.
- Storage must work fully offline.
- All stored phone numbers use the normalized E.164 form.

## Relationships

- Serves [Caller Identity](caller-identity.md), [Call Screening](call-screening.md), [Messaging](messaging.md), [App Bridge](app-bridge.md), [Spam Control](spam-control.md), and [User Preferences](user-preferences.md).
