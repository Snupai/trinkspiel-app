#!/usr/bin/env bash
set -euo pipefail
shopt -s nullglob

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
source "$ROOT_DIR/scripts/privacy-policy-utils.sh"
source "$ROOT_DIR/scripts/signing-utils.sh"

MIN_UNIT_TESTS=34
MIN_BUILT_IN_PACK_TESTS=5
MIN_CONNECTED_ANDROID_TESTS=16
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

RUN_BUILD=1
if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  printf '%s\n' "Usage: scripts/verify-release-ready.sh [--skip-build]"
  printf '%s\n' "Runs the release gate, validates store assets/docs, and verifies that the release AAB is signed."
  printf '%s\n' "Set SEEMOPS_PRIVACY_POLICY_URL to the hosted privacy-policy URL before the final release check."
  printf '%s\n' "Set SEEMOPS_MANUAL_QA_CONFIRMED=1 after completing docs/MANUAL_QA_REPORT.md."
  exit 0
elif [[ "${1:-}" == "--skip-build" ]]; then
  RUN_BUILD=0
elif [[ $# -gt 0 ]]; then
  printf '%s\n' "Usage: scripts/verify-release-ready.sh [--skip-build]" >&2
  exit 64
fi

if [[ "$RUN_BUILD" == "1" ]]; then
  ./gradlew \
    :app:testDebugUnitTest \
    :app:lintDebug \
    :app:assembleDebug \
    :app:assembleRelease \
    :app:assembleDebugAndroidTest \
    :app:bundleRelease \
    --no-daemon
fi

failures=0
signing_failed=0

section() {
  printf '\n%s\n' "$1"
}

ok() {
  printf '  OK      %s\n' "$1"
}

fail() {
  printf '  FAIL    %s\n' "$1"
  failures=$(( failures + 1 ))
}

require_file() {
  local file="$1"
  if [[ -s "$file" ]]; then
    ok "$file"
  else
    fail "Missing required file: $file"
  fi
}

require_any_file() {
  local label="$1"
  shift
  local files=("$@")
  if (( ${#files[@]} > 0 )); then
    ok "$label (${#files[@]} file(s))"
  else
    fail "Missing required file group: $label"
  fi
}

require_dimensions() {
  local file="$1"
  local expected_width="$2"
  local expected_height="$3"

  if [[ ! -s "$file" ]]; then
    fail "Missing image: $file"
    return
  fi
  if ! command -v sips >/dev/null 2>&1; then
    fail "Cannot verify image dimensions without sips: $file"
    return
  fi

  local actual_width actual_height
  if ! actual_width="$(sips -g pixelWidth "$file" 2>/dev/null | awk '/pixelWidth/ { print $2 }')"; then
    fail "Could not read image width: $file"
    return
  fi
  if ! actual_height="$(sips -g pixelHeight "$file" 2>/dev/null | awk '/pixelHeight/ { print $2 }')"; then
    fail "Could not read image height: $file"
    return
  fi

  if [[ "$actual_width" == "$expected_width" && "$actual_height" == "$expected_height" ]]; then
    ok "$file (${actual_width}x${actual_height})"
  else
    fail "$file has ${actual_width}x${actual_height}; expected ${expected_width}x${expected_height}"
  fi
}

file_mtime() {
  local file="$1"
  if stat -f '%m' "$file" >/dev/null 2>&1; then
    stat -f '%m' "$file"
  else
    stat -c '%Y' "$file"
  fi
}

latest_ui_source_mtime() {
  local latest=0
  local mtime
  local source_files=()
  while IFS= read -r -d '' file; do
    source_files+=("$file")
  done < <(find \
    app/src/main/java/com/snupai/trinkspiel/ui \
    app/src/main/java/com/snupai/trinkspiel/TrinkspielApp.kt \
    -type f -name '*.kt' -print0)

  for file in "${source_files[@]}"; do
    mtime="$(file_mtime "$file")"
    if (( mtime > latest )); then
      latest="$mtime"
    fi
  done
  printf '%s' "$latest"
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

require_artifacts_current() {
  local label="$1"
  local latest_input_mtime="$2"
  shift 2
  local files=("$@")
  local file artifact_mtime
  local stale_artifacts=()

  if (( ${#files[@]} == 0 )); then
    return
  fi
  if (( latest_input_mtime == 0 )); then
    fail "Could not determine build input timestamp for $label"
    return
  fi

  for file in "${files[@]}"; do
    if [[ ! -f "$file" ]]; then
      continue
    fi
    artifact_mtime="$(file_mtime "$file")"
    if (( artifact_mtime < latest_input_mtime )); then
      stale_artifacts+=("$file")
    fi
  done

  if (( ${#stale_artifacts[@]} > 0 )); then
    fail "$label are older than current app build inputs. Rebuild before packaging: ${stale_artifacts[*]}"
  else
    ok "$label are current with app build inputs"
  fi
}

require_results_current() {
  local label="$1"
  local latest_source_mtime="$2"
  shift 2
  local files=("$@")
  local latest_result_mtime=0
  local file result_mtime

  if (( ${#files[@]} == 0 )); then
    return
  fi
  if (( latest_source_mtime == 0 )); then
    fail "Could not determine source timestamp for $label"
    return
  fi

  for file in "${files[@]}"; do
    if [[ ! -f "$file" ]]; then
      continue
    fi
    result_mtime="$(file_mtime "$file")"
    if (( result_mtime > latest_result_mtime )); then
      latest_result_mtime="$result_mtime"
    fi
  done

  if (( latest_result_mtime >= latest_source_mtime )); then
    ok "$label are current with source files"
  else
    fail "$label are older than current source files. Rerun the matching tests."
  fi
}

require_screenshots_current() {
  local screenshots=("$@")
  local latest_source_mtime
  local screenshot
  local screenshot_mtime
  local stale_screenshots=()

  latest_source_mtime="$(latest_ui_source_mtime)"
  if (( latest_source_mtime == 0 )); then
    fail "Could not determine latest UI source timestamp for screenshot freshness"
    return
  fi

  for screenshot in "${screenshots[@]}"; do
    if [[ ! -f "$screenshot" ]]; then
      continue
    fi
    screenshot_mtime="$(file_mtime "$screenshot")"
    if (( screenshot_mtime < latest_source_mtime )); then
      stale_screenshots+=("$screenshot")
    fi
  done

  if (( ${#stale_screenshots[@]} > 0 )); then
    fail "Store screenshots are older than current UI source files. Recapture with scripts/capture-store-screenshots.sh: ${stale_screenshots[*]}"
  else
    ok "Store screenshots are current with UI source files"
  fi
}

read_junit_attr() {
  local file="$1"
  local attr="$2"
  sed -n "s/.* ${attr}=\"\\([0-9]*\\)\".*/\\1/p" "$file" | head -n 1
}

check_junit_results() {
  local label="$1"
  local min_tests="$2"
  shift 2
  local files=("$@")

  if (( ${#files[@]} == 0 )); then
    fail "$label result XML is missing"
    return
  fi

  local total=0
  local failed_count=0
  local error_count=0
  local skipped_count=0
  local file tests_value failures_value errors_value skipped_value
  for file in "${files[@]}"; do
    if [[ ! -f "$file" ]]; then
      fail "$label result XML is missing: $file"
      return
    fi
    tests_value="$(read_junit_attr "$file" "tests")"
    failures_value="$(read_junit_attr "$file" "failures")"
    errors_value="$(read_junit_attr "$file" "errors")"
    skipped_value="$(read_junit_attr "$file" "skipped")"
    total=$(( total + ${tests_value:-0} ))
    failed_count=$(( failed_count + ${failures_value:-0} ))
    error_count=$(( error_count + ${errors_value:-0} ))
    skipped_count=$(( skipped_count + ${skipped_value:-0} ))
  done

  if (( total >= min_tests && failed_count == 0 && error_count == 0 )); then
    ok "$label (${total} tests, ${skipped_count} skipped)"
  else
    fail "$label has ${total} tests, ${failed_count} failures, ${error_count} errors, ${skipped_count} skipped; expected at least ${min_tests} clean tests"
  fi
}

check_required_testcase() {
  local class_name="$1"
  local test_name="$2"
  local label="$3"
  shift 3
  local files=("$@")
  local file

  for file in "${files[@]}"; do
    if grep -F "classname=\"$class_name\"" "$file" | grep -Fq "name=\"$test_name\""; then
      ok "$label"
      return
    fi
  done

  fail "Missing required connected test: ${class_name}.${test_name} (${label})"
}

check_hosted_privacy_policy() {
  local url="$1"
  local hosted_html
  local hosted_html_file
  local effective_url
  local effective_url_problem
  local hosted_privacy_sha
  local local_privacy_sha

  if ! command -v curl >/dev/null 2>&1; then
    fail "Cannot verify hosted privacy-policy URL without curl"
    return
  fi

  hosted_html_file="$(mktemp)"
  if ! effective_url="$(fetch_hosted_privacy_policy "$url" "$hosted_html_file")"; then
    rm -f "$hosted_html_file"
    fail "Hosted privacy-policy URL is not reachable: $url"
    return
  fi

  ok "Hosted privacy-policy URL is reachable"
  if [[ "$effective_url" != "$url" ]]; then
    if effective_url_problem="$(privacy_url_issue "$effective_url")"; then
      fail "Hosted privacy-policy URL redirects to a URL that $effective_url_problem: $effective_url"
    else
      ok "Hosted privacy-policy final URL is HTTPS: $effective_url"
    fi
  fi

  hosted_html="$(cat "$hosted_html_file")"
  if contains_expected_privacy_text "$hosted_html"; then
    ok "Hosted privacy-policy content matches expected app/privacy text"
  else
    fail "Hosted privacy-policy URL is reachable but missing expected Seemops privacy-policy text"
  fi
  if cmp -s "$hosted_html_file" "docs/privacy-policy.html"; then
    ok "Hosted privacy-policy content exactly matches docs/privacy-policy.html"
  else
    hosted_privacy_sha="$(privacy_file_sha256 "$hosted_html_file")"
    local_privacy_sha="$(privacy_file_sha256 "docs/privacy-policy.html")"
    fail "Hosted privacy-policy content does not exactly match docs/privacy-policy.html"
    [[ -n "$local_privacy_sha" ]] && info "Local privacy-policy SHA-256: $local_privacy_sha"
    [[ -n "$hosted_privacy_sha" ]] && info "Hosted privacy-policy SHA-256: $hosted_privacy_sha"
  fi
  rm -f "$hosted_html_file"
}

section "Release artifacts"
require_file "app/build/outputs/apk/debug/app-debug.apk"
release_apks=(app/build/outputs/apk/release/*.apk)
require_any_file "release APK" "${release_apks[@]}"
require_file "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
require_file "app/build/outputs/bundle/release/app-release.aab"
main_build_input_mtime="$(latest_build_input_mtime_for app/src/main app/build.gradle.kts app/proguard-rules.pro)"
android_test_build_input_mtime="$(latest_build_input_mtime_for app/src/main app/src/androidTest app/build.gradle.kts)"
require_artifacts_current "debug APK" "$main_build_input_mtime" "app/build/outputs/apk/debug/app-debug.apk"
require_artifacts_current "release APK" "$main_build_input_mtime" "${release_apks[@]}"
require_artifacts_current "release AAB" "$main_build_input_mtime" "app/build/outputs/bundle/release/app-release.aab"
require_artifacts_current "Android test APK" "$android_test_build_input_mtime" "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

section "Documentation"
for doc in \
  docs/PLAY_STORE_METADATA.md \
  docs/PLAY_CONSOLE_SUBMISSION.md \
  docs/PLAY_STORE_RELEASE_CHECKLIST.md \
  docs/PRIVACY_POLICY.md \
  docs/privacy-policy.html \
  docs/DATA_SAFETY.md \
  docs/CONTENT_REVIEW.md \
  docs/CURRENT_APP_STATUS.md \
  docs/ASAP_FEATURES.md \
  docs/MANUAL_QA_REPORT.md \
  docs/FINAL_RELEASE_RUNBOOK.md \
  docs/RELEASE_AUDIT.md \
  docs/RELEASE_NOTES.md
do
  require_file "$doc"
done
if contains_expected_privacy_text "$(cat docs/privacy-policy.html)"; then
  ok "docs/privacy-policy.html content"
else
  fail "docs/privacy-policy.html is missing expected app/privacy text"
fi

section "Play Console handoff"
set +e
scripts/check-play-console-handoff.sh
handoff_exit="$?"
set -e
if [[ "$handoff_exit" == "0" ]]; then
  ok "Play Console handoff matches current app/store evidence"
else
  fail "Play Console handoff consistency check failed"
fi

section "Privacy policy URL"
if [[ -z "${SEEMOPS_PRIVACY_POLICY_URL:-}" ]]; then
  fail "SEEMOPS_PRIVACY_POLICY_URL is not set to the hosted privacy-policy URL"
elif url_problem="$(privacy_url_issue "$SEEMOPS_PRIVACY_POLICY_URL")"; then
  fail "SEEMOPS_PRIVACY_POLICY_URL $url_problem: $SEEMOPS_PRIVACY_POLICY_URL"
else
  ok "$SEEMOPS_PRIVACY_POLICY_URL"
  check_hosted_privacy_policy "$SEEMOPS_PRIVACY_POLICY_URL"
fi

section "Manual QA"
set +e
scripts/check-manual-qa-report.sh --require-confirmation
manual_qa_exit="$?"
set -e
if [[ "$manual_qa_exit" == "0" ]]; then
  ok "Manual QA report and confirmation are ready"
else
  fail "Manual QA report or confirmation is not ready"
fi

section "Lint and tests"
LINT_TEXT="app/build/reports/lint-results-debug.txt"
if [[ -f "$LINT_TEXT" ]] && grep -q "No issues found" "$LINT_TEXT"; then
  ok "$LINT_TEXT"
else
  fail "Debug lint report is missing or has issues: $LINT_TEXT"
fi

UNIT_TEST_FILES=(app/build/test-results/testDebugUnitTest/TEST-*.xml)
check_junit_results "Unit tests" "$MIN_UNIT_TESTS" "${UNIT_TEST_FILES[@]}"
UNIT_TEST_SOURCE_MTIME="$(latest_source_mtime_for app/src/main/java app/src/test/java app/build.gradle.kts)"
require_results_current "Unit test results" "$UNIT_TEST_SOURCE_MTIME" "${UNIT_TEST_FILES[@]}"

BUILT_IN_PACKS_TEST="app/build/test-results/testDebugUnitTest/TEST-com.snupai.trinkspiel.data.BuiltInPacksTest.xml"
check_junit_results "Built-in content guardrails" "$MIN_BUILT_IN_PACK_TESTS" "$BUILT_IN_PACKS_TEST"

ANDROID_TEST_FILES=(app/build/outputs/androidTest-results/connected/debug/TEST-*.xml)
check_junit_results "Connected Android tests" "$MIN_CONNECTED_ANDROID_TESTS" "${ANDROID_TEST_FILES[@]}"
ANDROID_TEST_SOURCE_MTIME="$(latest_source_mtime_for app/src/main/java app/src/androidTest/java app/build.gradle.kts)"
require_results_current "Connected Android test results" "$ANDROID_TEST_SOURCE_MTIME" "${ANDROID_TEST_FILES[@]}"
for required_test in "${REQUIRED_CONNECTED_TESTS[@]}"; do
  IFS='|' read -r class_name test_name label <<<"$required_test"
  check_required_testcase "$class_name" "$test_name" "$label" "${ANDROID_TEST_FILES[@]}"
done

section "Store screenshots"
SCREENSHOT_DIR="docs/store-assets/screenshots/phone"
SCREENSHOT_FILES=(
  "$SCREENSHOT_DIR/01-first-run-setup.png"
  "$SCREENSHOT_DIR/02-game-ready.png"
  "$SCREENSHOT_DIR/03-card-drawn.png"
  "$SCREENSHOT_DIR/04-entry-manager.png"
  "$SCREENSHOT_DIR/05-settings-diagnostics.png"
  "$SCREENSHOT_DIR/06-settings-legal.png"
)
require_dimensions "${SCREENSHOT_FILES[0]}" 1080 2400
require_dimensions "${SCREENSHOT_FILES[1]}" 1080 2400
require_dimensions "${SCREENSHOT_FILES[2]}" 1080 2400
require_dimensions "${SCREENSHOT_FILES[3]}" 1080 2400
require_dimensions "${SCREENSHOT_FILES[4]}" 1080 2400
require_dimensions "${SCREENSHOT_FILES[5]}" 1080 2400
require_screenshots_current "${SCREENSHOT_FILES[@]}"
set +e
scripts/check-store-screenshot-polish.sh "${SCREENSHOT_FILES[@]}"
screenshot_polish_exit="$?"
set -e
if [[ "$screenshot_polish_exit" == "0" ]]; then
  ok "Store screenshots have clean demo-mode status bars"
else
  fail "Store screenshot polish check failed; recapture with scripts/capture-store-screenshots.sh"
fi

section "Store graphics"
require_dimensions "docs/store-assets/feature-graphic.png" 1024 500
require_dimensions "docs/store-assets/store-icon.png" 512 512
require_file "docs/store-assets/source/feature-graphic.svg"
require_file "docs/store-assets/source/store-icon.svg"

section "Launcher icons"
require_dimensions "app/src/main/res/mipmap-mdpi/ic_launcher.webp" 48 48
require_dimensions "app/src/main/res/mipmap-hdpi/ic_launcher.webp" 72 72
require_dimensions "app/src/main/res/mipmap-xhdpi/ic_launcher.webp" 96 96
require_dimensions "app/src/main/res/mipmap-xxhdpi/ic_launcher.webp" 144 144
require_dimensions "app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" 192 192
require_file "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml"
require_file "app/src/main/res/drawable/ic_launcher_foreground.xml"
require_file "app/src/main/res/drawable/ic_launcher_monochrome.xml"
require_file "app/src/main/res/values/ic_launcher_background.xml"
STALE_FOREGROUNDS=(app/src/main/res/mipmap-*/ic_launcher_foreground.webp)
if (( ${#STALE_FOREGROUNDS[@]} > 0 )); then
  fail "Stale launcher foreground WebP files remain: ${STALE_FOREGROUNDS[*]}"
else
  ok "No stale launcher foreground WebP files"
fi

section "Release signing"
AAB="app/build/outputs/bundle/release/app-release.aab"
if ! command -v jarsigner >/dev/null 2>&1; then
  fail "jarsigner is not available on PATH"
elif [[ ! -f "$AAB" ]]; then
  fail "Missing release bundle: $AAB"
else
  signing_status="$(release_aab_signing_status "$AAB")"
  case "$signing_status" in
    unsigned)
      fail "Release bundle exists but is unsigned. Configure SEEMOPS_RELEASE_* values or signing/release-signing.properties, then run scripts/check-release-signing-config.sh."
      signing_failed=1
      ;;
    debug-signed)
      fail "Release bundle is signed with Android debug signing material. Configure a real release keystore, rebuild :app:bundleRelease, then rerun this gate."
      signing_failed=1
      ;;
    signed)
      ok "Release bundle is signed with non-debug signing material and verified: $AAB"
      ;;
    *)
      fail "Could not confirm release bundle signature: $AAB"
      ;;
  esac
fi

if (( failures > 0 )); then
  printf '\nRelease preflight failed with %s issue(s).\n' "$failures" >&2
  if (( signing_failed == 1 && failures == 1 )); then
    exit 2
  fi
  exit 1
fi

printf '\nRelease preflight passed. Upload the signed AAB from %s.\n' "$AAB"
