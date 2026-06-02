#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  printf '%s\n' "Usage: scripts/generate-launcher-icons.sh"
  printf '%s\n' "Renders legacy Android launcher WebP icons from docs/store-assets/source/store-icon.svg."
  exit 0
fi

if ! command -v sips >/dev/null 2>&1; then
  printf 'sips is required to render launcher icons on this machine.\n' >&2
  exit 3
fi
if ! command -v cwebp >/dev/null 2>&1; then
  printf 'cwebp is required to encode launcher icons on this machine.\n' >&2
  exit 4
fi

SOURCE="docs/store-assets/source/store-icon.svg"
FALLBACK_SOURCE="docs/store-assets/store-icon.png"
TMP_DIR="build/generated/launcher-icons"
mkdir -p "$TMP_DIR"

file_mtime() {
  stat -f %m "$1" 2>/dev/null || stat -c %Y "$1" 2>/dev/null || printf '0'
}

BASE_PNG="$TMP_DIR/store-icon.png"
if ! sips -s format png "$SOURCE" --out "$BASE_PNG" > /dev/null 2> "$TMP_DIR/sips-store-icon-error.txt"; then
  if [[ -f "$FALLBACK_SOURCE" ]]; then
    fallback_width="$(sips -g pixelWidth "$FALLBACK_SOURCE" | awk '/pixelWidth/ { print $2 }')"
    fallback_height="$(sips -g pixelHeight "$FALLBACK_SOURCE" | awk '/pixelHeight/ { print $2 }')"
    source_mtime="$(file_mtime "$SOURCE")"
    fallback_mtime="$(file_mtime "$FALLBACK_SOURCE")"
    if [[ "$fallback_width" == "512" &&
      "$fallback_height" == "512" &&
      "$fallback_mtime" -ge "$source_mtime" ]]; then
      cp "$FALLBACK_SOURCE" "$BASE_PNG"
      printf 'Reused current %s as launcher icon source; sips could not rerender %s\n' "$FALLBACK_SOURCE" "$SOURCE"
    else
      cat "$TMP_DIR/sips-store-icon-error.txt" >&2
      printf 'Fallback %s is not a current 512x512 PNG.\n' "$FALLBACK_SOURCE" >&2
      exit 5
    fi
  else
    cat "$TMP_DIR/sips-store-icon-error.txt" >&2
    printf 'Missing fallback launcher source: %s\n' "$FALLBACK_SOURCE" >&2
    exit 5
  fi
fi

render_icon() {
  local density="$1"
  local size="$2"
  local resized="$TMP_DIR/ic_launcher_${density}.png"
  local rendered="$TMP_DIR/ic_launcher_${density}.webp"
  local output="app/src/main/res/mipmap-${density}/ic_launcher.webp"

  mkdir -p "$(dirname "$output")"
  sips -z "$size" "$size" "$BASE_PNG" --out "$resized" >/dev/null
  cwebp -quiet -lossless "$resized" -o "$rendered"
  if [[ -f "$output" ]] && cmp -s "$rendered" "$output"; then
    rm -f "$rendered"
    printf 'Unchanged %s (%sx%s)\n' "$output" "$size" "$size"
  else
    mv "$rendered" "$output"
    printf 'Rendered %s (%sx%s)\n' "$output" "$size" "$size"
  fi
}

render_icon mdpi 48
render_icon hdpi 72
render_icon xhdpi 96
render_icon xxhdpi 144
render_icon xxxhdpi 192
