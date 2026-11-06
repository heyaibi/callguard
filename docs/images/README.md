# CallGuard documentation screenshots

One PNG per app screen, rendered from the raw device captures in `store/raw/`.
Every image is a 900x1200 canvas with the phone centred at the captures' true
0.45 ratio (1080x2400). Do not edit the PNGs by hand — edit `src/` and re-render.

| Image | Screen shown | Source capture |
| --- | --- | --- |
| `guard-home.png` | Guard home: black list, contacts scanned and calls filtered counters, plus quick actions | `store/raw/c-guard-home.png` |
| `incoming-call.png` | Incoming call with caller ID and network caller name | `store/raw/a-incoming-call.png` |
| `spam-call.png` | Incoming call flagged as spam, with spam likelihood and reasons | `store/raw/b-spam-call.png` |
| `keypad.png` | Dial pad with a typed number and the caller lookup shown above it | `store/raw/d-keypad-who.png` |
| `contacts.png` | Contacts list with per-contact trust state badges | `store/raw/e-contacts.png` |
| `messages.png` | Messages inbox: four conversations with unread counts and the Inbox and Quiet Inbox tabs | `store/raw/f-messages.png` |
| `settings.png` | Settings: blocking toggles and caller ID data sources | `store/raw/g-settings.png` |
| `active-call.png` | Active call with elapsed timer and hold, mute and speaker controls | `store/raw/h-active-call.png` |

## Notes on the captures

- `f-messages.png` was captured with CallGuard set as the default SMS app and
  READ SMS granted, so it shows the conversation list rather than a permission
  banner. The Inbox tab is selected and the Quiet Inbox was empty when the shot
  was taken.
- Every capture shows the screen its filename names.

## Rendering

```sh
docs/images/render.sh            # all eight
docs/images/render.sh keypad     # one shot by name
```

The script checks that the `.screen` box in `src/docs.css` still holds the 0.45
capture ratio before rendering. If that ratio drifts, `object-fit: cover` starts
cropping the top and bottom off the screenshots.
