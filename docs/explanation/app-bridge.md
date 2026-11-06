# Why CallGuard reads WhatsApp notifications to name an app call

A WhatsApp call never reaches Android's telephony stack, so neither of CallGuard's call hooks sees it. This page explains why a notification listener is the only surface left, what the bridge does with what it reads, and what it structurally cannot see.

The bridge is `app/src/main/java/com/callguard/bridge/` and the product definition is [App Bridge](../blueprint/domains/app-bridge.md).

## The two hooks that do not fire

CallGuard's cellular paths both come from Telecom. `CallGuardScreeningService` is bound by the platform for incoming calls it forwards to screening. `CallGuardInCallService` is bound while CallGuard holds the dialer role and receives every call Telecom knows about.

An app call is created inside the app. WhatsApp opens its own audio session, draws its own screen, and posts its own notification. Telecom has no call to forward, so both hooks stay silent. No permission and no role changes that, because the call is not a phone call as far as the platform is concerned.

## The surface that does exist

WhatsApp posts a notification for an incoming call, and Android lets a user grant an app access to those notifications. That is the only system-level signal CallGuard can attach to, and it is why the bridge is a `NotificationListenerService` rather than a call service.

Notification access is special access, not a role and not a runtime permission. The user grants it from system settings, and it can be revoked at any time. `AppBridgeListener` declares `BIND_NOTIFICATION_LISTENER_SERVICE`, so only the system can bind it, and the Guard tab reads the state from the enabled listener packages.

## What the bridge does with a notification

The listener runs on the main thread, so it copies the text fields out of the notification and hands them to a single background thread. Parsing and lookups then run off the main thread. `BridgeParser` has no Android types, which keeps its behavior unit-testable.

The order of work on that thread is deliberate:

1. Learn a profile name, if the notification has one and the app is enabled.
2. Stop if there is no phone number in the notification.
3. Stop if the notification does not look like a call.
4. Stop if this notification key was handled in the last 30 seconds.
5. Resolve the name, assess spam, and write the call log entry.
6. Show the overlay card, and only when a name was resolved.

Each step has a reason:

- The number is read from the title first. The text field is searched only when the title is missing, so a notification that already shows a saved contact's name is never second-guessed with a number parsed out of a message preview.
- The 30 second window exists because a notification is not a single event. The same key can be posted again when the app updates it, and a card per update would stack.
- The log entry is written before the card decision, so an app call with no resolvable name is still recorded. The card is the noisy part, so it is the part that gets suppressed.
- Deduplication and learning both run on the worker thread, and the map that tracks handled keys is touched only there, which keeps it safe without a lock.

A call is recognized by any of three signals: the notification category is `call`, a line reads `Incoming voice call` or `Incoming video call`, or the notification carries an action titled answer, accept, decline, reject, or hang up. The parser reads three because the fields WhatsApp fills in vary with the Android version and the call type.

## Why the bridge only reads

The listener never fires a notification action. It cannot answer a call, decline one, reply to a message, or dial a number. Everything it does is display and record.

That restriction is the reason the feature is safe to offer with a listener at all: a notification listener can see a great deal, and this one writes nothing back to the app it reads. The cost is that CallGuard can show a card but cannot act on the call, so the user still answers inside WhatsApp.

## What it cannot see

- A call from a number the user has saved. WhatsApp shows the saved name and no number, `numberOf` finds nothing, and the listener returns before logging or showing anything. The bridge only identifies callers the phone does not already know.
- A call that produces no notification, or a device where the user has turned off WhatsApp notifications. There is nothing to read.
- Any app other than WhatsApp and WhatsApp Business. The package check is exact, and the parser is written against WhatsApp's wording.
- Anything after notification access is revoked. The service stops receiving posts and the NOTIFICATION ACCESS row on the Guard tab flips to not granted, with no other warning.
- Calls the parser does not recognize. A WhatsApp wording change that removes the call line and the call actions would make a call look like a message.

The bridge also cannot verify what it reads. A notification is text from another app, and the name in it is treated as a name, not as proof of identity.

## What the bridge learns

A profile name is the `~ Name` prefix WhatsApp uses for a sender who is not saved. `BridgeParser.profileName` accepts it only at the start of a field and only when it has letters, so a message body containing a tilde is not read as a name. Names are capped at 40 characters.

The learned name is stored in the cache with origin `app:<package>`, and only for apps the user enabled. `learnName` keeps an existing entry unless that entry is itself app-origin, so a name learned from a call or a text is never overwritten by a profile name.

`AppProfileSource` reads those entries at rank 5 of the identity chain, and only while `bridgeApps[<package>]` is true. Turning the app off under APP BRIDGE stops its names from being used and leaves the stored entries in place. See [Why CallGuard asks the network for a name first](caller-identity.md) for where that rank sits.

## Why the card is an overlay

A card drawn over another app needs `SYSTEM_ALERT_WINDOW`. `OverlayCard` checks the grant with `canDrawOverlays` and, when it is missing, posts a heads-up notification on the `caller_id` channel instead. The card is a plain `View` hierarchy rather than Compose, because it is added to the window manager from a service, and it dismisses itself after 6 seconds or on a tap. One card is shown at a time.

The same fallback covers a refused window, so a card that cannot be added becomes a notification rather than disappearing.

## What the design costs

- Detection depends on text another app wrote. Nothing in the bridge is covered by a stability contract, and a WhatsApp update is the main way this feature can break.
- The bridge shows a name and writes a log row. It cannot block, silence, or decline an app call, because the platform gives it no way to.
- The 30 second deduplication window means two calls from the same notification key inside 30 seconds produce one card.
- Every app call is logged with outcome `ALLOWED`, since the bridge has no verdict to record. The app-call rows in the log are a history, not a screening result.
- Debug builds also accept notifications from `com.android.shell`, so `adb shell cmd notification post` can drive the bridge without WhatsApp. Release builds ignore that package.

## Related

- [App Bridge domain specification](../blueprint/domains/app-bridge.md) for the product rules and vocabulary.
- [Permissions and roles reference](../reference/permissions-and-roles.md) for notification access and the overlay grant.
- [Settings reference](../reference/settings.md) for the two per-app switches.
