# Caller Identity

Status: settled

Subdomain Class: core

## Purpose & Scope

This domain owns resolving a phone number into a display name and the source of that name. It does not own how calls are blocked or how messages are filtered. It only answers the question "who is calling?".

## Ubiquitous Language

- **Caller Identity** — The display name shown for a phone number, plus the source that produced it and a confidence level.
- **Lookup Source** — A method that can turn a phone number into a name. Each source has a priority and a trust level.
- **CNAP** — Calling Name Presentation. The telecom network delivers the caller's name with the call. This is the highest-trust source in India.
- **Local Contact** — A name stored in the device phonebook.
- **Google Contacts** — Names synced from the user's Google account, used only if the user signs in.
- **Operator Service** — A name provided by the telecom operator through a dedicated API or USSD, when available.
- **App Profile Name** — A name extracted from a messaging app's profile for that number.
- **On-Device Cache** — Names learned from past calls, messages, and apps, stored only on the phone.
- **Online Lookup** — An optional remote service that returns a name for a number. Used only when the user opts in and a network is available.
- **Confidence** — How sure the system is that a name is correct, shown as high, medium, or low.

## Concepts & Model

- **Caller Identity** (entity): has a phone number (identity), a display name, a source, a confidence level, and a last-seen time. Two identities are the same when their phone numbers match after normalization.
- **Phone Number** (value object): a normalized E.164 string. Two numbers are equal when their normalized forms match.
- **Lookup Source** (value object): a name, a priority rank, and a trust level (network, device, bundled, online, learned).
- **Resolution Result** (value object): the chosen name, the source used, and the confidence. A result with no name is allowed and means "unknown".
- **Resolution Policy** (aggregate): owns the ordered list of sources and the rules for choosing one result. It must keep the order consistent and must always return one result, even if the name is unknown.

## Capabilities & Operations

- Resolve a phone number to a Caller Identity by trying sources in priority order and returning the first successful result.
- Normalize a raw phone number into E.164 form using the device region.
- Read Google Contacts when the user signed in and look up the number there.
- Query the Operator Service when it is available for the current SIM.
- Extract an App Profile Name from a supported messaging app when the user allowed it.
- Add a learned name to the On-Device Cache after a call, message, or app notification is handled.
- Remove a learned name from the On-Device Cache when the user marks it wrong.
- Check whether a number is present in the Bundled Database (see Local Data).
- Perform an Online Lookup when the user has opted in and the cache has no result.

## Rules & Invariants

- CNAP has the highest priority when it returns a name, because it comes from the telecom network.
- Local Contact has the next priority, because the user chose that name.
- Google Contacts comes next, when the user signed in and the contact exists.
- Operator Service comes next, when the operator provides a name.
- App Profile Name comes next, when a messaging app exposes a profile name.
- On-Device Cache comes next, then Bundled Database, then Online Lookup.
- A number blocked or silenced by the user keeps its identity but is marked so other domains can act on it.
- The system never sends the user's phonebook to any remote service.
- Every resolution returns one result; an unknown name is a valid result, not an error.
- Phone numbers are always normalized before lookup or storage.

## Relationships

- Feeds [Call Screening](call-screening.md) with the resolved identity for incoming and outgoing calls.
- Feeds [Messaging](messaging.md) with the resolved identity for SMS senders.
- Feeds [App Bridge](app-bridge.md) with the resolved identity for app callers.
- Feeds [Spam Control](spam-control.md) with the resolved identity for reputation checks.
- Reads from and writes to [Local Data](local-data.md).
- Reads settings from [User Preferences](user-preferences.md).
