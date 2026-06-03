#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
source "$ROOT_DIR/scripts/android-sdk-env.sh"
ensure_android_sdk_tools_on_path

OUT_DIR="docs/store-assets/screenshots/phone"
INSTALL_APK=1
SYSTEM_UI_DEMO_ENABLED=0

usage() {
  printf '%s\n' "Usage: scripts/capture-store-screenshots.sh [--skip-install] [--out DIR]"
  printf '%s\n' "Captures Play Store phone screenshots from an attached Android device/emulator."
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-install)
      INSTALL_APK=0
      shift
      ;;
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

if ! command -v adb >/dev/null 2>&1; then
  printf 'adb is not available. ' >&2
  android_sdk_tools_hint >&2
  exit 3
fi

ATTACHED_DEVICES="$(adb devices | awk 'NR > 1 && $2 == "device" { count++ } END { print count + 0 }')"
if [[ "$ATTACHED_DEVICES" == "0" ]]; then
  printf 'No attached Android device/emulator found.\n' >&2
  exit 4
fi
if [[ "$ATTACHED_DEVICES" != "1" && -z "${ANDROID_SERIAL:-}" ]]; then
  printf 'Multiple devices are attached. Set ANDROID_SERIAL, then rerun.\n' >&2
  exit 5
fi

restore_system_ui_demo() {
  if [[ "$SYSTEM_UI_DEMO_ENABLED" == "1" ]]; then
    adb shell am broadcast -a com.android.systemui.demo -e command exit >/dev/null 2>&1 || true
  fi
}
trap restore_system_ui_demo EXIT

prepare_system_ui_demo() {
  adb shell settings put global sysui_demo_allowed 1 >/dev/null 2>&1 || return 0
  SYSTEM_UI_DEMO_ENABLED=1
  adb shell am broadcast -a com.android.systemui.demo -e command enter >/dev/null 2>&1 || true
  adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1000 >/dev/null 2>&1 || true
  adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false >/dev/null 2>&1 || true
  adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e mobile show -e datatype none -e fully true >/dev/null 2>&1 || true
  adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false >/dev/null 2>&1 || true
}

APK="app/build/outputs/apk/debug/app-debug.apk"
if [[ "$INSTALL_APK" == "1" ]]; then
  ./gradlew :app:assembleDebug --no-daemon
  adb install -r "$APK" >/dev/null
elif [[ ! -f "$APK" ]]; then
  printf 'Debug APK is missing: %s\n' "$APK" >&2
  exit 6
fi

mkdir -p "$OUT_DIR"
rm -f "$OUT_DIR"/*.png

dump_ui() {
  local i
  for ((i = 0; i < 8; i++)); do
    if run_with_timeout 12 adb shell timeout 8 uiautomator dump /sdcard/window.xml >/dev/null 2>&1; then
      adb exec-out cat /sdcard/window.xml | tr -d '\r'
      return 0
    fi
    sleep 1
  done
  run_with_timeout 12 adb shell timeout 8 uiautomator dump /sdcard/window.xml >/dev/null
  adb exec-out cat /sdcard/window.xml | tr -d '\r'
}

wait_for_text() {
  local text="$1"
  local attempts="${2:-20}"
  local i xml
  for ((i = 0; i < attempts; i++)); do
    dismiss_system_dialogs
    xml="$(dump_ui)"
    if grep -Fq "$text" <<<"$xml"; then
      return 0
    fi
    sleep 1
  done
  printf 'Timed out waiting for text: %s\n' "$text" >&2
  return 1
}

swipe_up_until_text() {
  local text="$1"
  local attempts="${2:-5}"
  local i xml
  for ((i = 0; i < attempts; i++)); do
    dismiss_system_dialogs
    xml="$(dump_ui)"
    if grep -Fq "$text" <<<"$xml"; then
      return 0
    fi
    adb shell input swipe 540 2000 540 520 350
    sleep 1
  done
  dismiss_system_dialogs
  xml="$(dump_ui)"
  if grep -Fq "$text" <<<"$xml"; then
    return 0
  fi
  printf 'Timed out scrolling to text: %s\n' "$text" >&2
  return 1
}

tap_node() {
  local attr="$1"
  local value="$2"
  local line
  line="$(dump_ui | tr '>' '\n' | grep -F "$attr=\"$value\"" | head -n 1 || true)"
  if [[ -z "$line" ]]; then
    printf 'Could not find UI node with %s="%s"\n' "$attr" "$value" >&2
    return 1
  fi
  local bounds x1 y1 x2 y2 x y
  bounds="$(sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p' <<<"$line")"
  if [[ -z "$bounds" ]]; then
    printf 'Could not parse node bounds for %s="%s"\n' "$attr" "$value" >&2
    return 1
  fi
  read -r x1 y1 x2 y2 <<<"$bounds"
  x=$(( (x1 + x2) / 2 ))
  y=$(( (y1 + y2) / 2 ))
  adb shell input tap "$x" "$y"
}

dismiss_system_dialogs() {
  local xml
  xml="$(dump_ui || true)"
  if grep -Fq "System UI isn't responding" <<<"$xml"; then
    tap_node "text" "Wait" || adb shell input keyevent KEYCODE_BACK
    sleep 2
  fi
}

run_with_timeout() {
  local seconds="$1"
  shift
  "$@" &
  local pid="$!"
  local elapsed=0
  while kill -0 "$pid" 2>/dev/null; do
    if (( elapsed >= seconds )); then
      kill "$pid" 2>/dev/null || true
      sleep 1
      kill -9 "$pid" 2>/dev/null || true
      wait "$pid" 2>/dev/null || true
      return 124
    fi
    sleep 1
    elapsed=$(( elapsed + 1 ))
  done
  wait "$pid"
}

capture() {
  local file="$OUT_DIR/$1"
  sleep 1
  adb exec-out screencap -p > "$file"
  if [[ ! -s "$file" ]]; then
    printf 'Screenshot capture failed: %s\n' "$file" >&2
    exit 7
  fi
  printf 'Captured %s\n' "$file"
}

adb shell pm clear com.snupai.trinkspiel >/dev/null
adb shell am start -n com.snupai.trinkspiel/.MainActivity >/dev/null
prepare_system_ui_demo
sleep 2
dismiss_system_dialogs

wait_for_text "Bereit machen"
capture "01-first-run-setup.png"

tap_node "content-desc" "18 plus bestätigen"
tap_node "content-desc" "Sicher spielen bestätigen"
swipe_up_until_text "Mit Classic Starter starten"
tap_node "text" "Mit Classic Starter starten"

wait_for_text "Karte ziehen"
capture "02-game-ready.png"

tap_node "text" "Karte ziehen"
wait_for_text "Erledigt, nächster Zug"
capture "03-card-drawn.png"

tap_node "content-desc" "Einträge"
wait_for_text "Einträge"
capture "04-entry-manager.png"

adb shell input keyevent KEYCODE_BACK
wait_for_text "Karte"
tap_node "content-desc" "Einstellungen"
wait_for_text "Einstellungen"
swipe_up_until_text "Diagnose" 8
adb shell input swipe 540 2000 540 1600 250
wait_for_text "Diagnose"
capture "05-settings-diagnostics.png"
adb shell input swipe 540 1900 540 520 350
swipe_up_until_text "Datenschutzrichtlinie anzeigen" 8
capture "06-settings-legal.png"

printf 'Store screenshots written to %s\n' "$OUT_DIR"
