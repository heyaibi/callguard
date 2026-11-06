# How CallGuard is built, and why it has this shape

CallGuard is one Gradle module with no backend, no dependency injection framework, and no ORM. This page explains the layers, what each package is allowed to know about, how a call, a text, and an app call travel through them, and which constraints produced the shape.

The product rules live in the [blueprint](../blueprint/README.md). This page is about the code that applies them: `:app`, package `com.callguard`, Kotlin with Jetpack Compose, `minSdk` 29, `targetSdk` 37. Build and test commands are in the [Gradle tasks reference](../reference/gradle-tasks.md).

## Why one module

There is one app, one developer, and no shared library to publish. A second module would add build configuration, a published API surface, and a version to keep in step, and it would buy compile-time separation between parts of the app that ship together anyway.

Separation is done with packages instead, and with one rule that the packages mostly keep: the rule objects do not import Android UI, and they do not import each other's internals. `com.callguard.screening` knows `identity`, `spam` and `prefs`. `com.callguard.identity` knows `data` and `prefs` and nothing about calls.

Nothing enforces that rule at build time. ktlint and detekt check formatting and static analysis, not layering, so a wrong import compiles.

## The layers

| Layer | Packages | What it holds | Android dependencies |
|---|---|---|---|
| Shared kernel | `core` | `PhoneNumber`, a value class over a string, and `PhoneNumbers.normalize` and `format`. Normalization defaults to region `IN`, applies Indian national rules (ten-digit mobile numbers starting 6 to 9, `1800` and `1860` service numbers, a leading zero, or an explicit `91` prefix), keeps alphanumeric sender IDs uppercased, keeps 3 to 6 digit short codes as written, and returns null for blank or unrecognized input. Formatting writes `+91 98765 43210` for a `+91` number with ten national digits and the stored string otherwise. | None |
| Rules | `identity`, `spam`, `screening`, `messaging`, `prefs` | The policy objects and the settings model. `ResolutionPolicy`, `SpamPolicy`, `ScreeningPolicy`, `MessageFilterPolicy`, `Settings`, `SettingsStore`. | `identity` also holds the phonebook readers, which need a `Context` |
| Storage | `data` | `CallGuardDb`, the repositories, `BundledDatabase` | SQLite and assets |
| Platform adapters | `call`, `messaging`, `screening`, `bridge` | The services, receivers, activities and notifiers that Android binds | The platform services themselves |
| Interface | `ui` | Compose screens, navigation, theme, formatting helpers | Compose |
| Wiring | `AppGraph`, `CallGuardApp` | One object graph, built once | `Application` |

The rules layer is where the test suite lives. Twenty JVM test classes cover the policies, the parsers, the format helpers, the bridge parser, and one Compose screen. `ScreeningPolicyTest` needs no Android types at all, because `ScreeningPolicy.decide` takes an identity, a spam assessment, a settings record, and a Boolean.

## Why the policies are pure, and what that costs

A policy that asked the phonebook for a permission would be untestable without Android, and it would put a permission decision inside a product rule. CallGuard keeps the permission question outside instead.

`AppGraph.effectiveSettings()` returns the stored settings with `contactsOnly` forced to false when `READ_CONTACTS` is missing. Every caller that runs a policy passes that copy: `CallGuardScreeningService` and `MessageHandler`. The policies then treat "in contacts" as a plain Boolean.

The cost is that a new caller can forget to use the effective copy and reintroduce the bug the method exists to prevent. There is no type that makes the plain `Settings` unusable for a policy.

## Why there is a manual service locator

`CallGuardApp` builds one `AppGraph` in `onCreate`, and `CallGuardApp.graph(context)` is the only accessor. A service, a receiver, or a composable reaches the same graph from any context.

The graph holds the settings store, the repositories, the bundled database, the resolution policy, the spam policy, the screening policy, and the message filter policy, plus `resolve(raw, cnapName)` and `isInContacts(number)`. That is a handful of objects with no scopes, no qualifiers, and no lifecycle beyond the process.

A DI framework would add a compiler plugin and generated code to remove four lines of construction. The cost of the manual approach is that the graph is a global: a test constructs its own policies rather than replacing them in the graph, and nothing verifies the graph is complete until a code path touches a missing piece.

## Why there is no Room

The database is four tables created by hand in a `SQLiteOpenHelper`: `cache` keyed by number, `reputation` keyed by number, `call_log` and `message_log` as append-only tables, plus an index on `call_log (number, timestamp)`. The schema version is 1 and `onUpgrade` is empty.

Room would add KSP and a generated schema to the build for queries this small. It would also introduce migration testing, which this app does not have yet. The cost of the hand-written route is that the first schema change has to write a real `onUpgrade`, and every query is a string.

Settings take the same route at a smaller scale: one SharedPreferences file named `settings`, with one key per setting. The bundled database is a JSON asset read once at startup, replaced only by an app update, and its version is shown in Settings.

## What each package exposes

| Package | Main types | Notes |
|---|---|---|
| `core` | `PhoneNumber`, `PhoneNumbers` | Every number goes through `normalize` before storage or lookup, so comparisons are string equality |
| `identity` | `SourceKind` with a rank and a trust label, `LookupSource`, `LookupContext`, `ResolutionResult`, `ResolutionPolicy`, the eight sources | `resolve` never throws and returns `SourceKind.NONE` when nothing answers |
| `spam` | `SpamAssessment`, `SpamPolicy`, `SpamSource` | `SPAM_THRESHOLD` is 70 |
| `screening` | `ScreeningPolicy`, `ScreeningDecision`, `CallAnswer`, `CallResponseSpec`, `ScreeningVerdicts`, `CallerIdNotifier` | One answer per call, enforced by `CallAnswer` |
| `messaging` | `MessageFilterPolicy`, `MessageHandler`, `SmsProvider`, `MessageNotifier`, `SmsRole` | `isDefaultSmsApp` reads `RoleManager`, because `Telephony.Sms.getDefaultSmsPackage` does not report the role on this platform |
| `call` | `Dialer`, `CallGuardInCallService`, `CallManager`, `InCallActivity`, `DialActivity`, `CallNotifier`, `CallRinger`, `CallForegroundService` | `CallManager` is the state holder for calls in progress |
| `bridge` | `AppBridgeListener`, `BridgeParser`, `OverlayCard` | The parser has no Android types |
| `data` | `CallGuardDb`, `Repositories`, `BundledDatabase` | Repositories expose `StateFlow` for the logs |
| `prefs` | `Settings`, `SettingsStore` | One `StateFlow<Settings>` |
| `ui` | Screens, the `CgColors` and `CgType` theme tokens, format helpers | Collects the flows with `collectAsStateWithLifecycle` |

## Contracts that hold across packages

- A log entry records the outcome that actually happened. A screened call logs `ALLOWED`, `SILENCED`, or `BLOCKED`; a call screening never answered logs `OUTGOING`, `ALLOWED`, or `MISSED`, where a declined call counts as missed. Message outcomes are the four `MessageOutcome` values.
- A cache entry carries an origin, and the origin is the only thing that distinguishes a name learned from a call or a text from one learned from an app. An entry whose origin is `call` and one whose origin starts with `app:` are read by different sources.
- A reputation entry carries its source, and only a `USER` entry is exempt from `clearAll`, which is why the reset dialog promises that spam marks survive.
- A missing permission never crashes a lookup. Provider reads catch their exceptions and return null or an empty list.
- CallGuard never sends the phonebook anywhere. The app does not declare the `INTERNET` permission, so it could not if it tried.

## Flow: an incoming cellular call that screening handles

The screening service resolves identity, assesses spam, and asks `ScreeningPolicy` for a decision. The service sends exactly one answer: the full decision, or after 2500 ms the lists-only fallback, whichever reaches `CallAnswer.offer` first. A call may already be sitting in the in-call service while this happens, which is why the two services coordinate through the verdict map instead of calling each other.

Before the answer goes out, the decision is recorded in `ScreeningVerdicts` under the call's key. After it, `afterAnswer` writes the log entry, learns a CNAP name into the cache, and posts either a caller ID card or a blocked notice. Nothing after the answer delays the call.

See [Why CallGuard answers a call in 2.5 seconds](screening.md) for the reasoning behind the deadline and the rule order.

## Flow: a cellular call under the dialer role

With the dialer role held, `CallGuardInCallService` receives every call, contacts included. `CallManager` tracks each one in a `StateFlow`, and four things read that state: `InCallActivity` draws the screen, `CallNotifier` posts the ringing and ongoing notifications, `CallRinger` rings and vibrates, and `CallForegroundService` keeps a call in progress in the foreground.

`CallManager` is an object rather than an injected class, because the service, the activity, and the notification actions all need the same state and none of them can pass a reference to the others. Changes happen on the main thread. Identity and spam lookup runs on a background scope and reports back on the main thread.

When Telecom removes a call, `CallManager` peeks the verdict. A call with a verdict was already logged by screening, so it is skipped and the verdict is forgotten. Anything else goes through `logUnscreened`, which writes `OUTGOING`, `ALLOWED`, or `MISSED`, decided from the direction and whether the call ever connected.

If the foreground service start is refused, the in-progress call falls back to a plain notification. A ringing call over another app raises a heads-up rather than the in-call screen, and a placed call opens the screen directly.

## Flow: a text

The path depends on the SMS role. Under `ROLE_SMS`, `SmsDeliverReceiver` receives `SMS_DELIVER` and `MessageHandler.handle` stores the text in the SMS provider, writes the log entry, and posts an alert according to the outcome. The text is stored whether the outcome is notify, silenced, quiet inbox, or blocked, so nothing is lost.

Without the role, `SmsReceiver` receives `SMS_RECEIVED` and calls `logOnly`. The system SMS app stores and alerts, and CallGuard writes a log row.

MMS download is not implemented. `MmsReceiver` exists so the SMS role has a complete receiver set, and it ignores the push.

## Flow: an app call

`AppBridgeListener` reads notifications from the packages enabled in the APP BRIDGE section, parses the number and an optional profile name, resolves identity and spam, writes a log entry with direction `APP`, and shows the overlay card when a name was resolved. See [Why CallGuard reads WhatsApp notifications](app-bridge.md).

## Launch routing and the keyboard

`MainActivity` is the only launcher activity and is `singleTask`, so a launch while the app is open arrives in `onNewIntent`. It converts the intent into an `OpenRequest` and hands it to `AppNav`, which opens it once. `DIAL` with or without a `tel:` number, and `tel:` `VIEW`, open the keypad, prefilled when a number is present. A message notification's extra, and `sms:`, `smsto:`, `mms:` and `mmsto:` links, open a conversation. A caller ID card opens the incoming-call screen.

`DialActivity` is separate and exported, because the dialer role needs an activity that answers `CALL` with a `tel:` URI and `CALL_BUTTON`. `InCallActivity` is `singleInstance`, excluded from recents, and shows over the lock screen with the screen turned on.

The window uses `adjustNothing`. On this edge-to-edge build the window is not resized for the keyboard (`adjustResize` had no effect when measured on the emulator, and the default pan moved the header off-screen), so each text screen applies the IME inset itself: `ComposeBar` in `ThreadScreen`, which also scrolls to the newest text, and `ComposeScreen` as a whole.

## What this shape costs

- The verdict map is in memory. A process restart loses it, and an in-flight call then has no verdict to peek, which the in-call service treats the same as a call screening never answered.
- The single module means a wrong import is a compile success. Layering is a review question, not a build failure.
- The manual graph and the object singletons (`CallManager`, `ScreeningVerdicts`, `OverlayCard`) are global state. Tests construct policies directly rather than substituting dependencies.
- Screens read the stores through the graph, so a Compose preview without an `Application` cannot show real data. Debug builds seed demo data instead: `CallGuardApp` calls `DemoData.seedOnce` when `BuildConfig.DEBUG` is true.
- Without a backend there is no shared reputation and no remote name service. `OnlineLookupSource` is wired in at the last rank and returns no name, and the app declares no `INTERNET` permission.

## Related

- [Gradle tasks reference](../reference/gradle-tasks.md) for building, testing, and the CI pipeline.
- [Settings reference](../reference/settings.md) for the state the wiring holds.
- [Product domain specification](../blueprint/README.md) for the domains this code implements.
