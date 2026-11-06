# Why some texts ring and others stay quiet

CallGuard sorts each text into a kind and checks its sender, then picks one outcome for it. This page explains that order and the reasons for it. The switches that control the order are in the [settings reference](../reference/settings.md).

## The tension

A text from someone who is not in your contacts can be a real bank alert or a scam. The two can look alike. CallGuard cannot ring for every unknown sender, and it cannot silence all of them either. It needs one fixed order that decides each case.

## The outcomes

Each text ends in one of four outcomes. The outcome sets the alert, and it sets where the text shows.

| Outcome | Alert | Where the text shows |
|---|---|---|
| Notify | A sound and a notification | Inbox |
| Silenced | A notification with no sound | Inbox |
| Quiet inbox | No notification | Spam tab, marked as read |
| Blocked | No notification | Spam tab, marked as read |

These outcomes apply only when CallGuard is the default SMS app. Any other default SMS app stores the text and shows the alert. CallGuard only logs the outcome in that case. See [Make CallGuard the default SMS app](../how-to/set-as-default-sms-app.md).

## The order

The app checks these rules in order and stops at the first rule that matches.

1. The sender is on the block list. The outcome is blocked.
2. The sender is on the allow list. The outcome is notify.
3. You set ALWAYS RING or MUTE SENDER for the sender. The outcome is notify, or silenced for MUTE SENDER.
4. The sender is a contact. The outcome is notify, or quiet inbox when the number is marked as spam.
5. The text scores 0.6 or more for spam. The outcome is quiet inbox.
6. The kind of the text rings, and the number is not marked as spam. The outcome is notify.
7. CONTACTS ONLY is on. The outcome is blocked.
8. IGNORE MODE is on. The outcome is quiet inbox.
9. SILENCE MODE is on. The outcome is silenced.
10. The number is marked as spam. The outcome is quiet inbox.
11. No rule matched. The outcome is notify.

Rules 3, 5, and 6 are new. The other rules keep their old outcomes.

## What counts as spam

Two checks can mark a text as spam. The spam score is the classifier's score for the text, from 0 to 1. A score of 0.6 or more counts as spam.

The number reputation is a second check on the sender's number. It uses your spam marks, a bundled list of known spam numbers, and the call pattern of the number.

## The kinds

The classifier gives each text one kind. Each kind has a switch under TEXTS THAT RING on the Guard tab.

| Kind | Switch | Typical text | Rings by default |
|---|---|---|---|
| OTP | OTP | Login codes and one-time passwords | Yes |
| FINANCE | BANKS & INVESTMENTS | Bank, card, UPI, and broker alerts | Yes |
| GOVERNMENT | GOVERNMENT | Tax, passport, and other official notices | Yes |
| TRANSACTIONAL | SERVICE & BILLS | Service and bill notices from a registered sender ID or short code | Yes |
| DELIVERY | DELIVERY | Order, courier, and tracking updates | Yes |
| PERSONAL | PERSONAL | Texts from a phone number, when no other kind applies | No |
| PROMOTIONAL | PROMOTIONS | Offers, deals, and advertising | No |
| SCAM | (no switch) | Likely fraud or phishing. Always goes to the quiet inbox | No |

A text that is neither a scam nor a promotion gets its specific kind first, such as OTP or DELIVERY. Otherwise the scam and promotion checks decide. A text that matches no kind is TRANSACTIONAL when it comes from a registered sender ID or short code, and PERSONAL otherwise.

## Why spam comes before the kinds

A text can look like a bank message and still be a scam. The spam check runs before the kind check. So a text that scores 0.6 or more goes to the quiet inbox, even when its kind is switched on.

## Why contacts are not scored

The classifier does not change how a text from a contact is handled. That is why the TEXTS THAT RING switches only affect senders who are not contacts. The number reputation still applies to contacts.

## Sender actions

ALWAYS RING and MUTE SENDER come before the kind and spam rules. ALWAYS RING wins over CONTACTS ONLY, the kind switches, and the spam score. MUTE SENDER silences a text whatever its kind or score. The block list and the allow list still come first.

An Indian DLT sender ID, such as VM-HDFCBK-S, is stored under its brand header, HDFCBK. So one action also covers AX-HDFCBK and other prefixes of that brand.

## The costs

A text from a contact rings even when it looks like spam, unless the number is marked as spam. The classifier does not check it.

A real alert with a high spam score goes to the quiet inbox. To fix that sender, use ALWAYS RING or Allow in the conversation sheet.

## Where the decision happens

The app reads each text when it arrives, on a background thread. The classifier runs on the phone, and it does not send the text anywhere. The classifier uses fixed rules plus a small word model that learns from your spam marks.

## Why the Spam tab also uses the score

The order above decides each text when it arrives. A text that arrived before CallGuard was the default SMS app has no outcome. So the Messages list also checks the score: a sender whose newest received text scores 0.6 or more shows on the Spam tab. The allow list, the block list, and your own spam mark come first. Like rule 4, the score never moves a contact.

## What a spam mark changes

When you mark a conversation SPAM or NOT SPAM on the Messages screen, three things happen. The number reputation records your mark, so rules 4, 6, and 10 use it for new texts. The conversation moves at once: SPAM to the Spam tab, NOT SPAM to the Inbox. The classifier also learns the newest text from that sender and saves what it learned on the phone.

The word model adds to the score only after it has learned 20 texts. Until then, your marks change the number reputation and the tab, not the spam score. See [Filter texts by kind and act on many at once](../how-to/filter-and-act-on-texts.md).

## Related

- [CallGuard settings reference](../reference/settings.md): every switch, its storage key, and its default.
- [Tune what CallGuard blocks](../how-to/tune-blocking-rules.md): the rules for calls and texts, and how to change them.
- [Manage the allow and block lists](../how-to/manage-lists.md): the lists that the order checks first.
- [Filter texts by kind and act on many at once](../how-to/filter-and-act-on-texts.md): the two tabs, the kind chips, and bulk actions.
