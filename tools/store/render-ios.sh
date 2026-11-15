#!/usr/bin/env bash
# Renders the App Store screenshots with headless Chrome, in the Play shots' design (shot.html, captions.js).
#
#   tools/store/render-ios.sh            # CHROME=/path/to/chrome to pick the browser
#
# Input: tools/store/raw-ios/<format>/<shot>.png, captured on the Mac by ios-screenshots.sh (iphone69 on an iPhone
# 17 Pro Max, duo-closed and duo-open on the iPhone Duo; simulator sizes). Needs python3 with Pillow for the crop.
# Output: ios/store-screenshots/<format>/1..7.png at the sizes App Store Connect takes.
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
tools="$root/tools/store"
out="$root/ios/store-screenshots"

chrome="${CHROME:-}"
for c in "$(command -v chromium 2>/dev/null || true)" "$(command -v google-chrome 2>/dev/null || true)" \
         "$HOME"/.cache/ms-playwright/chromium_headless_shell-*/chrome-headless-shell-linux64/chrome-headless-shell; do
  [ -z "$chrome" ] && [ -n "$c" ] && [ -x "$c" ] && chrome="$c"
done
[ -n "$chrome" ] || { echo "Chrome not found; set CHROME=/path/to/chrome" >&2; exit 1; }
[ -f "$tools/fonts/google_sans_flex.ttf" ] || { echo "Run render.sh once first: it downloads the font" >&2; exit 1; }

# format, final width × height, layout width, the screen's corner radius as a share of its width
formats=(
  "iphone69 1320 2868 1080 .125"
  "duo-closed 1398 2034 1080 .02"
  "duo-open 2853 2007 1920 .063"
)
# The gallery first: the first screenshot shows what you can make. No home screen shortcut on iOS.
shots=(gallery detail ideas ai home form editor)

for f in "${formats[@]}"; do
  read -r name width height layout radius <<< "$f"
  raw="$tools/raw-ios/$name"
  [ -d "$raw" ] || { echo "skip $name: no $raw"; continue; }
  # Laid out at `layout` pixels wide and scaled up to the final width, so the captions keep the Play sizes.
  scale=$(python3 -c "print($width / $layout)")
  page_h=$(python3 -c "import math; print(math.ceil($height * $layout / $width))")
  ratio=$(python3 -c "from PIL import Image; w, h = Image.open('$raw/home.png').size; print(h / w)")
  mkdir -p "$out/$name"; rm -f "$out/$name"/*.png
  for i in "${!shots[@]}"; do
    file="$out/$name/$((i + 1)).png"
    "$chrome" --headless --disable-gpu --hide-scrollbars --allow-file-access-from-files --force-device-scale-factor="$scale" \
      --virtual-time-budget=4000 --window-size="$layout,$page_h" --screenshot="$file" \
      "file://$tools/shot.html?shot=${shots[$i]}&src=raw-ios/$name/${shots[$i]}.png&w=$layout&h=$page_h&ratio=$ratio&radius=$radius&store=ios" \
      >/dev/null 2>&1
    # Exactly the App Store size, RGB with no alpha.
    python3 -c "from PIL import Image; Image.open('$file').convert('RGB').crop((0, 0, $width, $height)).save('$file')"
  done
  echo "rendered $out/$name"
done
