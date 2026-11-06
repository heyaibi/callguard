# Filter texts by kind and act on many at once

Narrow the **Messages** list to the kinds of text you want, then mark read, block, allow, mark spam, or delete many conversations in one step. Mark read and delete change the texts only when CallGuard is the default SMS app; see [Make CallGuard the default SMS app](set-as-default-sms-app.md).

## The two tabs

**INBOX** holds the texts that reached you. **SPAM** holds spam, held-back, and blocked senders, with no alerts. A sender goes to **SPAM** when its newest text scores 60 or more for spam, when you mark it spam, or when the filter rules held it back. The allow list and a NOT SPAM mark keep a sender in **INBOX**. A contact never moves to **SPAM** on its score alone.

Each row shows the kind of its newest received text under the time, such as `BANKS`. On the **SPAM** tab the row also shows the spam score, such as `SCAM · 87`.

## Filter by kind

1. Open the **Messages** tab and pick **INBOX** or **SPAM**.
2. In the chip row under the tabs, tap one or more kinds, such as **OTP** or **BANKS**. The row lists only the kinds that the open tab holds.
3. Tap a chip again to drop it. With no chip on, every kind shows.

Switching tabs clears the chips. A conversation with only texts you sent has no kind, so it is hidden while a chip is on. When nothing matches, the list says `NO MATCHES`.

## Act on many conversations

1. Tap **SELECT** at the right end of the line under the tabs.
2. Tap each conversation you want. A white check marks it. Tap **ALL** to pick every visible conversation, and tap **ALL** again to clear them.
3. Tap an action in the bar at the bottom:

| Action | What it does |
|---|---|
| **READ** | Marks the texts read and clears their notifications. |
| **BLOCK** | Adds the senders to the block list. |
| **ALLOW** (**INBOX** on the Spam tab) | Adds the senders to the allow list. |
| **SPAM** | Marks the numbers as spam and moves the conversations to **SPAM**. |
| **NOT SPAM** | Marks the numbers as not spam and moves the conversations to **INBOX**. |
| **DELETE** | Asks first, then deletes the conversations from the phone. |

The action applies only to picked conversations that the chips still show. Selection ends after the action. To leave without acting, tap **DONE** or press Back.

## Verify

After **SPAM**, the picked conversations leave **INBOX**, and the **SPAM** count goes up by the same number. After **DELETE**, the conversations are gone from both tabs.

## Troubleshooting

- **The action buttons are dim**: no visible conversation is picked. Pick one, or clear the chips so the picked ones show again.
- **A spam-marked sender is still in INBOX**: the sender is on the allow list, which wins over a spam mark. Remove it first; see [Manage the allow and block lists](manage-lists.md).
- **READ leaves the texts unread**: CallGuard is not the default SMS app, so it clears the notification but cannot change the texts.

## Related

- [Why some texts ring and others stay quiet](../explanation/message-filtering.md): the kinds, the spam score, and what a spam mark changes.
- [Manage the allow and block lists](manage-lists.md)
