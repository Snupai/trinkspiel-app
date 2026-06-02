#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  printf '%s\n' "Usage: scripts/check-store-screenshot-polish.sh [PNG ...]"
  printf '%s\n' "Checks Play Store screenshots for a clean demo-mode status bar."
  exit 0
fi

if ! command -v python3 >/dev/null 2>&1; then
  printf '%s\n' "FAIL    python3 is required to inspect screenshot pixels" >&2
  exit 1
fi

SCREENSHOTS=("$@")
if (( ${#SCREENSHOTS[@]} == 0 )); then
  SCREENSHOT_DIR="docs/store-assets/screenshots/phone"
  SCREENSHOTS=(
    "$SCREENSHOT_DIR/01-first-run-setup.png"
    "$SCREENSHOT_DIR/02-game-ready.png"
    "$SCREENSHOT_DIR/03-card-drawn.png"
    "$SCREENSHOT_DIR/04-entry-manager.png"
    "$SCREENSHOT_DIR/05-settings-diagnostics.png"
    "$SCREENSHOT_DIR/06-settings-legal.png"
  )
fi

python3 - "${SCREENSHOTS[@]}" <<'PY'
from __future__ import annotations

import struct
import sys
import zlib
from pathlib import Path


PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
TOP_ROWS = 72
MIN_DARK_PIXELS = 1000
MAX_VIVID_GREEN_PIXELS = 20


def paeth_predictor(left: int, up: int, up_left: int) -> int:
    estimate = left + up - up_left
    left_distance = abs(estimate - left)
    up_distance = abs(estimate - up)
    up_left_distance = abs(estimate - up_left)
    if left_distance <= up_distance and left_distance <= up_left_distance:
        return left
    if up_distance <= up_left_distance:
        return up
    return up_left


def inspect_png(path: Path) -> tuple[int, int, int, int]:
    data = path.read_bytes()
    if not data.startswith(PNG_SIGNATURE):
        raise ValueError("not a PNG file")

    width = height = bit_depth = color_type = None
    idat_chunks: list[bytes] = []
    offset = len(PNG_SIGNATURE)
    while offset < len(data):
        length = struct.unpack(">I", data[offset : offset + 4])[0]
        chunk_type = data[offset + 4 : offset + 8]
        chunk_data = data[offset + 8 : offset + 8 + length]
        offset += 12 + length
        if chunk_type == b"IHDR":
            width, height, bit_depth, color_type, _, _, _ = struct.unpack(
                ">IIBBBBB",
                chunk_data,
            )
        elif chunk_type == b"IDAT":
            idat_chunks.append(chunk_data)
        elif chunk_type == b"IEND":
            break

    if width is None or height is None or bit_depth is None or color_type is None:
        raise ValueError("missing PNG IHDR")
    if bit_depth != 8 or color_type not in (2, 6):
        raise ValueError(f"unsupported PNG format: bit_depth={bit_depth}, color_type={color_type}")

    channels = 4 if color_type == 6 else 3
    bytes_per_pixel = channels
    row_bytes = width * channels
    raw = zlib.decompress(b"".join(idat_chunks))
    previous = bytearray(row_bytes)
    cursor = 0
    dark_pixels = 0
    vivid_green_pixels = 0

    for _ in range(min(height, TOP_ROWS)):
        filter_type = raw[cursor]
        cursor += 1
        row = bytearray(raw[cursor : cursor + row_bytes])
        cursor += row_bytes

        for i in range(row_bytes):
            left = row[i - bytes_per_pixel] if i >= bytes_per_pixel else 0
            up = previous[i]
            up_left = previous[i - bytes_per_pixel] if i >= bytes_per_pixel else 0
            if filter_type == 1:
                row[i] = (row[i] + left) & 0xFF
            elif filter_type == 2:
                row[i] = (row[i] + up) & 0xFF
            elif filter_type == 3:
                row[i] = (row[i] + ((left + up) // 2)) & 0xFF
            elif filter_type == 4:
                row[i] = (row[i] + paeth_predictor(left, up, up_left)) & 0xFF
            elif filter_type != 0:
                raise ValueError(f"unsupported PNG filter: {filter_type}")

        for x in range(0, row_bytes, channels):
            red = row[x]
            green = row[x + 1]
            blue = row[x + 2]
            alpha = row[x + 3] if channels == 4 else 255
            if alpha < 128:
                continue
            if max(red, green, blue) <= 60:
                dark_pixels += 1
            if green >= 160 and green - max(red, blue) >= 80 and red <= 120 and blue <= 150:
                vivid_green_pixels += 1

        previous = row

    return width, height, dark_pixels, vivid_green_pixels


def main() -> int:
    failures = 0
    print("Store screenshot polish check")
    for raw_path in sys.argv[1:]:
        path = Path(raw_path)
        if not path.is_file() or path.stat().st_size == 0:
            print(f"  FAIL    {path}: missing or empty")
            failures += 1
            continue
        try:
            width, height, dark_pixels, vivid_green_pixels = inspect_png(path)
        except Exception as exc:
            print(f"  FAIL    {path}: {exc}")
            failures += 1
            continue

        if dark_pixels < MIN_DARK_PIXELS:
            print(
                f"  FAIL    {path}: status bar has only {dark_pixels} dark pixels; "
                "recapture with System UI demo mode",
            )
            failures += 1
        elif vivid_green_pixels > MAX_VIVID_GREEN_PIXELS:
            print(
                f"  FAIL    {path}: found {vivid_green_pixels} vivid-green status pixels; "
                "recapture with System UI demo mode",
            )
            failures += 1
        else:
            print(
                f"  OK      {path} ({width}x{height}, {dark_pixels} dark status pixels, "
                f"{vivid_green_pixels} vivid-green pixels)",
            )
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
PY
