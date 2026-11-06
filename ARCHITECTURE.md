# CallGuard — Architecture

Source of truth for behavior: `blueprints/callguard/` (PDS). This file maps PDS domains to code.

- Single Gradle module `:app`, package `com.callguard`, Kotlin + Jetpack Compose, minSdk 29, targetSdk 37.
- No backend. No Room/KSP: persistence uses platform `SQLiteOpenHelper` and `SharedPreferences`.
- No DI framework: a manual service locator `AppGraph` built in `CallGuardApp : Application`.
- Build: `cd apps/callguard && ./gradlew :app:assembleDebug :app:testDebugUnitTest`.

## Packages

| Package | PDS domain | Contents |
|---|---|---|
| `com.callguard.core` | shared kernel | `PhoneNumber`, `PhoneNumbers` (normalizer) |
| `com.callguard.identity` | Caller Identity | `LookupSource`, `ResolutionResult`, `ResolutionPolicy`, source impls |
| `com.callguard.spam` | Spam Control | `SpamAssessment`, `SpamPolicy` |
| `com.callguard.screening` | Call Screening | `ScreeningDecision`, `ScreeningPolicy`, `CallGuardScreeningService`, `CallAnswer` (one answer per call), `CallerIdNotifier` |
| `com.callguard.messaging` | Messaging | `MessageFilterPolicy`, `SmsReceiver`, `MessageNotifier`, `SmsRole` (ROLE_SMS check) |
| `com.callguard.call` | Calls (default phone app) | `Dialer` (`placeCall`, ROLE_DIALER), `CallGuardInCallService` (InCallService), `CallManager`, `InCallActivity`, `DialActivity` (CALL tel:, CALL_BUTTON), `CallNotifier`, `CallRinger`, `CallForegroundService` |
| `com.callguard.bridge` | App Bridge | `AppBridgeListener` (NotificationListenerService), `OverlayCard` |
| `com.callguard.data` | Local Data | `CallGuardDb`, repositories, `BundledDatabase` |
| `com.callguard.prefs` | User Preferences | `SettingsStore` |
| `com.callguard.ui` | — | Compose screens, `theme/` (already exists — reuse `CgColors`, `CgType`, `DotFont`, `MonoFont`) |

## Core API contract (signatures must match exactly)

```kotlin
// core
@JvmInline value class PhoneNumber(val e164: String)       // always normalized
object PhoneNumbers {
    fun normalize(raw: String, defaultRegion: String = "IN"): PhoneNumber?  // null when not a number
    fun format(n: PhoneNumber): String  // "+91 98765 43210" style for +91 10-digit; else e164
}

// identity
enum class Confidence { HIGH, MEDIUM, LOW, NONE }
enum class SourceKind(val rank: Int, val trust: String) {
    CNAP(1,"network"), LOCAL_CONTACT(2,"device"), GOOGLE_CONTACTS(3,"device"),
    OPERATOR_SERVICE(4,"network"), APP_PROFILE(5,"learned"), ON_DEVICE_CACHE(6,"learned"),
    BUNDLED_DATABASE(7,"bundled"), ONLINE_LOOKUP(8,"online"), NONE(99,"none")
}
data class LookupContext(val cnapName: String? = null)       // CNAP delivered with the call
interface LookupSource { val kind: SourceKind; fun isAvailable(): Boolean; fun lookup(n: PhoneNumber, ctx: LookupContext): String? }
data class ResolutionResult(val number: PhoneNumber, val name: String?, val source: SourceKind, val confidence: Confidence) {
    val isKnown get() = name != null
}
class ResolutionPolicy(sources: List<LookupSource>) {        // sorts by kind.rank; skips unavailable; first non-blank wins
    fun resolve(n: PhoneNumber, ctx: LookupContext = LookupContext()): ResolutionResult  // never throws; unknown = name null, NONE
}
// Confidence mapping: CNAP, LOCAL_CONTACT, GOOGLE_CONTACTS -> HIGH; OPERATOR_SERVICE, APP_PROFILE, BUNDLED_DATABASE -> MEDIUM; cache, online -> LOW.

// spam
enum class SpamSource { BUNDLED, LEARNED, USER, HEURISTIC, NONE }
data class SpamAssessment(val number: PhoneNumber, val score: Int, val isSpam: Boolean, val reason: String)
class SpamPolicy(/* repos */) {
    companion object { const val SPAM_THRESHOLD = 70 }
    fun assess(n: PhoneNumber, nowMillis: Long = System.currentTimeMillis()): SpamAssessment
    fun mark(n: PhoneNumber, spam: Boolean)   // user mark
    fun clearMark(n: PhoneNumber)
}

// screening
enum class ScreeningDecision { ALLOW, SILENCE, BLOCK }
class ScreeningPolicy {
    fun decide(identity: ResolutionResult, spam: SpamAssessment, settings: Settings, inContacts: Boolean): ScreeningDecision
}

// messaging
enum class MessageOutcome { NOTIFY, SILENCED, QUIET_INBOX, BLOCKED }
class MessageFilterPolicy { fun decide(identity: ResolutionResult, spam: SpamAssessment, settings: Settings, inContacts: Boolean): MessageOutcome }

// prefs
enum class UnknownCallerAction { SILENCE, BLOCK }
data class Settings(
    val contactsOnly: Boolean = true, val silenceMode: Boolean = false, val ignoreMode: Boolean = false,
    val unknownCallerAction: UnknownCallerAction = UnknownCallerAction.SILENCE,
    val blockSpam: Boolean = false, val showCardForUnknown: Boolean = true,
    val onlineLookup: Boolean = false, val googleContacts: Boolean = false,
    val bridgeApps: Map<String, Boolean> = mapOf("com.whatsapp" to true, "com.whatsapp.w4b" to true),
    val allowList: Set<String> = emptySet(), val blockList: Set<String> = emptySet(), // e164 strings
)
class SettingsStore(context: Context) {
    val settings: StateFlow<Settings>
    fun update(transform: (Settings) -> Settings)
    fun allow(n: PhoneNumber)   // adds to allow list, removes from block list
    fun block(n: PhoneNumber)   // adds to block list, removes from allow list
    fun unlist(n: PhoneNumber)
    fun reset()
}

// data
enum class Direction { INCOMING, OUTGOING, APP }
data class CallLogEntry(val id: Long = 0, val number: String, val name: String?, val source: SourceKind, val direction: Direction, val outcome: String /* ALLOWED|SILENCED|BLOCKED|... */, val app: String? = null, val timestamp: Long)
data class MessageLogEntry(val id: Long = 0, val number: String, val name: String?, val body: String, val outcome: MessageOutcome, val timestamp: Long)
data class CacheEntry(val number: String, val name: String, val origin: String /* call|sms|app:<pkg> */, val lastSeen: Long)
data class ReputationEntry(val number: String, val score: Int, val isSpam: Boolean, val source: SpamSource, val updated: Long)
class CallGuardDb(context: Context) : SQLiteOpenHelper  // tables: cache, reputation, call_log, message_log
class Repositories(db: CallGuardDb) {
    val cache: CacheRepository        // get(n), put(entry), remove(n), all(), clear()
    val reputation: ReputationRepository // get(n), put(entry), remove(n), clear()
    val calls: CallLogRepository      // add(e), recent(limit): List, observe(): StateFlow<List>, countSince(n, sinceMillis): Int, clear()
    val messages: MessageLogRepository // add(e), recent(limit), quietInbox(): List, observe(): StateFlow<List>, clear()
    fun clearAll()                    // cache + logs + reputation(non-user); settings kept
    fun exportJson(): String
}
class BundledDatabase(context: Context) { val version: Int; fun nameFor(n: PhoneNumber): String?; fun isSpam(n: PhoneNumber): Boolean }
// asset: app/src/main/assets/bundled.json  {"version":1,"business":{"+9118001234567":"SBI Card"},"spam":["+911409..."]}

// graph
class AppGraph(context: Context) { settingsStore, repos, bundled, resolutionPolicy, spamPolicy, screeningPolicy, messageFilterPolicy,
    fun resolve(raw: String, cnapName: String? = null): ResolutionResult? ; fun isInContacts(n: PhoneNumber): Boolean }
class CallGuardApp : Application() { lateinit var graph: AppGraph }  // companion: fun graph(ctx) = (ctx.applicationContext as CallGuardApp).graph
```

## Rules carried from PDS (must be unit-tested)

Screening, in order: block list → BLOCK; allow list → ALLOW; contacts only && not in contacts → BLOCK; spam.isSpam && blockSpam → BLOCK;
spam.isSpam → SILENCE; unknown (not in contacts and identity unknown) && silenceMode → SILENCE or BLOCK per `unknownCallerAction`; else ALLOW.
Spam assessment alone never yields BLOCK unless the user enabled `blockSpam`.
Contacts only (default on) applies only while READ_CONTACTS is granted: `AppGraph.effectiveSettings()` sets `contactsOnly = false` otherwise, so the policies stay pure.

Messaging: block list → BLOCKED; allow list → NOTIFY; contacts only && not in contacts → BLOCKED; not in contacts && ignoreMode → QUIET_INBOX;
not in contacts && silenceMode → SILENCED; spam → QUIET_INBOX; else NOTIFY.

Spam: user mark "not spam" → score 0 regardless; user mark "spam" → 100; bundled spam → 90;
heuristics: ≥3 calls in last 10 min +40, number pattern (e.g. +91 140 telemarketing series) +30, call between 22:00–07:00 +10; capped 100.

Identity: Google Contacts only when `settings.googleContacts`; Online Lookup only when `settings.onlineLookup`
(no backend exists, so the source is present but returns null); Operator Service unavailable unless SIM supports it (returns false today);
App Profile reads cache entries whose origin starts with `app:` and only for bridge-enabled apps. Phonebook never leaves the device.

## Platform integration

- `CallGuardScreeningService : CallScreeningService` (role `android.app.role.CALL_SCREENING`). Android binds a third-party screening service
  only for calls that are NOT from contacts, so contacts are never screened here; contacts-only blocking acts on the calls the platform forwards.
  Uses `Call.Details.callerDisplayName` as CNAP only when the presentation is allowed. Each incoming call gets exactly one answer: the full
  screening on a worker thread, or, after `SCREENING_DEADLINE_MILLIS` (2500 ms), the lists-only fallback (block list → BLOCK, else ALLOW).
  Outgoing calls are allowed at once and only logged. Every call is logged with the answer actually sent; CNAP names are learned into the cache
  (origin `call`). Allowed and silenced calls get a heads-up Caller ID card (channel `caller_id`, silent, 30 s) and an `OverlayCard` when the
  overlay permission is granted; blocked calls get a low-importance notice (channel `blocked`) and no card. With the dialer role held
  no card or overlay is posted (the in-call screen shows the caller); the log and the blocked notice are unchanged.
- Default phone app (`ROLE_DIALER`): `CallGuardInCallService` receives every call on the device, contacts' calls included. `CallManager`
  tracks them, `InCallActivity` draws the in-call UI, `CallRinger` rings, `CallForegroundService` keeps the call in progress. A call that
  screening never answered is logged by `CallManager` when Telecom removes it (`logUnscreened`).
- Screening and the dialer role: the role does not change which calls are screened. Telecom binds `CallGuardScreeningService` only for calls
  it forwards to call screening, which excludes calls from contacts (platform behavior from the existing design, not re-verified on device).
  So contacts' calls reach the call log through `CallManager` but get no screening verdict and ring as normal.
- Launch routing: `MainActivity` (`singleTask`) opens every screen a launch asks for. DIAL (with or without a tel: number) and tel: VIEW open the
  keypad, pre-filled with the number. `ACTION_OPEN_THREAD` (message notifications) and sms:, smsto:, mms:, mmsto: links open a conversation.
  A caller ID card opens the incoming-call screen. A launch while the app is open arrives in `onNewIntent` and reaches `AppNav` as an
  `OpenRequest`. `DialActivity` keeps only CALL tel: (places the call) and CALL_BUTTON.
- Keyboard: `MainActivity` uses `adjustNothing`. On this edge-to-edge build the window is not resized for the IME (measured on the emulator:
  `adjustResize` had no effect), and the default pan moved the header off-screen. So each text screen applies the IME inset once itself:
  `ComposeBar` in `ThreadScreen` (which also scrolls to the newest text) and `ComposeScreen` as a whole.
- Default SMS app (`ROLE_SMS`): `SmsDeliverReceiver` → `MessageHandler` stores every text in the SMS provider, logs it, and notifies only for NOTIFY (silent for SILENCED; none for QUIET_INBOX/BLOCKED). `ThreadScreen` shows a conversation and sends replies. Role check uses `RoleManager.isRoleHeld` (`Telephony.Sms.getDefaultSmsPackage` is unreliable on API 36).
- Both roles are requested from Settings → PERMISSIONS (`DEFAULT PHONE APP`, `DEFAULT SMS APP`, shown as HELD / NOT HELD and re-read on resume) and, for SMS, from the Messages banner.
- Not default: `SmsReceiver` (`SMS_RECEIVED`) only logs; the system SMS app still notifies. MMS download is not implemented.
- `AppBridgeListener : NotificationListenerService`: parses WhatsApp call notifications, resolves, shows `OverlayCard` (TYPE_APPLICATION_OVERLAY) for ~6 s when enabled and a name is found.
