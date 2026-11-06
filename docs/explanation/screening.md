# Why CallGuard answers a call in 2.5 seconds and never twice

A cellular call gives CallGuard one callback and a few seconds to decide between allowing, silencing, and blocking it. This page explains the ordered rule model, why the deadline fallback gives up accuracy rather than risk a late answer, and why contacts-only blocking is the app's own rule rather than something the platform does for it.

The decision rules themselves are product rules and live in [Call Screening](../blueprint/domains/call-screening.md). What follows is the reasoning behind the mechanism that applies them.

## One callback, one answer, a short budget

`CallGuardScreeningService.onScreenCall` receives a call, and the platform waits for a `CallResponse`. The service has to send exactly one answer. A second answer for the same call is not a correction; it is an error the platform is not expecting.

Two things follow. The work is split so that nothing slow sits between the callback and the answer, and the answer itself is guarded. `CallAnswer` holds a lock and a nullable `sent` value: the first decision to arrive is sent and remembered, and any later decision for that call is dropped. Both the full screening and the deadline fallback go through it.

The full screening runs on a two-thread daemon pool, because it reads the phonebook, the cache, and the reputation table. A single scheduled thread runs the deadline, so a busy worker cannot delay it.

## Why the rules are an ordered list

The policy evaluates conditions in a fixed order and returns at the first match. There is no weighting and no score. The order is the design: a blocked number is blocked whatever else is true about it, an allow-listed number rings even with contacts only and silence mode on, and spam never blocks a call unless the user turned on `BLOCK SPAM`.

An ordered list was chosen because it can be tested as a table. `ScreeningPolicyTest` covers each branch as a plain JVM test with no Android types, which is only possible because the policy takes an identity, a spam assessment, a settings record, and a Boolean. The costs of the choice:

- No rule can be overridden by a more specific one. A number that is both allow-listed and a heavy repeat caller rings, because the allow list is checked first.
- The order is a product decision that has to be defended in prose. A scoring model would hide the same decision inside weights.
- Silence mode cannot act on a number with a resolved name, even a name the app learned itself. The unknown test is "not in contacts and no name from any source", which is stricter than "not in contacts".

## Why the deadline is 2.5 seconds

`SCREENING_DEADLINE_MILLIS` is `2500`. The platform allows a few seconds, and the constant leaves a margin for the answer to travel back. The timer fires an answer computed before the full screening started: the block list applies, and everything else is allowed.

The fallback is deliberately narrow. Its job is to make sure the one thing the user explicitly asked for still happens when the phone is slow: a blocked number is still rejected. Everything else rings. A ring that should have been silenced is recoverable, since the user can ignore the call and mark the number afterwards. A block that arrives after the call already rang would be a lie in the log.

The answer that went out is the one that gets logged, so a call handled by the fallback is recorded as `ALLOWED` or `BLOCKED` according to what actually happened, not according to what the screening would have said.

## Why contacts-only blocking works at all

Android binds a third-party screening service for calls from contacts as well. Confirmed on an API 36 device with CallGuard holding `ROLE_CALL_SCREENING`: a call from a saved contact was bound to `CallGuardScreeningService` and screened, while Telecom's own record for that call carried `mIsInContacts=true`. The calls that reach `onScreenCall` are therefore all of them, not a set the platform has already filtered down to strangers.

The contact check is the app's own, done at decision time. `ScreeningPolicy.decide` is handed `graph.isInContacts(number)` as a plain Boolean and applies `contactsOnly` itself, so the rule blocks a number outside the contacts and allows one inside them. That is what makes contacts-only both accurate and effective: contacts ring because no rule ever blocks a contact, and non-contacts are blocked because the app looked, not because the platform skipped them.

Two consequences follow:

- Without `READ_CONTACTS`, `isInContacts` is always false, so contacts only would block every call that reaches screening. `AppGraph.effectiveSettings()` clears `contactsOnly` in that state, which keeps the policy pure and moves the permission question out of the decision.
- With the dialer role held, `CallGuardInCallService` receives every call, contacts included. That changes who draws the call, not who screens it: a contact's call is screened and logged like any other, and `CallManager.logUnscreened` is left for calls that end without a screening verdict.

## Why the verdict is stored in memory

`ScreeningVerdicts` keeps the decision for each call key, and two other components read it.

The screening service records the verdict before it calls `respondToCall`, because the platform can add the call to the in-call service immediately after the answer. The in-call service then peeks the verdict when the call is added, which tells it two things: whether screening silenced this call, and, when the call ends, whether screening already logged it. A call with a verdict is not logged a second time.

The record has to outlive the call because an outgoing call can be screened after Telecom has added it. Entries expire after one hour, and the in-call service forgets the entry when Telecom removes the call. A number with no usable handle is keyed as `PRIVATE`, which is also what the log stores for a hidden caller.

## What happens after the answer

Nothing in this section delays the call, because all of it runs after the response was sent.

The call is logged with the answer that went out, and a CNAP name is learned into the cache with origin `call`. For an incoming call with a number, the service then posts either a caller ID card or a blocked notice. The card shows the name or `UNKNOWN`, the number, the source, the confidence, and the spam score when the number is likely spam.

Three conditions change that. A blocked call gets a low-importance notice on the `blocked` channel and no card. While CallGuard holds the dialer role, no card and no overlay are posted, because the in-call screen already shows who is calling. The overlay card appears only with the overlay permission; without it, `OverlayCard` posts a heads-up notification on the `caller_id` channel instead.

## What the model costs

- A slow phonebook or a slow cache read makes an unknown call ring. The fallback prefers that to a silence it cannot justify.
- Screening is a snapshot. A name learned after the call was answered does not change the verdict for that call.
- The three decisions map onto platform flags rather than onto three distinct behaviors. `BLOCK` sets `disallowCall`, `rejectCall`, and `skipNotification`; `SILENCE` sets only `silenceCall`; `ALLOW` sets nothing and leaves the platform defaults. How a silenced call looks on a given device is the platform's decision, not the app's.
- The rule list is not user-editable. The Guard tab exposes the modes and the two lists, and no per-rule configuration.

## Related

- [Call Screening domain specification](../blueprint/domains/call-screening.md) for the authoritative rules and their invariants.
- [Why CallGuard asks the network for a name first](caller-identity.md) for how the identity that feeds the decision is resolved.
- [Permissions and roles reference](../reference/permissions-and-roles.md) for the call screening role and what is lost without it.
