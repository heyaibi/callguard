# App Bridge

Status: settled

Subdomain Class: supporting

## Purpose & Scope

This domain owns caller identity for apps other than the phone dialer, such as WhatsApp. It listens to notifications from those apps and shows a caller identity card on top of them. It does not own the phone call itself.

## Ubiquitous Language

- **App Call** — A voice or video call that happens inside another app, not the cellular network.
- **Notification Listener** — The system service that lets the app read notifications from other apps.
- **Overlay Card** — A small card shown on screen above the other app, showing the caller's name.
- **Supported App** — An app whose notifications the bridge understands, such as WhatsApp.

## Concepts & Model

- **App Call** (entity): has a source app, a caller number or ID, a resolved Caller Identity, and a timestamp.
- **App Bridge Policy** (aggregate): owns the list of Supported Apps and the rules for when to show an Overlay Card. It must avoid showing cards when the user disabled them.
- **Overlay Card** (value object): has a name, a number, and a source app. It is shown only for a short time.

## Capabilities & Operations

- Listen for incoming call notifications from Supported Apps.
- Extract a phone number or contact ID from the notification.
- Ask Caller Identity to resolve the extracted number.
- Show an Overlay Card with the resolved name when available.
- Let the user mark a Supported App as enabled or disabled.

## Rules & Invariants

- The bridge only reads notifications; it never sends messages or makes calls.
- An Overlay Card is shown only when the user enabled the bridge for that app.
- The bridge works offline using the On-Device Cache and Bundled Database.
- If no name can be resolved, no card is shown, to avoid noise.
- The user can revoke notification access at any time, and the bridge stops then.

## Relationships

- Depends on [Caller Identity](caller-identity.md) for name resolution.
- Reads rules from [User Preferences](user-preferences.md).
- Writes cache to [Local Data](local-data.md).
