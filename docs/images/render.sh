#!/bin/sh
# Re-render every documentation screenshot from docs/images/src/ with headless Chrome.
# Usage: docs/images/render.sh            (renders all eight shots)
#        docs/images/render.sh keypad     (renders a single shot by name)
#
# Output: 900x1200 PNGs in docs/images/. The canvas is wider than the phone frame
# on purpose: the captures are 1080x2400 (ratio 0.45), so the phone is centred at
# that true ratio inside a 3:4 canvas instead of being stretched to it.
set -eu

HERE=$(cd "$(dirname "$0")" && pwd)
SRC="$HERE/src"
RAW="$HERE/../../store/raw"
CSS="$SRC/docs.css"
CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
PROFILE="${TMPDIR:-/tmp}/callguard-docs-chrome-profile"
ONLY="${1:-all}"

# name:raw-capture pairs, in documentation order.
SHOTS="guard-home:c-guard-home \
incoming-call:a-incoming-call \
spam-call:b-spam-call \
keypad:d-keypad-who \
contacts:e-contacts \
messages:f-messages \
settings:g-settings \
active-call:h-active-call"

if [ ! -x "$CHROME" ]; then
  echo "Chrome not found at: $CHROME" >&2
  exit 1
fi
if [ ! -f "$CSS" ]; then
  echo "Missing stylesheet: $CSS" >&2
  exit 1
fi

# Guard the failure mode that actually breaks these renders: the .screen box must
# hold the 0.45 capture ratio, otherwise object-fit: cover slices UI off top/bottom.
read -r SCREEN_W SCREEN_H <<EOF
$(awk '/^\.doc \.screen \{/ { found=1; next }
       found && /width:/  { w=$2; h=$4; gsub(/[a-z;]/, "", w); gsub(/[a-z;]/, "", h); print w, h; exit }' "$CSS")
EOF
if [ -z "${SCREEN_W:-}" ] || [ -z "${SCREEN_H:-}" ]; then
  echo "Could not read the .screen width/height from $CSS" >&2
  exit 1
fi
if ! awk -v w="$SCREEN_W" -v h="$SCREEN_H" 'BEGIN { d = h - w / 0.45; if (d < 0) d = -d; exit !(d < 0.5) }'; then
  echo "Screen box is ${SCREEN_W}x${SCREEN_H}, which is not the 0.45 capture ratio." >&2
  echo "Set height to width / 0.45, or object-fit: cover will crop the screenshot." >&2
  exit 1
fi

# Every shot needs its raw capture; fail early with the list of gaps.
missing=""
for pair in $SHOTS; do
  cap=${pair##*:}
  [ -f "$RAW/$cap.png" ] || missing="$missing $cap.png"
done
if [ -n "$missing" ]; then
  echo "Missing raw captures in store/raw/:$missing" >&2
  echo "Shots that use a missing capture will fail to render." >&2
fi

want() { [ "$ONLY" = "all" ] || [ "$ONLY" = "$1" ]; }

if [ "$ONLY" != "all" ]; then
  known=""
  for pair in $SHOTS; do
    [ "${pair%%:*}" = "$ONLY" ] && known=yes
  done
  if [ -z "$known" ]; then
    echo "Unknown shot: $ONLY" >&2
    echo "Known shots: $(for pair in $SHOTS; do printf '%s ' "${pair%%:*}"; done)" >&2
    exit 1
  fi
fi

shot() {
  # $1 html file, $2 output png, $3 width, $4 height
  "$CHROME" --headless=new --user-data-dir="$PROFILE" --hide-scrollbars \
    --force-device-scale-factor=1 --window-size="$3,$4" --virtual-time-budget=3000 \
    --screenshot="$2" "file://$1" >/dev/null 2>&1
  sz=$(sips -g pixelWidth -g pixelHeight "$2" | awk '/pixel/{printf "%s ", $2}')
  echo "  $(basename "$2")  $sz"
}

for pair in $SHOTS; do
  name=${pair%%:*}
  if want "$name"; then
    shot "$SRC/$name.html" "$HERE/$name.png" 900 1200
  fi
done

# Keep the output directory clean for git.
find "$HERE" -name '.DS_Store' -delete 2>/dev/null || true
echo "done"
