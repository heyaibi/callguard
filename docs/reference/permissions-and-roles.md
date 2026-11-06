# Permissions and roles reference

Every permission and role CallGuard declares, where the code uses it, what stops working without it, and whether the app needs it to run.

The manifest is `app/src/main/AndroidManifest.xml` and is the only place these are declared. Runtime requests come from the screens named in each row; nothing requests a permission on app start.

## Runtime permissions

These show a system prompt and can be refused.

| Permission | Where the code uses it | Without it | Ask for it from |
|---|---|---|---|
| `READ_CONTACTS` | `LocalContactSource` for name lookup and `contains`, `Phonebook.loadPhonebook`, the contact search in `ComposeScreen` and `KeypadScreen` | No contact names; contacts only is forced off (`AppGraph.effectiveSettings`); the Contacts screen and contact search are empty | Settings → CONTACTS, the home banner, the Contacts screen, the keypad, the compose screen |
| `READ_CALL_LOG` | `hasReadCallLog` and `readSystemCalls` in `calllog/RecentCalls.kt`, for the Recents screen | Recents shows only the calls CallGuard logged, and a `Phone call history is off` banner appears | Recents, `Allow call log` |
| `CALL_PHONE` | `Dialer.placeCall`, `DialActivity`, `ui/Calls.kt` | `placeCall` returns false. `ui/Calls.kt` then opens the system dialer with the number; `DialActivity` shows `This number cannot be called.` | Dial time, per call |
| `WRITE_CONTACTS` | `Phonebook.setStarred` and `Phonebook.deleteContact` | Star and delete return false and the contact is unchanged | Contacts screen |
| `RECEIVE_SMS` | `SmsDeliverReceiver` (default SMS app), `SmsReceiver` (`SMS_RECEIVED`) | No texts arrive; the Settings `SMS` row reads not granted | Settings → SMS, with `READ_SMS` |
| `READ_SMS` | `MessagesScreen`, `ThreadScreen`, `SmsProvider` | The conversation list and threads cannot be read | Settings → SMS, the messages screen, the thread screen |
| `SEND_SMS` | `ThreadScreen` reply, `HeadlessSmsSendService` | Replies cannot be sent and the send button is disabled | Thread screen |
| `POST_NOTIFICATIONS` | `CallerIdNotifier`, `MessageNotifier`, `CallNotifier`, `OverlayCard` fallback | No caller ID card, no blocked-call notice, no ringing notification, no message alert | Settings → NOTIFICATIONS |
| `RECEIVE_MMS` | Declared only. No call site in `app/src/main/java` | Nothing observed. MMS download is not implemented | Not requested |
| `RECEIVE_WAP_PUSH` | Declared only. No call site in `app/src/main/java` | Nothing observed. It is part of the MMS receiver set the SMS role expects, alongside `RECEIVE_MMS` | Not requested |

<!-- VERIFY: RECEIVE_MMS has no code path that reads or checks it. It is declared likely because the SMS role expects a complete MMS receiver set. The platform's role criteria would need checking to confirm the declaration is required. -->

`POST_NOTIFICATIONS` is only requested on API 33 and above. The Settings row reads as granted below that (`Perms.read` in `SettingsScreen.kt`).

## Permissions granted without a prompt

| Permission | Where the code uses it | Without it | How it is granted |
|---|---|---|---|
| `SYSTEM_ALERT_WINDOW` | `OverlayCard.canShow` and `OverlayCard.show` for the caller card over other apps | The card is posted as a heads-up notification on the `caller_id` channel instead | Special access, `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`; the Settings → OVERLAY row opens it |
| `USE_FULL_SCREEN_INTENT` | `CallNotifier.setFullScreenIntent` for a ringing call while the screen is off | The ringing call shows as a heads-up notification and does not take over the screen | No prompt |
| `VIBRATE` | `CallRinger` | An incoming call rings without vibration | No prompt |
| `FOREGROUND_SERVICE` | `CallForegroundService` | The service start is refused; the in-progress call is posted as a plain notification instead (`CallManager.syncInProgress`) | No prompt |
| `FOREGROUND_SERVICE_PHONE_CALL` | The same service, declared `android:foregroundServiceType="phoneCall"` | `startForeground` with the phone-call type fails, with the same fallback | No prompt |

## Declared with no call site

These are in the manifest and nothing in `app/src/main/java` reads or checks them. They are listed separately so a reader does not assume a behavior that does not exist.

| Permission | Status |
|---|---|
| `READ_PHONE_STATE` | Declared. No permission check, request, or telephony state read in the code. |
| `ANSWER_PHONE_CALLS` | Declared. CallGuard answers calls through `Call.answer` in `CallManager`, which the in-call service provides, not through this permission. |
| `WRITE_CALL_LOG` | Declared. `calllog/RecentCalls.kt` reads `CallLog.Calls` and never writes to it. CallGuard keeps its own log in `call_log`. |

<!-- VERIFY: no call site exists for these three, and the manifest comment does not explain them. They may be left from an earlier design, or required by a role the app asks for. Removing them without checking the role requirements risks losing the dialer or SMS role. -->

CallGuard declares no `INTERNET` permission. Nothing in the app can open a network connection, which is why `OnlineLookupSource` returns no name.

## Roles

Roles come from `android.app.role.RoleManager`. The user grants each one from a system dialog, and the app reads the state with `isRoleHeld`.

| Role | Service or component it binds | Without it |
|---|---|---|
| `ROLE_CALL_SCREENING` | `CallGuardScreeningService` through `BIND_SCREENING_SERVICE` and the `android.telecom.CallScreeningService` intent filter | No call is screened. Silence, block, contacts only and spam rules have no effect on calls, and no caller ID card is posted for calls. |
| `ROLE_DIALER` | `CallGuardInCallService` through `BIND_INCALL_SERVICE`, with `IN_CALL_SERVICE_UI` and `IN_CALL_SERVICE_RINGING` both true | No in-call screen, no CallGuard ringing, no foreground call service, and no logging of calls that screening never answered. Calls go to whichever app holds the role. |
| `ROLE_SMS` | `SmsDeliverReceiver` and `MmsReceiver` | `SmsReceiver` still receives `SMS_RECEIVED` and logs the text, but the system SMS app stores and notifies it. CallGuard cannot send replies. |

### Requesting each role

`Dialer.requestRoleIntent` builds the `ROLE_DIALER` prompt. `SettingsScreen` builds the other two with `RoleManager.createRequestRoleIntent`. The three rows under Settings → PERMISSIONS show `HELD` or `NOT HELD` and open the prompt when tapped. The Messages screen shows a banner with the `ROLE_SMS` prompt.

`DialActivity` offers the same prompt from its own screen when the role is missing. `MainActivity` starts on the keypad only while the role is held, and on the home screen otherwise.

### What the dialer role changes

The in-call service receives every call on the device, contacts included. Screening is unaffected: `CallGuardScreeningService` is bound for the calls Telecom forwards to screening, which excludes calls from contacts. A contact's call therefore reaches the log through `CallManager.logUnscreened` with no screening verdict, and rings normally.

While the role is held, `CallerIdNotifier` posts no card for answered calls, because the in-call screen already shows the caller. Blocked-call notices and log entries are unchanged.

## Component guards

Six components declare an `android:permission`, which is a requirement on the sender, not a request by CallGuard. The holder of each guard is listed here so a reader can tell which missing grant silently stops a broadcast or a binding.

| Component | Guard | Who holds it |
|---|---|---|
| `SmsDeliverReceiver` | `android.permission.BROADCAST_SMS` | The telephony stack, once CallGuard holds `ROLE_SMS` |
| `MmsReceiver` | `android.permission.BROADCAST_WAP_PUSH` | The telephony stack, once CallGuard holds `ROLE_SMS` |
| `HeadlessSmsSendService` | `android.permission.SEND_RESPOND_VIA_MESSAGE` | The system, for respond-via-message from the dialer |
| `AppBridgeListener` | `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` | The system, once the user enables notification access |
| `CallGuardScreeningService` | `android.permission.BIND_SCREENING_SERVICE` | Telecom, once CallGuard holds `ROLE_CALL_SCREENING` |
| `CallGuardInCallService` | `android.permission.BIND_INCALL_SERVICE` | Telecom, once CallGuard holds `ROLE_DIALER` |

`AppBridgeListener` also needs notification access, which is not a role and not a runtime permission. The Settings → NOTIFICATION ACCESS row opens `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`, and the row reads granted when the package appears in `NotificationManagerCompat.getEnabledListenerPackages`. Without it the bridge service never receives a notification and every switch under APP BRIDGE has no effect.

## Failure modes

- A refused runtime permission leaves the app running. Every lookup and read path catches the failure and returns an empty or null result, so the effect is a missing name or an empty screen rather than a crash.
- `CALL_PHONE` refused at dial time does not drop the call: `placeOrDial` falls back to `Intent.ACTION_DIAL`, which hands the number to the system dialer.
- The dialer role is the only way CallGuard sees calls from contacts. Without it, those calls are neither logged nor shown in the in-call screen.
- Notification access can be revoked at any time from system settings. The bridge stops then, with no in-app warning beyond the Settings row changing to not granted.

## Related

- [Settings reference](settings.md) for the switches that depend on these grants.
- [App Bridge explained](../explanation/app-bridge.md) for what the notification listener can and cannot read.
- [Screening explained](../explanation/screening.md) for how the call screening role shapes the decision path.
