# Changelog

All notable changes to CallGuard are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Texts that ring. The Guard tab has a TEXTS THAT RING section with one switch for each kind of text.
- ALWAYS RING and MUTE SENDER in a conversation. A muted sender stays in the inbox with no sound.
- Each new text is sorted into a kind and scored for spam on the phone.

### Changed

- The bottom bar reads Keypad, Recents, Messages, Contacts, and Guard.
- Settings now live on the Guard screen, below the protection status. The separate Settings screen is gone.
- Texts from people who are not your contacts ring by default when they are one-time codes, bank, government, service, delivery, or personal texts. Promotions and scams do not ring by default.

### Fixed

- Returning to Messages, or switching to its tab, shows the last list at once instead of an empty list. A burst of message changes reloads the list once.

## [0.1.0] - 2026-11-06

CallGuard was extracted from a monorepo, so the git history starts at the import. This entry lists the milestones of the branch the app was built on, in the order they landed.

### Added

- Android app scaffold with Kotlin, Jetpack Compose, and the dot-matrix theme.
- Domain core, data layer, and unit tests for both.
- UI mockups for home, recents, lookup, messages, lists, settings, and the incoming call card.
- Home, recents, and lookup wired to the core, plus toll-free number normalization and debug seed data.
- Contacts-only blocking, on by default once Contacts access is granted.
- App Bridge: WhatsApp notification listener, notification parser, and overlay card.
- Call screening service with CNAP, the deadline fallback, and the caller ID card, plus support for running as the default SMS app.
- Full phone and SMS app: dialer, in-call UI, keypad, contacts, merged recents, conversations, and inline reply.
- Launcher icon: dot-matrix shield, with adaptive, round, and themed variants.
- Play Store asset set: icon, feature graphic, and six screenshots.
