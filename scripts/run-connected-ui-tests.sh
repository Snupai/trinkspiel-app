#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
source "$ROOT_DIR/scripts/android-sdk-env.sh"
ensure_android_sdk_tools_on_path

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  printf '%s\n' "Usage: scripts/run-connected-ui-tests.sh"
  printf '%s\n' "Runs connected Android UI/import tests when an Android device/emulator is attached."
  exit 0
fi

if ! command -v adb >/dev/null 2>&1; then
  printf 'adb is not available. ' >&2
  android_sdk_tools_hint >&2
  exit 3
fi

ATTACHED_DEVICES="$(adb devices | awk 'NR > 1 && $2 == "device" { count++ } END { print count + 0 }')"
if [[ "$ATTACHED_DEVICES" == "0" ]]; then
  printf 'No attached Android device/emulator found.\n' >&2
  printf 'Attach a device or start an emulator, then rerun this script.\n' >&2
  exit 4
fi

printf 'Running connected Android tests on %s attached device(s).\n' "$ATTACHED_DEVICES"
./gradlew :app:connectedDebugAndroidTest --no-daemon
