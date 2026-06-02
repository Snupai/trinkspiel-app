#!/usr/bin/env bash
set -euo pipefail
shopt -s nullglob

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
source "$ROOT_DIR/scripts/signing-utils.sh"

OUTPUT_FORMAT="markdown"
NO_FAIL=0
MIN_CONNECTED_ANDROID_TESTS=16
MIN_MANUAL_QA_ROWS=34
REQUIRED_CONNECTED_TESTS=(
  "com.snupai.trinkspiel.IncomingBackupIntentTest|sharedBackupJsonShowsImportPreview|incoming transfer-package share preview"
  "com.snupai.trinkspiel.IncomingBackupIntentTest|sharedBackupJsonCanBeConfirmedAndImported|incoming transfer-package import confirmation"
  "com.snupai.trinkspiel.util.TransferPackageShareTest|createFileExposesReadableJsonAttachment|transfer-package JSON attachment URI"
  "com.snupai.trinkspiel.TrinkspielAppSmokeTest|gameLoadsStarterPackAndDrawsCard|starter pack draw and session recap"
  "com.snupai.trinkspiel.TrinkspielAppSmokeTest|entryManagerCreatesPackTemplateDrafts|pack template draft creation"
  "com.snupai.trinkspiel.TrinkspielAppSmokeTest|gameplaySkipCompleteAndUndoWork|skip/complete/undo gameplay"
  "com.snupai.trinkspiel.TrinkspielAppSmokeTest|settingsScreenShowsCoreReleaseOptions|settings release options"
  "com.snupai.trinkspiel.TrinkspielAppSmokeTest|settingsThemeAndPrivacyPolicyControlsWork|theme/privacy controls"
  "com.snupai.trinkspiel.TrinkspielAppSmokeTest|entryManagerAddsPausesEditsAndDeletesCustomEntry|entry CRUD controls"
  "com.snupai.trinkspiel.TrinkspielAppSmokeTest|playerPanelAddsPlayersAndUpdatesScoreboard|player/scoreboard controls"
  "com.snupai.trinkspiel.TrinkspielAppSmokeTest|settingsLastIssueActionsReflectStoredCrashState|last-issue controls"
  "com.snupai.trinkspiel.TrinkspielAppSmokeTest|settingsResetActionsClearPlayersAndReturnToFirstRunGate|settings reset controls"
  "com.snupai.trinkspiel.TrinkspielAppSmokeTest|settingsResetActionsResetRoundAndScores|round/score reset controls"
)

usage() {
  printf '%s\n' "Usage: scripts/release-blockers.sh [--json] [--no-fail]"
  printf '%s\n' "Prints a concise release-blocker report from current local evidence."
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --json)
      OUTPUT_FORMAT="json"
      shift
      ;;
    --no-fail)
      NO_FAIL=1
      shift
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

json_string() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\n'/\\n}"
  printf '"%s"' "$value"
}

file_mtime() {
  local file="$1"
  if stat -f '%m' "$file" >/dev/null 2>&1; then
    stat -f '%m' "$file"
  else
    stat -c '%Y' "$file"
  fi
}

latest_source_mtime_for() {
  local latest=0
  local file mtime
  while IFS= read -r -d '' file; do
    mtime="$(file_mtime "$file")"
    if (( mtime > latest )); then
      latest="$mtime"
    fi
  done < <(find "$@" -type f \( -name '*.kt' -o -name '*.kts' -o -name '*.xml' \) -print0)
  printf '%s' "$latest"
}

latest_build_input_mtime_for() {
  local latest=0
  local path file mtime

  for path in "$@"; do
    if [[ ! -e "$path" ]]; then
      continue
    fi
    while IFS= read -r -d '' file; do
      mtime="$(file_mtime "$file")"
      if (( mtime > latest )); then
        latest="$mtime"
      fi
    done < <(find "$path" -type f -print0)
  done

  printf '%s' "$latest"
}

latest_result_mtime_for() {
  local latest=0
  local file mtime
  for file in "$@"; do
    if [[ ! -f "$file" ]]; then
      continue
    fi
    mtime="$(file_mtime "$file")"
    if (( mtime > latest )); then
      latest="$mtime"
    fi
  done
  printf '%s' "$latest"
}

read_junit_attr() {
  local file="$1"
  local attr="$2"
  sed -n "s/.* ${attr}=\"\\([0-9]*\\)\".*/\\1/p" "$file" | head -n 1
}

testcase_exists() {
  local class_name="$1"
  local test_name="$2"
  shift 2
  local file
  for file in "$@"; do
    if grep -F "classname=\"$class_name\"" "$file" | grep -Fq "name=\"$test_name\""; then
      return 0
    fi
  done
  return 1
}

latest_ui_source_mtime() {
  local latest=0
  local file mtime
  while IFS= read -r -d '' file; do
    mtime="$(file_mtime "$file")"
    if (( mtime > latest )); then
      latest="$mtime"
    fi
  done < <(find \
    app/src/main/java/com/snupai/trinkspiel/ui \
    app/src/main/java/com/snupai/trinkspiel/TrinkspielApp.kt \
    -type f -name '*.kt' -print0)
  printf '%s' "$latest"
}

image_dimensions_match() {
  local file="$1"
  local expected_width="$2"
  local expected_height="$3"
  local actual_width actual_height

  if [[ ! -s "$file" ]] || ! command -v sips >/dev/null 2>&1; then
    return 1
  fi
  actual_width="$(sips -g pixelWidth "$file" 2>/dev/null | awk '/pixelWidth/ { print $2 }')"
  actual_height="$(sips -g pixelHeight "$file" 2>/dev/null | awk '/pixelHeight/ { print $2 }')"
  [[ "$actual_width" == "$expected_width" && "$actual_height" == "$expected_height" ]]
}

manual_qa_field_value() {
  local field="$1"
  sed -n "s/^- ${field}:[[:space:]]*//p" docs/MANUAL_QA_REPORT.md | head -n 1
}

current_app_version() {
  local version_name version_code
  version_name="$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' app/build.gradle.kts | head -n 1)"
  version_code="$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' app/build.gradle.kts | head -n 1)"
  if [[ -n "$version_name" && -n "$version_code" ]]; then
    printf '%s (%s)' "$version_name" "$version_code"
  fi
}

manual_qa_checklist_stats() {
  awk '
    function trim(value) {
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
      return value
    }
    BEGIN {
      in_table = 0
      rows = 0
      passed = 0
      todo = 0
      failed = 0
      invalid = 0
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
      status = column_count >= 5 ? trim(columns[4]) : ""
      rows++
      if (status == "Passed") {
        passed++
      } else if (status == "TODO") {
        todo++
      } else if (status == "Failed") {
        failed++
      } else {
        invalid++
      }
    }
    END {
      printf "%d %d %d %d %d\n", rows, passed, todo, failed, invalid
    }
  ' docs/MANUAL_QA_REPORT.md
}

set +e
scripts/check-release-signing-config.sh >/dev/null 2>&1
signing_config_exit="$?"
scripts/check-privacy-policy-url.sh >/dev/null 2>&1
privacy_exit="$?"
scripts/check-play-console-handoff.sh >/dev/null 2>&1
handoff_exit="$?"
scripts/check-manual-qa-report.sh --require-confirmation >/dev/null 2>&1
manual_qa_check_exit="$?"
set -e

artifact_missing=()
artifact_stale=()
release_apks=(app/build/outputs/apk/release/*.apk)
main_build_input_mtime="$(latest_build_input_mtime_for app/src/main app/build.gradle.kts app/proguard-rules.pro)"
android_test_build_input_mtime="$(latest_build_input_mtime_for app/src/main app/src/androidTest app/build.gradle.kts)"

check_artifact_group_current() {
  local label="$1"
  local latest_input_mtime="$2"
  shift 2
  local files=("$@")
  local file

  if (( ${#files[@]} == 0 )); then
    artifact_missing+=("$label")
    return
  fi
  if (( latest_input_mtime == 0 )); then
    artifact_stale+=("$label")
    return
  fi
  for file in "${files[@]}"; do
    if [[ ! -f "$file" ]]; then
      artifact_missing+=("$file")
    elif (( $(file_mtime "$file") < latest_input_mtime )); then
      artifact_stale+=("$file")
    fi
  done
}

check_artifact_group_current "debug APK" "$main_build_input_mtime" "app/build/outputs/apk/debug/app-debug.apk"
check_artifact_group_current "release APK" "$main_build_input_mtime" "${release_apks[@]}"
check_artifact_group_current "release AAB" "$main_build_input_mtime" "app/build/outputs/bundle/release/app-release.aab"
check_artifact_group_current "Android test APK" "$android_test_build_input_mtime" "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

artifacts_state="ready"
artifacts_summary="APK/AAB artifacts are present and current with app build inputs."
if (( ${#artifact_missing[@]} > 0 || ${#artifact_stale[@]} > 0 )); then
  artifacts_state="blocked"
  artifacts_summary="APK/AAB artifacts are missing or older than app build inputs."
fi
artifacts_action="Run ./gradlew :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:bundleRelease --no-daemon, then rerun scripts/verify-release-ready.sh --skip-build."

aab_status="missing"
AAB="app/build/outputs/bundle/release/app-release.aab"
aab_status="$(release_aab_signing_status "$AAB")"

signing_state="ready"
signing_summary="Release signing inputs are complete and the release AAB is signed."
if [[ "$signing_config_exit" != "0" || "$aab_status" != "signed" ]]; then
  signing_state="blocked"
  if [[ "$aab_status" == "debug-signed" ]]; then
    signing_summary="Release AAB appears signed with Android debug signing material."
  elif [[ "$signing_config_exit" != "0" && "$aab_status" == "unsigned" ]]; then
    signing_summary="Release signing inputs are incomplete and the release AAB is unsigned."
  elif [[ "$signing_config_exit" != "0" ]]; then
    signing_summary="Visible release signing inputs are incomplete."
  else
    signing_summary="Release signing inputs look complete, but the release AAB is not signed."
  fi
fi
signing_action="Run scripts/prepare-release-signing-handoff.sh, configure real SEEMOPS_RELEASE_* values on a trusted signing machine, run scripts/check-release-signing-config.sh, rebuild :app:bundleRelease, then rerun scripts/verify-release-ready.sh --skip-build."

privacy_state="ready"
privacy_summary="Hosted privacy-policy URL is public HTTPS, reachable, contains expected Seemops privacy-policy commitments, and exactly matches docs/privacy-policy.html."
if [[ "$privacy_exit" != "0" ]]; then
  privacy_state="blocked"
  if [[ -z "${SEEMOPS_PRIVACY_POLICY_URL:-}" ]]; then
    privacy_summary="Local privacy-policy file is valid, but SEEMOPS_PRIVACY_POLICY_URL is not set."
  else
    privacy_summary="Hosted privacy-policy URL is not passing validation."
  fi
fi
privacy_action="Run scripts/prepare-privacy-policy-hosting.sh, host the generated privacy-policy HTML at the final public HTTPS URL, export SEEMOPS_PRIVACY_POLICY_URL, then run scripts/check-privacy-policy-url.sh."

handoff_state="ready"
handoff_summary="Play Console handoff matches current app identity, store assets, data-safety claims, privacy text, dependencies, permissions, and backup settings."
if [[ "$handoff_exit" != "0" ]]; then
  handoff_state="blocked"
  handoff_summary="Play Console submission handoff is missing required evidence or is out of sync with the current app/store state."
fi
handoff_action="Run scripts/check-play-console-handoff.sh, update docs/PLAY_CONSOLE_SUBMISSION.md or the referenced store/privacy docs, then rerun scripts/verify-release-ready.sh --skip-build."

manual_fields_ready=1
manual_missing_fields=()
for field in "Device model" "Android version" "App version" "Tester" "Date"; do
  value="$(manual_qa_field_value "$field")"
  if [[ -z "$value" || "$value" == "TODO" ]]; then
    manual_fields_ready=0
    manual_missing_fields+=("$field")
  fi
done
manual_result="$(manual_qa_field_value "Result")"
manual_app_version="$(manual_qa_field_value "App version")"
manual_expected_app_version="$(current_app_version)"
manual_date="$(manual_qa_field_value "Date")"
manual_app_version_ready=1
manual_date_ready=1
manual_extra_summary=""
if [[ -n "$manual_app_version" &&
  "$manual_app_version" != "TODO" &&
  -n "$manual_expected_app_version" &&
  "$manual_app_version" != "$manual_expected_app_version" ]]; then
  manual_app_version_ready=0
  manual_extra_summary="${manual_extra_summary}; app version: ${manual_app_version}, expected ${manual_expected_app_version}"
fi
if [[ -n "$manual_date" && "$manual_date" != "TODO" && ! "$manual_date" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
  manual_date_ready=0
  manual_extra_summary="${manual_extra_summary}; date must use YYYY-MM-DD"
fi
read -r manual_total_rows manual_passed_rows manual_todo_rows manual_failed_rows manual_invalid_rows < <(manual_qa_checklist_stats)
manual_rows_ready=1
if (( manual_total_rows < MIN_MANUAL_QA_ROWS ||
  manual_passed_rows != manual_total_rows ||
  manual_todo_rows > 0 ||
  manual_failed_rows > 0 ||
  manual_invalid_rows > 0 )); then
  manual_rows_ready=0
fi

manual_state="ready"
manual_summary="Manual QA report is filled, passed, confirmed, and includes concrete high-risk evidence notes."
if [[ "$manual_qa_check_exit" != "0" ||
  "${SEEMOPS_MANUAL_QA_CONFIRMED:-}" != "1" ||
  "$manual_fields_ready" != "1" ||
  "$manual_app_version_ready" != "1" ||
  "$manual_date_ready" != "1" ||
  "$manual_result" != "Passed" ||
  "$manual_rows_ready" != "1" ]]; then
  manual_state="blocked"
  manual_summary="Manual QA is not confirmed. Missing fields: ${manual_missing_fields[*]:-none}; result: ${manual_result:-missing}; passed rows: ${manual_passed_rows}/${manual_total_rows}; minimum rows: ${MIN_MANUAL_QA_ROWS}; TODO: ${manual_todo_rows}; Failed: ${manual_failed_rows}; invalid: ${manual_invalid_rows}${manual_extra_summary}."
fi
manual_action="Run scripts/prepare-manual-qa.sh --update-report --tester \"Your Name\", complete every checklist row by hand, replace high-risk TODO evidence notes with concrete device evidence, set Result: Passed, then export SEEMOPS_MANUAL_QA_CONFIRMED=1."

connected_tests_state="ready"
connected_tests_summary="Connected Android UI/import test evidence is current and clean."
connected_tests_action="Attach a device or start an emulator, then run scripts/run-connected-ui-tests.sh."
ANDROID_TEST_FILES=(app/build/outputs/androidTest-results/connected/debug/TEST-*.xml)
if (( ${#ANDROID_TEST_FILES[@]} == 0 )); then
  connected_tests_state="blocked"
  connected_tests_summary="Connected Android test result XML files are missing."
else
  connected_total=0
  connected_failures=0
  connected_errors=0
  connected_skipped=0
  for file in "${ANDROID_TEST_FILES[@]}"; do
    tests_value="$(read_junit_attr "$file" "tests")"
    failures_value="$(read_junit_attr "$file" "failures")"
    errors_value="$(read_junit_attr "$file" "errors")"
    skipped_value="$(read_junit_attr "$file" "skipped")"
    connected_total=$(( connected_total + ${tests_value:-0} ))
    connected_failures=$(( connected_failures + ${failures_value:-0} ))
    connected_errors=$(( connected_errors + ${errors_value:-0} ))
    connected_skipped=$(( connected_skipped + ${skipped_value:-0} ))
  done

  connected_test_source_mtime="$(latest_source_mtime_for app/src/main/java app/src/androidTest/java app/build.gradle.kts)"
  connected_test_result_mtime="$(latest_result_mtime_for "${ANDROID_TEST_FILES[@]}")"
  missing_required_tests=()
  for required_test in "${REQUIRED_CONNECTED_TESTS[@]}"; do
    IFS='|' read -r class_name test_name label <<<"$required_test"
    if ! testcase_exists "$class_name" "$test_name" "${ANDROID_TEST_FILES[@]}"; then
      missing_required_tests+=("$label")
    fi
  done

  if (( connected_total < MIN_CONNECTED_ANDROID_TESTS || connected_failures > 0 || connected_errors > 0 )); then
    connected_tests_state="blocked"
    connected_tests_summary="Connected Android test results are not clean: ${connected_total} tests, ${connected_failures} failures, ${connected_errors} errors, ${connected_skipped} skipped."
  elif (( ${#missing_required_tests[@]} > 0 )); then
    connected_tests_state="blocked"
    connected_tests_summary="Connected Android test results are missing required coverage: ${missing_required_tests[*]}."
  elif (( connected_test_source_mtime == 0 || connected_test_result_mtime < connected_test_source_mtime )); then
    connected_tests_state="blocked"
    connected_tests_summary="Connected Android test results are older than current app/androidTest source files."
  fi
fi

screenshots_state="ready"
screenshots_summary="Store screenshots are present, 1080x2400, and current with UI source files."
screenshots_action="Run scripts/capture-store-screenshots.sh with a phone-sized device or emulator attached."
SCREENSHOT_DIR="docs/store-assets/screenshots/phone"
SCREENSHOT_FILES=(
  "$SCREENSHOT_DIR/01-first-run-setup.png"
  "$SCREENSHOT_DIR/02-game-ready.png"
  "$SCREENSHOT_DIR/03-card-drawn.png"
  "$SCREENSHOT_DIR/04-entry-manager.png"
  "$SCREENSHOT_DIR/05-settings-diagnostics.png"
  "$SCREENSHOT_DIR/06-settings-legal.png"
)
latest_source_mtime="$(latest_ui_source_mtime)"
for screenshot in "${SCREENSHOT_FILES[@]}"; do
  if ! image_dimensions_match "$screenshot" 1080 2400; then
    screenshots_state="blocked"
    screenshots_summary="One or more store screenshots are missing or not 1080x2400."
    break
  fi
  if (( $(file_mtime "$screenshot") < latest_source_mtime )); then
    screenshots_state="blocked"
    screenshots_summary="One or more store screenshots are older than current UI source files."
    break
  fi
done
if [[ "$screenshots_state" == "ready" ]]; then
  if scripts/check-store-screenshot-polish.sh "${SCREENSHOT_FILES[@]}" >/dev/null 2>&1; then
    screenshots_summary="Store screenshots are present, 1080x2400, current with UI source files, and have clean demo-mode status bars."
  else
    screenshots_state="blocked"
    screenshots_summary="One or more store screenshots have a noisy or malformed status bar."
    screenshots_action="Run scripts/capture-store-screenshots.sh to recapture screenshots with Android System UI demo mode."
  fi
fi

blocker_count=0
for state in "$artifacts_state" "$signing_state" "$privacy_state" "$handoff_state" "$manual_state" "$connected_tests_state" "$screenshots_state"; do
  if [[ "$state" != "ready" ]]; then
    blocker_count=$(( blocker_count + 1 ))
  fi
done

if [[ "$OUTPUT_FORMAT" == "json" ]]; then
  generated_at="$(date '+%Y-%m-%d %H:%M:%S %Z')"
  printf '{\n'
  printf '  "generated_at": %s,\n' "$(json_string "$generated_at")"
  printf '  "blocker_count": %s,\n' "$blocker_count"
  printf '  "items": [\n'
	  items=(
	    "release_artifacts|$artifacts_state|$artifacts_summary|$artifacts_action"
	    "release_signing|$signing_state|$signing_summary|$signing_action"
	    "privacy_policy_url|$privacy_state|$privacy_summary|$privacy_action"
	    "play_console_handoff|$handoff_state|$handoff_summary|$handoff_action"
	    "manual_qa|$manual_state|$manual_summary|$manual_action"
	    "connected_android_tests|$connected_tests_state|$connected_tests_summary|$connected_tests_action"
	    "store_screenshots|$screenshots_state|$screenshots_summary|$screenshots_action"
	  )
  for i in "${!items[@]}"; do
    IFS='|' read -r id state summary action <<<"${items[$i]}"
    printf '    {"id": %s, "status": %s, "summary": %s, "next_action": %s}' \
      "$(json_string "$id")" \
      "$(json_string "$state")" \
      "$(json_string "$summary")" \
      "$(json_string "$action")"
    if (( i < ${#items[@]} - 1 )); then
      printf ','
    fi
    printf '\n'
  done
  printf '  ]\n'
  printf '}\n'
else
  printf 'Release blockers\n'
  printf 'Generated: %s\n\n' "$(date '+%Y-%m-%d %H:%M:%S %Z')"
  printf 'Blockers remaining: %s\n\n' "$blocker_count"
  printf '%s\n  %s\n  Next: %s\n' "- release_artifacts: $artifacts_state" "$artifacts_summary" "$artifacts_action"
  printf '%s\n  %s\n  Next: %s\n' "- release_signing: $signing_state" "$signing_summary" "$signing_action"
	  printf '%s\n  %s\n  Next: %s\n' "- privacy_policy_url: $privacy_state" "$privacy_summary" "$privacy_action"
	  printf '%s\n  %s\n  Next: %s\n' "- play_console_handoff: $handoff_state" "$handoff_summary" "$handoff_action"
	  printf '%s\n  %s\n  Next: %s\n' "- manual_qa: $manual_state" "$manual_summary" "$manual_action"
	  printf '%s\n  %s\n  Next: %s\n' "- connected_android_tests: $connected_tests_state" "$connected_tests_summary" "$connected_tests_action"
	  printf '%s\n  %s\n  Next: %s\n' "- store_screenshots: $screenshots_state" "$screenshots_summary" "$screenshots_action"
	fi

if (( blocker_count > 0 && NO_FAIL == 0 )); then
  exit 1
fi
