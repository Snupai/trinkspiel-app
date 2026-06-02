#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
source "$ROOT_DIR/scripts/android-sdk-env.sh"
ensure_android_sdk_tools_on_path

OUT_DIR="build/device-refresh-handoff"

usage() {
  printf '%s\n' "Usage: scripts/prepare-device-refresh-handoff.sh [--out DIR]"
  printf '%s\n' "Writes a non-secret handoff folder for refreshing connected UI tests and Play Store screenshots."
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --out)
      OUT_DIR="${2:-}"
      if [[ -z "$OUT_DIR" ]]; then
        usage >&2
        exit 2
      fi
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      usage >&2
      exit 2
      ;;
  esac
done

mkdir -p "$OUT_DIR"
rm -f "$OUT_DIR"/*

generated_at="$(date '+%Y-%m-%d %H:%M:%S %Z')"
version_name="$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' app/build.gradle.kts | head -n 1)"
version_code="$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' app/build.gradle.kts | head -n 1)"

{
  printf '# Seemops Device Refresh Handoff\n\n'
  printf 'Generated: %s\n\n' "$generated_at"
  printf 'App version: %s (%s)\n\n' "${version_name:-unknown}" "${version_code:-unknown}"
  printf 'Use this folder when a phone-sized Android device or emulator is available. It contains no secrets.\n\n'
  printf '## Required Device Pass\n\n'
  printf '1. Attach exactly one phone-sized Android device, or start one emulator and set `ANDROID_SERIAL` if multiple devices are connected.\n'
  printf '2. From the repository root, run:\n\n'
  printf '```sh\n'
  printf 'scripts/run-connected-ui-tests.sh\n'
  printf 'scripts/capture-store-screenshots.sh\n'
  printf 'scripts/verify-release-ready.sh --skip-build\n'
  printf '```\n\n'
  printf '3. The verifier must report clean connected Android tests and current screenshots before final packaging.\n\n'
  printf '## Expected Evidence\n\n'
  printf '%s\n' '- `scripts/run-connected-ui-tests.sh` should run at least 16 clean connected tests.'
  printf '%s\n' '- `scripts/capture-store-screenshots.sh` should refresh the six files under `docs/store-assets/screenshots/phone/`.'
  printf '%s\n\n' '- `scripts/verify-release-ready.sh --skip-build` should no longer report stale connected Android test results or stale screenshots.'
  printf '## Current Workspace Device Snapshot\n\n'
  printf 'See `current-device-status.txt` for the current `adb devices`, AVD list, and script dry-run output.\n'
} > "$OUT_DIR/README.md"

{
  printf 'Generated: %s\n\n' "$generated_at"
  printf 'adb path: '
  if command -v adb >/dev/null 2>&1; then
    command -v adb
  else
    printf 'not available\n'
  fi
  printf '\n'

  printf 'adb devices:\n'
  if command -v adb >/dev/null 2>&1; then
    adb devices || true
  else
    android_sdk_tools_hint
  fi
  printf '\n'

  printf 'AVDs:\n'
  if command -v emulator >/dev/null 2>&1; then
    emulator -list-avds || true
  elif [[ -n "${ANDROID_HOME:-}" && -x "$ANDROID_HOME/emulator/emulator" ]]; then
    "$ANDROID_HOME/emulator/emulator" -list-avds || true
  else
    printf 'emulator command not available\n'
  fi
  printf '\n'

  printf 'scripts/run-connected-ui-tests.sh dry-run result:\n'
  scripts/run-connected-ui-tests.sh || true
  printf '\n'

  printf 'scripts/capture-store-screenshots.sh --skip-install dry-run result:\n'
  scripts/capture-store-screenshots.sh --skip-install || true
} > "$OUT_DIR/current-device-status.txt" 2>&1

(
  cd "$OUT_DIR"
  shasum -a 256 README.md current-device-status.txt > checksums.sha256
)

printf 'Device refresh handoff folder written to %s\n' "$OUT_DIR"
