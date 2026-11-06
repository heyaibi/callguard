# 0001 Expand Caller Identity lookup sources

Date: 2026-11-05

## Context & Problem Statement

The first draft of Caller Identity listed only CNAP, local contacts, cache, bundled database, and online lookup. The product must be robust when CNAP is unavailable or returns no name, so more on-device and opt-in sources are needed.

## Decision & Drivers

Add Google Contacts, Operator Service, and App Profile Name as lookup sources, in that priority order after local contacts and before the on-device cache. Keep all sources on-device or opt-in; never upload the phonebook.

## Invariants & Consequences

- The resolution order now has eight sources instead of five.
- Google Contacts is used only when the user signs in.
- Operator Service is used only when the current SIM's operator exposes it.
- App Profile Name is used only when the user allowed App Bridge for that app.
- No new backend or remote dependency is introduced.

## Rejected Alternatives

- Crowdsourced phonebook sharing between users: rejected because there is no backend and it raises privacy concerns.
- Search-engine scraping for names: rejected because it is unreliable and leaks numbers.

## Amends

- [Caller Identity](../domains/caller-identity.md)
