# Spam Control

Status: settled

Subdomain Class: supporting

## Purpose & Scope

This domain owns detecting and managing spam. It combines simple on-device heuristics with user marks and a bundled list. It does not own name resolution or call handling; it only reports a spam score and a spam flag.

## Ubiquitous Language

- **Spam Score** — A number from 0 to 100 that says how likely a number is spam.
- **Spam Flag** — A simple yes or no that marks a number as spam.
- **User Mark** — When the user says a number is spam or not spam.
- **Heuristic** — A simple rule that raises or lowers the spam score, such as many calls in a short time.
- **Reputation Cache** — On-device storage of spam scores and flags.

## Concepts & Model

- **Spam Assessment** (value object): has a phone number, a spam score, a spam flag, and a reason. It is produced on demand and is not stored as a decision.
- **Reputation Entry** (entity): has a phone number, a spam score, a spam flag, a source (bundled, learned, user), and a last-updated time.
- **Spam Policy** (aggregate): owns the heuristics and the thresholds that turn a score into a flag. It must keep thresholds stable so behavior is predictable.

## Capabilities & Operations

- Assess a phone number and return a Spam Assessment.
- Apply heuristics such as call frequency, number pattern, and time of day.
- Read the Bundled Database for known spam numbers.
- Read and update the Reputation Cache.
- Record a User Mark and adjust the score.
- Reset a User Mark when the user changes their mind.

## Rules & Invariants

- A User Mark of "not spam" overrides any heuristic or bundled flag for that number.
- A User Mark of "spam" overrides any heuristic that says otherwise.
- The bundled list is read-only; user marks are stored separately.
- Spam assessment never blocks a call by itself; it only informs Call Screening and Messaging.
- The system never shares user marks with any remote service.

## Relationships

- Reads identity from [Caller Identity](caller-identity.md).
- Informs [Call Screening](call-screening.md) and [Messaging](messaging.md).
- Reads from and writes to [Local Data](local-data.md).
- Reads settings from [User Preferences](user-preferences.md).
