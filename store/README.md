# CallGuard Play Store graphics

## Files
- `icon-512.png`: 512 x 512 app icon, no alpha. Source: `src/icon-512.html`.
- `feature-graphic.png`: 1024 x 500, no alpha. Source: `src/feature.html`.
- `screenshots/01.png` to `06.png`: 1080 x 1920, no alpha. Sources: `src/slide-01.html` to `src/slide-06.html`.
- `src/icon.svg`: shield vector master, generated from `app/src/main/res/drawable/ic_launcher_foreground.xml` (74 dots, one red).
- `src/store.css`, `src/slide.css`: shared styles. Fonts load from `../../app/src/main/res/font/`.
- `raw/`: unedited emulator screenshots (1080 x 2400) used by the templates.
- `listing.md`: Play listing copy.
- `render.sh`: re-renders everything.

## Editing captions
Open `src/slide-0N.html`, change the `<h1>` (2 lines max, keep the `<br>`) and the `<p class="sub">` (one line). Then re-render.

## Re-rendering
Needs Google Chrome at `/Applications/Google Chrome.app`.

    sh store/render.sh              # all graphics
    sh store/render.sh slide-03     # one slide

## Raw captures
- `a-incoming-call.png`: incoming-call screen from the app's PREVIEW CALL (normal caller).
- `b-spam-call.png`: same preview, tapped to the spam variant.
- `c-guard-home.png`: Guard home.
- `d-keypad-who.png`: keypad with a number and the Who is this card.
- `e-contacts.png`: contacts list with the alphabet scroller.
- `f-messages.png`: messages inbox with four conversations, their unread counts, and the Inbox and Quiet Inbox tabs.
- `g-settings.png`: settings with Contacts only on.
- `h-active-call.png`: active call.
