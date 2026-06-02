#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
source "$ROOT_DIR/scripts/android-sdk-env.sh"
ensure_android_sdk_tools_on_path

RUN_BUILD=1
UPDATE_REPORT=0
GUIDE_ONLY=0
WRITE_FIXTURES=1
TESTER=""
OUT_DIR="build/manual-qa"
FIXTURES_OUT_DIR=""
EVIDENCE_OUT_DIR=""
REPORT="docs/MANUAL_QA_REPORT.md"
PACKAGE_NAME="com.snupai.trinkspiel"
MAIN_ACTIVITY="com.snupai.trinkspiel/.MainActivity"

usage() {
  printf '%s\n' "Usage: scripts/prepare-manual-qa.sh [--skip-build] [--update-report] [--guide-only] [--skip-fixtures] [--tester NAME] [--out DIR] [--fixtures-out DIR] [--evidence-out DIR]"
  printf '%s\n' "Installs and launches the current debug build on one attached device, then writes manual-QA device/app fields."
  printf '%s\n' "--update-report fills metadata, resets Result to Not completed, and resets checklist rows to TODO."
  printf '%s\n' "--guide-only writes a tester guide and evidence packet from the current manual-QA checklist without using adb."
  printf '%s\n' "--skip-fixtures avoids writing manual-QA JSON import fixtures."
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-build)
      RUN_BUILD=0
      shift
      ;;
    --update-report)
      UPDATE_REPORT=1
      shift
      ;;
    --guide-only)
      GUIDE_ONLY=1
      shift
      ;;
    --skip-fixtures)
      WRITE_FIXTURES=0
      shift
      ;;
    --tester)
      TESTER="${2:-}"
      if [[ -z "$TESTER" ]]; then
        usage >&2
        exit 64
      fi
      shift 2
      ;;
    --fixtures-out)
      FIXTURES_OUT_DIR="${2:-}"
      if [[ -z "$FIXTURES_OUT_DIR" ]]; then
        usage >&2
        exit 64
      fi
      shift 2
      ;;
    --evidence-out)
      EVIDENCE_OUT_DIR="${2:-}"
      if [[ -z "$EVIDENCE_OUT_DIR" ]]; then
        usage >&2
        exit 64
      fi
      shift 2
      ;;
    --out)
      OUT_DIR="${2:-}"
      if [[ -z "$OUT_DIR" ]]; then
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

FIXTURES_OUT_DIR="${FIXTURES_OUT_DIR:-$OUT_DIR/fixtures}"
EVIDENCE_OUT_DIR="${EVIDENCE_OUT_DIR:-$OUT_DIR/evidence}"

current_app_version() {
  local version_name version_code
  version_name="$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' app/build.gradle.kts | head -n 1)"
  version_code="$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' app/build.gradle.kts | head -n 1)"
  if [[ -n "$version_name" && -n "$version_code" ]]; then
    printf '%s (%s)' "$version_name" "$version_code"
  else
    printf 'unknown'
  fi
}

write_qa_guide() {
  local guide_device_model="$1"
  local guide_android_version="$2"
  local guide_app_version="$3"
  local guide_tester="$4"
  local guide_date="$5"
  local guide_device_serial="$6"
  local guide_file="$OUT_DIR/manual-qa-guide.md"

  mkdir -p "$OUT_DIR"
  {
    printf '# Manual QA Guide\n\n'
    printf 'Generated: %s\n\n' "$(date '+%Y-%m-%d %H:%M:%S %Z')"
    printf 'Use this guide while filling `%s`. It is a tester aid, not proof by itself; the release gate still reads `%s`.\n\n' "$REPORT" "$REPORT"
    printf '## Run Metadata\n\n'
    printf -- '- Device model: %s\n' "$guide_device_model"
    printf -- '- Android version: %s\n' "$guide_android_version"
    printf -- '- App version: %s\n' "$guide_app_version"
    printf -- '- Tester: %s\n' "$guide_tester"
    printf -- '- Date: %s\n' "$guide_date"
    printf -- '- Device serial used by adb: %s\n\n' "$guide_device_serial"
    printf '## Before You Start\n\n'
    printf -- '- Use a phone-sized Android device for the final pass.\n'
    printf -- '- Keep every checklist row in `%s` as `TODO` until you have checked it by hand.\n' "$REPORT"
	    printf -- '- Use `Failed` for anything that needs a fix; do not work around a failed row by marking it passed.\n'
	    printf -- '- Only set `Result: Passed` after every checklist row is `Passed`.\n'
	    printf -- '- Replace every `TODO evidence:` note with concrete device evidence before marking that row `Passed`.\n'
	    if [[ "$FIXTURES_OUT_DIR" == "manual-qa-fixtures" ]]; then
	      printf -- '- For import checks, use `manual-qa-fixtures/` from the generated package root, then open those JSON files through Android system pickers/share targets.\n'
	    else
	      printf -- '- For import checks, use `%s/`, or `manual-qa-fixtures/` from a generated package root, then open those JSON files through Android system pickers/share targets.\n' "$FIXTURES_OUT_DIR"
	    fi
	    printf -- '- After the report is complete, run `SEEMOPS_MANUAL_QA_CONFIRMED=1 scripts/check-manual-qa-report.sh --require-confirmation`.\n\n'
    printf '## High-Risk System UI Checks\n\n'
    printf -- '- Android chooser/share sheet: transfer package, privacy sharing, diagnostics, support request, and last-issue export.\n'
    printf -- '- Android file picker: card-pack export/import and full-backup export/import. Use `seemops_qa_card_pack.json` and `seemops_qa_backup.json` from the manual QA fixtures for import checks.\n'
    printf -- '- Transfer receive/import: use `seemops_qa_transfer_package.json` from the manual QA fixtures when checking open/share-to-Seemops behavior.\n'
    printf -- '- Last-issue state: first confirm the no-crash buttons are disabled, then run `scripts/seed-manual-qa-last-issue.sh` from the repo, or `bash tools/seed-manual-qa-last-issue.sh` from a generated package root, and verify share/export/delete.\n'
    printf -- '- Accessibility: large font scale, TalkBack labels, touch targets, and light/dark contrast.\n'
    printf -- '- Reset flows: round, score, player, and age/safety reset should leave the app in the exact expected state.\n\n'
    printf '## Checklist\n'
    awk '
      function trim(value) {
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
        return value
      }
      BEGIN {
        in_table = 0
        last_area = ""
      }
      /^\|[[:space:]]*Area[[:space:]]*\|[[:space:]]*Check[[:space:]]*\|[[:space:]]*Status[[:space:]]*\|[[:space:]]*Notes[[:space:]]*\|[[:space:]]*$/ {
        in_table = 1
        next
      }
      in_table && /^\|[[:space:]]*-+[[:space:]]*\|/ {
        next
      }
      in_table && $0 !~ /^\|/ {
        in_table = 0
      }
      in_table && /^\|/ {
        column_count = split($0, columns, /\|/)
        if (column_count >= 5) {
          area = trim(columns[2])
	          check = trim(columns[3])
	          status = trim(columns[4])
	          notes = trim(columns[5])
	          if (area != last_area) {
	            printf "\n### %s\n\n", area
	            last_area = area
	          }
	          printf "- [ ] %s", check
	          if (status != "") {
	            printf " (current report status: `%s`)", status
	          }
	          if (notes != "") {
	            printf " -- Notes: %s", notes
	          }
	          printf "\n"
	        }
	      }
    ' "$REPORT"
  } > "$guide_file"

  printf '%s' "$guide_file"
}

write_qa_fixtures() {
  if [[ "$WRITE_FIXTURES" != "1" ]]; then
    return
  fi

  scripts/generate-manual-qa-fixtures.sh --out "$FIXTURES_OUT_DIR" >/dev/null
}

write_qa_evidence_packet() {
  scripts/prepare-manual-qa-evidence-packet.sh \
    --report "$REPORT" \
    --out "$EVIDENCE_OUT_DIR" \
    --fixtures-dir "$FIXTURES_OUT_DIR" \
    >/dev/null
  printf '%s' "$EVIDENCE_OUT_DIR"
}

if [[ "$GUIDE_ONLY" == "1" ]]; then
  qa_guide_file="$(write_qa_guide \
    "TODO phone-sized Android device" \
    "TODO" \
    "$(current_app_version)" \
    "${TESTER:-TODO}" \
    "$(date '+%Y-%m-%d')" \
    "not collected; rerun without --guide-only on the QA device")"
  write_qa_fixtures
  qa_evidence_dir="$(write_qa_evidence_packet)"
  printf 'Manual QA guide written to %s\n' "$qa_guide_file"
  if [[ "$WRITE_FIXTURES" == "1" ]]; then
    printf 'Manual QA fixtures written to %s\n' "$FIXTURES_OUT_DIR"
  fi
  printf 'Manual QA evidence packet written to %s\n' "$qa_evidence_dir"
  printf 'Run without --guide-only on the QA device to install, clear app data, launch, and fill report metadata.\n'
  exit 0
fi

if ! command -v adb >/dev/null 2>&1; then
  printf 'adb is not available. ' >&2
  android_sdk_tools_hint >&2
  exit 3
fi

attached_devices="$(adb devices | awk 'NR > 1 && $2 == "device" { count++ } END { print count + 0 }')"
if [[ "$attached_devices" == "0" ]]; then
  printf 'No attached Android device/emulator found.\n' >&2
  printf 'Attach a phone-sized device or start an emulator, then rerun.\n' >&2
  exit 4
fi
if [[ "$attached_devices" != "1" && -z "${ANDROID_SERIAL:-}" ]]; then
  printf 'Multiple devices are attached. Set ANDROID_SERIAL, then rerun.\n' >&2
  exit 5
fi

if [[ "$RUN_BUILD" == "1" ]]; then
  ./gradlew :app:assembleDebug --no-daemon
fi

APK="app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -s "$APK" ]]; then
  printf 'Debug APK is missing: %s\n' "$APK" >&2
  exit 6
fi

clean_prop() {
  tr -d '\r' | sed 's/^[[:space:]]*//; s/[[:space:]]*$//'
}

device_serial="$(adb get-serialno | clean_prop)"
manufacturer="$(adb shell getprop ro.product.manufacturer | clean_prop)"
model="$(adb shell getprop ro.product.model | clean_prop)"
android_release="$(adb shell getprop ro.build.version.release | clean_prop)"
android_sdk="$(adb shell getprop ro.build.version.sdk | clean_prop)"
qa_date="$(date '+%Y-%m-%d')"
device_model="${manufacturer} ${model}"
android_version="Android ${android_release} (API ${android_sdk})"
app_version="$(current_app_version)"

adb install -r "$APK" >/dev/null
adb shell pm clear "$PACKAGE_NAME" >/dev/null
adb shell am start -n "$MAIN_ACTIVITY" >/dev/null

mkdir -p "$OUT_DIR"
qa_info_file="$OUT_DIR/manual-qa-device-info.md"
cat > "$qa_info_file" <<EOF
# Manual QA Device Info

Generated: $(date '+%Y-%m-%d %H:%M:%S %Z')

Copy these values into \`docs/MANUAL_QA_REPORT.md\` before doing the hands-on checklist:

- Device model: ${device_model}
- Android version: ${android_version}
- App version: ${app_version}
- Tester: ${TESTER:-TODO}
- Date: ${qa_date}
- Result: Not completed

Device serial used by adb: ${device_serial}

The app was freshly installed, app data was cleared, and \`${MAIN_ACTIVITY}\` was launched.
Do not set \`Result: Passed\` or checklist rows to \`Passed\` until the manual checks are actually complete.
EOF

write_qa_fixtures

update_field() {
  local field="$1"
  local value="$2"
  local escaped_value
  escaped_value="$(printf '%s' "$value" | sed 's/[&/\]/\\&/g')"
  sed -i.bak "s/^- ${field}:.*/- ${field}: ${escaped_value}/" "$REPORT"
}

reset_checklist_statuses() {
  local tmp_file="${REPORT}.tmp"
  awk '
    function trim(value) {
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
      return value
    }
    function evidence_note_for(check) {
      if (check == "Card-pack export creates a readable JSON file through the Android file picker.") {
        return "TODO evidence: record Android picker target and exported filename."
      }
      if (check == "Card-pack import shows the validation preview before applying a file.") {
        return "TODO evidence: use `seemops_qa_card_pack.json`; record preview counts before applying."
      }
      if (check == "Full backup export creates a readable JSON file through the Android file picker.") {
        return "TODO evidence: record Android picker target and exported filename."
      }
      if (check == "Full backup import restores cards and settings after preview confirmation.") {
        return "TODO evidence: use `seemops_qa_backup.json`; record restored cards/settings signal."
      }
      if (check == "Device-transfer package shares as a JSON attachment through Android'\''s chooser.") {
        return "TODO evidence: record chooser target and attachment filename/MIME."
      }
      if (check == "Device-transfer package can be received on another device or emulator and opened/shared directly to Seemops.") {
        return "TODO evidence: use `seemops_qa_transfer_package.json`; record receiving device and open/share path."
      }
      if (check == "Received device-transfer package imports through the backup preview flow and restores the expected cards/settings.") {
        return "TODO evidence: record preview counts and restored settings/player signal."
      }
      if (check == "Diagnostics report can be shared/exported and does not include card text or player names.") {
        return "TODO evidence: record share/export target and private-data spot check."
      }
      if (check == "Last-issue shortcut is disabled without a crash summary, then share/export/delete work when a stored issue exists.") {
        return "TODO evidence: record disabled state, seed helper run, and share/export/delete result."
      }
      if (check == "Support request can be prepared through Android'\''s share sheet and does not include card text, player names, or crash stack traces.") {
        return "TODO evidence: record share target and private-data spot check."
      }
      if (check == "Privacy policy can be shared from settings through Android'\''s share sheet.") {
        return "TODO evidence: record Android share target and policy title/snippet."
      }
      if (check == "Dark, light, and system themes render legibly.") {
        return "TODO evidence: record checked theme modes and any device display setting used."
      }
      if (check == "Large font scale does not clip primary setup/game actions, settings actions, player chips, or recap content.") {
        return "TODO evidence: record Android font/display size and checked screens."
      }
      if (check == "Icon-only controls and important actions have clear TalkBack labels.") {
        return "TODO evidence: record TalkBack labels sampled for top-bar and row actions."
      }
      if (check == "Touch targets remain comfortable on a phone-sized device.") {
        return "TODO evidence: record device size and checked compact controls."
      }
      if (check == "Light/dark contrast remains readable in setup, gameplay, entry manager, settings, and dialogs.") {
        return "TODO evidence: record screens checked in both light and dark mode."
      }
      return ""
    }
    BEGIN {
      in_table = 0
    }
    /^\|[[:space:]]*Area[[:space:]]*\|[[:space:]]*Check[[:space:]]*\|[[:space:]]*Status[[:space:]]*\|[[:space:]]*Notes[[:space:]]*\|[[:space:]]*$/ {
      in_table = 1
      print
      next
    }
    in_table && /^\|[[:space:]]*-+[[:space:]]*\|/ {
      print
      next
    }
    in_table && $0 !~ /^\|/ {
      in_table = 0
      print
      next
    }
    in_table && /^\|/ {
      column_count = split($0, columns, /\|/)
      if (column_count >= 5) {
        check = trim(columns[3])
        notes = evidence_note_for(check)
        if (notes != "") {
          printf "|%s|%s| TODO | %s |\n", columns[2], columns[3], notes
        } else {
          printf "|%s|%s| TODO | |\n", columns[2], columns[3]
        }
        next
      }
    }
    {
      print
    }
  ' "$REPORT" > "$tmp_file"
  mv "$tmp_file" "$REPORT"
}

if [[ "$UPDATE_REPORT" == "1" ]]; then
  update_field "Device model" "$device_model"
  update_field "Android version" "$android_version"
  update_field "App version" "$app_version"
  update_field "Date" "$qa_date"
  update_field "Result" "Not completed"
  if [[ -n "$TESTER" ]]; then
    update_field "Tester" "$TESTER"
  fi
  reset_checklist_statuses
  rm -f "${REPORT}.bak"
fi

qa_guide_file="$(write_qa_guide \
  "$device_model" \
  "$android_version" \
  "$app_version" \
  "${TESTER:-TODO}" \
  "$qa_date" \
  "$device_serial")"
qa_evidence_dir="$(write_qa_evidence_packet)"

printf 'Manual QA prep complete.\n'
printf 'Device model: %s\n' "$device_model"
printf 'Android version: %s\n' "$android_version"
printf 'App version: %s\n' "$app_version"
printf 'QA info written to %s\n' "$qa_info_file"
printf 'QA guide written to %s\n' "$qa_guide_file"
if [[ "$WRITE_FIXTURES" == "1" ]]; then
  printf 'QA fixtures written to %s\n' "$FIXTURES_OUT_DIR"
fi
printf 'QA evidence packet written to %s\n' "$qa_evidence_dir"
if [[ "$UPDATE_REPORT" == "1" ]]; then
  printf 'Updated metadata fields in %s and reset Result/checklist statuses for a fresh manual pass.\n' "$REPORT"
else
  printf 'Run with --update-report to fill metadata and reset %s for a fresh manual pass.\n' "$REPORT"
fi
