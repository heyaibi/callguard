# Ubiquitous Language

This index records every term across the product's bounded contexts. Each domain file owns the full definition of its terms; this gloss is an index pointer.

<!-- BEGIN GENERATED: ubiquitous language -->
- [**Allow**](domains/call-screening.md) — Let the call ring normally.
- [**Allow List**](domains/user-preferences.md) — Numbers that always ring and notify, even in silence mode.
- [**App Call**](domains/app-bridge.md) — A voice or video call that happens inside another app, not the cellular network.
- [**App Profile Name**](domains/caller-identity.md) — A name extracted from a messaging app's profile for that number.
- [**Block**](domains/call-screening.md) — Reject the call so it does not reach the user.
- [**Block List**](domains/user-preferences.md) — Numbers that are always blocked.
- [**Bundled Database**](domains/local-data.md) — A read-only file shipped with the app that holds known business and spam numbers.
- [**Cache**](domains/local-data.md) — Learned names and reputation entries stored for quick lookup.
- [**Call Log**](domains/local-data.md) — Stored records of handled calls.
- [**Call Log Entry**](domains/call-screening.md) — A record that a call happened, with its outcome.
- [**Caller ID Card**](domains/call-screening.md) — The on-screen card that shows the caller's name and number.
- [**Caller Identity**](domains/caller-identity.md) — The display name shown for a phone number, plus the source that produced it and a confidence level.
- [**CNAP**](domains/caller-identity.md) — Calling Name Presentation.
- [**Confidence**](domains/caller-identity.md) — How sure the system is that a name is correct, shown as high, medium, or low.
- [**Google Contacts**](domains/caller-identity.md) — Names synced from the user's Google account, used only if the user signs in.
- [**Heuristic**](domains/spam-control.md) — A simple rule that raises or lowers the spam score, such as many calls in a short time.
- [**Ignore Mode**](domains/user-preferences.md) — A toggle that moves messages from unknown senders to a quiet inbox.
- [**Ignore Unknown Senders**](domains/messaging.md) — Move messages from numbers not in the phonebook to a separate, quiet inbox.
- [**Incoming Call**](domains/call-screening.md) — A cellular call that reaches the device.
- [**Local Contact**](domains/caller-identity.md) — A name stored in the device phonebook.
- [**Lookup Source**](domains/caller-identity.md) — A method that can turn a phone number into a name.
- [**Message Log**](domains/local-data.md) — Stored records of handled messages.
- [**Message Log Entry**](domains/messaging.md) — A record that a message was received and how it was handled.
- [**Message Sender**](domains/messaging.md) — The phone number that sent a message.
- [**Notification Listener**](domains/app-bridge.md) — The system service that lets the app read notifications from other apps.
- [**On-Device Cache**](domains/caller-identity.md) — Names learned from past calls, messages, and apps, stored only on the phone.
- [**On-Device Database**](domains/local-data.md) — A local store on the phone that holds cache, logs, and lists.
- [**Online Lookup**](domains/caller-identity.md) — An optional remote service that returns a name for a number.
- [**Operator Service**](domains/caller-identity.md) — A name provided by the telecom operator through a dedicated API or USSD, when available.
- [**Outgoing Call**](domains/call-screening.md) — A cellular call placed from the device.
- [**Overlay Card**](domains/app-bridge.md) — A small card shown on screen above the other app, showing the caller's name.
- [**Quiet Inbox**](domains/user-preferences.md) — A separate message inbox for ignored senders.
- [**Reputation Cache**](domains/spam-control.md) — On-device storage of spam scores and flags.
- [**Screening Decision**](domains/call-screening.md) — The choice to allow, silence, or block a call.
- [**Settings Profile**](domains/user-preferences.md) — The full set of user choices stored on the device.
- [**Silence**](domains/call-screening.md) — Stop the phone from ringing and vibrating, while keeping the call in the call log.
- [**Silence Messages**](domains/messaging.md) — Stop sound and vibration for messages from unknown numbers.
- [**Silence Mode**](domains/user-preferences.md) — A master toggle that silences calls and messages from unknown numbers.
- [**SMS Message**](domains/messaging.md) — A text message received on the device.
- [**Spam Flag**](domains/spam-control.md) — A simple yes or no that marks a number as spam.
- [**Spam Score**](domains/spam-control.md) — A number from 0 to 100 that says how likely a number is spam.
- [**Supported App**](domains/app-bridge.md) — An app whose notifications the bridge understands, such as WhatsApp.
- [**User Mark**](domains/spam-control.md) — When the user says a number is spam or not spam.
<!-- END GENERATED: ubiquitous language -->
