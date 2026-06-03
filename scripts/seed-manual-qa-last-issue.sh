#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
source "$ROOT_DIR/scripts/android-sdk-env.sh"
ensure_android_sdk_tools_on_path

PACKAGE_NAME="com.snupai.trinkspiel"
MAIN_ACTIVITY="com.snupai.trinkspiel/.MainActivity"
CRASH_CLASS="com.snupai.trinkspiel.ManualQaStoredIssue"
CRASH_MESSAGE="Manual QA stored issue"

usage() {
  printf '%s\n' "Usage: scripts/seed-manual-qa-last-issue.sh [--message TEXT]"
  printf '%s\n' "Seeds a fake local crash summary into the installed debug QA app so last-issue share/export/delete can be checked by hand."
  printf '%s\n' "Run after scripts/prepare-manual-qa.sh and after confirming the no-crash disabled state."
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --message)
      CRASH_MESSAGE="${2:-}"
      if [[ -z "$CRASH_MESSAGE" ]]; then
        usage >&2
        exit 64
      fi
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      usage >&2
      exit 64
      ;;
  esac
done

if ! command -v adb >/dev/null 2>&1; then
  printf 'adb is not available. ' >&2
  android_sdk_tools_hint >&2
  exit 3
fi

attached_devices="$(adb devices | awk 'NR > 1 && $2 == "device" { count++ } END { print count + 0 }')"
if [[ "$attached_devices" == "0" ]]; then
  printf 'No attached Android device/emulator found.\n' >&2
  printf 'Run scripts/prepare-manual-qa.sh on a phone-sized QA device first, then rerun.\n' >&2
  exit 4
fi
if [[ "$attached_devices" != "1" && -z "${ANDROID_SERIAL:-}" ]]; then
  printf 'Multiple devices are attached. Set ANDROID_SERIAL, then rerun.\n' >&2
  exit 5
fi

if ! adb shell pm path "$PACKAGE_NAME" >/dev/null 2>&1; then
  printf 'App is not installed for package %s.\n' "$PACKAGE_NAME" >&2
  printf 'Run scripts/prepare-manual-qa.sh on the QA device first.\n' >&2
  exit 6
fi

xml_escape() {
  sed \
    -e 's/&/\&amp;/g' \
    -e 's/</\&lt;/g' \
    -e 's/>/\&gt;/g' \
    -e 's/"/\&quot;/g' \
    -e "s/'/\&apos;/g"
}

timestamp="$(date -u '+%Y-%m-%dT%H:%M:%S.000Z')"
escaped_timestamp="$(printf '%s' "$timestamp" | xml_escape)"
escaped_class="$(printf '%s' "$CRASH_CLASS" | xml_escape)"
escaped_message="$(printf '%s' "$CRASH_MESSAGE" | xml_escape)"
escaped_stack="$(printf '%s\n' \
  "$CRASH_CLASS: $CRASH_MESSAGE" \
  "    at com.snupai.trinkspiel.ManualQa.seed(ManualQa.kt:1)" \
  "    at scripts.seed-manual-qa-last-issue(debug-helper)" | xml_escape)"

tmp_xml="$(mktemp)"
cat > "$tmp_xml" <<EOF
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="crashTimestamp">${escaped_timestamp}</string>
    <string name="crashClass">${escaped_class}</string>
    <string name="crashMessage">${escaped_message}</string>
    <string name="crashStack">${escaped_stack}</string>
</map>
EOF

adb shell run-as "$PACKAGE_NAME" sh -c \
  "mkdir -p shared_prefs && cat > shared_prefs/seemops_diagnostics.xml && chmod 600 shared_prefs/seemops_diagnostics.xml" < "$tmp_xml"
rm -f "$tmp_xml"

if ! adb shell run-as "$PACKAGE_NAME" cat shared_prefs/seemops_diagnostics.xml | grep -Fq "$CRASH_CLASS"; then
  printf 'Could not verify seeded last-issue state in app shared preferences.\n' >&2
  exit 7
fi

adb shell am force-stop "$PACKAGE_NAME" >/dev/null
adb shell am start -n "$MAIN_ACTIVITY" >/dev/null

printf 'Seeded a manual-QA last-issue summary at %s.\n' "$timestamp"
printf 'Open Einstellungen > Diagnose and verify Letzten Fehler teilen/exportieren/löschen.\n'
