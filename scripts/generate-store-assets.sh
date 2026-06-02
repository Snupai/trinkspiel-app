#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  printf '%s\n' "Usage: scripts/generate-store-assets.sh"
  printf '%s\n' "Renders Play Store PNG assets from SVG sources in docs/store-assets/source."
  exit 0
fi

if ! command -v sips >/dev/null 2>&1; then
  printf 'sips is required to render store assets on this machine.\n' >&2
  exit 3
fi

ASSET_DIR="docs/store-assets"
SOURCE_DIR="$ASSET_DIR/source"

file_mtime() {
  stat -f %m "$1" 2>/dev/null || stat -c %Y "$1" 2>/dev/null || printf '0'
}

render_png() {
  local source="$1"
  local output="$2"
  local expected_width="$3"
  local expected_height="$4"
  local tmp_dir tmp_output actual_width actual_height source_mtime output_mtime
  tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/seemops-store-asset.XXXXXX")"
  tmp_output="$tmp_dir/rendered.png"

  mkdir -p "$(dirname "$output")"
  if ! sips -s format png "$source" --out "$tmp_output" > /dev/null 2> "$tmp_dir/sips-error.txt"; then
    if [[ -f "$output" ]]; then
      actual_width="$(sips -g pixelWidth "$output" | awk '/pixelWidth/ { print $2 }')"
      actual_height="$(sips -g pixelHeight "$output" | awk '/pixelHeight/ { print $2 }')"
      source_mtime="$(file_mtime "$source")"
      output_mtime="$(file_mtime "$output")"
      if [[ "$actual_width" == "$expected_width" &&
        "$actual_height" == "$expected_height" &&
        "$output_mtime" -ge "$source_mtime" ]]; then
        rm -rf "$tmp_dir"
        printf 'Reused current %s (%sx%s); sips could not rerender %s\n' "$output" "$actual_width" "$actual_height" "$source"
        return
      fi
    fi
    cat "$tmp_dir/sips-error.txt" >&2
    rm -rf "$tmp_dir"
    printf 'Could not render %s, and no current %sx%s %s exists to reuse.\n' "$source" "$expected_width" "$expected_height" "$output" >&2
    exit 5
  fi

  actual_width="$(sips -g pixelWidth "$tmp_output" | awk '/pixelWidth/ { print $2 }')"
  actual_height="$(sips -g pixelHeight "$tmp_output" | awk '/pixelHeight/ { print $2 }')"

  if [[ "$actual_width" != "$expected_width" || "$actual_height" != "$expected_height" ]]; then
    rm -rf "$tmp_dir"
    printf 'Unexpected dimensions for %s: got %sx%s, expected %sx%s\n' \
      "$output" "$actual_width" "$actual_height" "$expected_width" "$expected_height" >&2
    exit 4
  fi

  if [[ -f "$output" ]] && cmp -s "$tmp_output" "$output"; then
    rm -rf "$tmp_dir"
    printf 'Unchanged %s (%sx%s)\n' "$output" "$actual_width" "$actual_height"
  else
    mv "$tmp_output" "$output"
    rm -rf "$tmp_dir"
    printf 'Rendered %s (%sx%s)\n' "$output" "$actual_width" "$actual_height"
  fi
}

render_png "$SOURCE_DIR/feature-graphic.svg" "$ASSET_DIR/feature-graphic.png" 1024 500
render_png "$SOURCE_DIR/store-icon.svg" "$ASSET_DIR/store-icon.png" 512 512
