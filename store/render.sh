#!/bin/sh
# Re-render every Play Store graphic from store/src/ with headless Chrome.
# Usage: store/render.sh            (renders all)
#        store/render.sh slide-03   (renders one slide by name)
set -eu

HERE=$(cd "$(dirname "$0")" && pwd)
SRC="$HERE/src"
RAW="$HERE/raw"
CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
PROFILE="${TMPDIR:-/tmp}/callguard-store-chrome-profile"
ONLY="${1:-all}"

if [ ! -x "$CHROME" ]; then
  echo "Chrome not found at: $CHROME" >&2
  exit 1
fi

# Every screenshot slide needs its raw capture; fail early with the list of gaps.
missing=""
for f in a-incoming-call b-spam-call c-guard-home d-keypad-who e-contacts f-messages g-settings h-active-call; do
  [ -f "$RAW/$f.png" ] || missing="$missing $f.png"
done
if [ -n "$missing" ]; then
  echo "Missing raw captures in store/raw/:$missing" >&2
  echo "Slides that use a missing capture will fail to render." >&2
fi

shot() {
  # $1 html file, $2 output png, $3 width, $4 height
  "$CHROME" --headless=new --user-data-dir="$PROFILE" --hide-scrollbars \
    --force-device-scale-factor=1 --window-size="$3,$4" --virtual-time-budget=3000 \
    --screenshot="$2" "file://$1" >/dev/null 2>&1
  sz=$(sips -g pixelWidth -g pixelHeight "$2" | awk '/pixel/{printf "%s ", $2}')
  echo "  $(basename "$2")  $sz"
}

want() { [ "$ONLY" = "all" ] || [ "$ONLY" = "$1" ]; }

if want icon-512; then
  shot "$SRC/icon-512.html" "$HERE/icon-512.png" 512 512
fi
if want feature-graphic; then
  shot "$SRC/feature.html" "$HERE/feature-graphic.png" 1024 500
fi
for n in 1 2 3 4 5 6; do
  if want "slide-0$n"; then
    mkdir -p "$HERE/screenshots"
    shot "$SRC/slide-0$n.html" "$HERE/screenshots/0$n.png" 1080 1920
  fi
done
echo "done"
