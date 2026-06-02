#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
source "$ROOT_DIR/scripts/android-sdk-env.sh"
source "$ROOT_DIR/scripts/privacy-policy-utils.sh"
source "$ROOT_DIR/scripts/signing-utils.sh"
ensure_android_sdk_tools_on_path
shopt -s nullglob

MIN_UNIT_TESTS=34
MIN_BUILT_IN_PACK_TESTS=5
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

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  printf '%s\n' "Usage: scripts/release-status.sh"
  printf '%s\n' "Prints a non-mutating release readiness snapshot from current build outputs."
  printf '%s\n' "Set SEEMOPS_RELEASE_STATUS_CHECK_ADB=1 to actively query attached Android devices."
  exit 0
fi

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

current_app_version() {
  local version_name version_code
  version_name="$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' app/build.gradle.kts | head -n 1)"
  version_code="$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' app/build.gradle.kts | head -n 1)"
  if [[ -n "$version_name" && -n "$version_code" ]]; then
    printf '%s (%s)' "$version_name" "$version_code"
  fi
}

adb_server_running() {
  pgrep -f '[a]db .*fork-server server' >/dev/null 2>&1
}

should_probe_adb_devices() {
  [[ "${SEEMOPS_RELEASE_STATUS_CHECK_ADB:-0}" == "1" ]] || adb_server_running
}

print_result_freshness() {
  local label="$1"
  local latest_source_mtime="$2"
  shift 2
  local latest_result_mtime

  if (( $# == 0 )); then
    return 1
  fi
  latest_result_mtime="$(latest_result_mtime_for "$@")"
  if (( latest_source_mtime == 0 )); then
    printf '  REVIEW  could not determine source timestamp for %s\n' "$label"
    return 1
  elif (( latest_result_mtime >= latest_source_mtime )); then
    printf '  OK      %s are current with source files\n' "$label"
    return 0
  else
    printf '  REVIEW  %s are older than current source files; rerun the matching tests\n' "$label"
    return 1
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

printf 'Seemops Trinkspiel release status\n'
printf 'Generated: %s\n\n' "$(date '+%Y-%m-%d %H:%M:%S %Z')"

printf 'Artifacts:\n'
print_artifact_status() {
  local artifact="$1"
  if [[ -f "$artifact" ]]; then
    size="$(ls -lh "$artifact" | awk '{ print $5 }')"
    printf '  OK      %s (%s)\n' "$artifact" "$size"
  else
    printf '  MISSING %s\n' "$artifact"
  fi
}

print_artifact_status "app/build/outputs/apk/debug/app-debug.apk"
release_apks=(app/build/outputs/apk/release/*.apk)
if (( ${#release_apks[@]} > 0 )); then
  for artifact in "${release_apks[@]}"; do
    print_artifact_status "$artifact"
  done
else
  printf '  MISSING app/build/outputs/apk/release/*.apk\n'
fi
print_artifact_status "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
print_artifact_status "app/build/outputs/bundle/release/app-release.aab"
print_artifact_freshness() {
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
    printf '  REVIEW  Could not determine build input timestamp for %s\n' "$label"
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
    printf '  REVIEW  %s older than current app build inputs: %s\n' "$label" "${stale_artifacts[*]}"
  else
    printf '  OK      %s current with app build inputs\n' "$label"
  fi
}
main_build_input_mtime="$(latest_build_input_mtime_for app/src/main app/build.gradle.kts app/proguard-rules.pro)"
android_test_build_input_mtime="$(latest_build_input_mtime_for app/src/main app/src/androidTest app/build.gradle.kts)"
print_artifact_freshness "debug APK" "$main_build_input_mtime" "app/build/outputs/apk/debug/app-debug.apk"
print_artifact_freshness "release APK" "$main_build_input_mtime" "${release_apks[@]}"
print_artifact_freshness "release AAB" "$main_build_input_mtime" "app/build/outputs/bundle/release/app-release.aab"
print_artifact_freshness "Android test APK" "$android_test_build_input_mtime" "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

printf '\nRelease signing:\n'
AAB="app/build/outputs/bundle/release/app-release.aab"
if [[ -f "$AAB" ]]; then
  signing_status="$(release_aab_signing_status "$AAB")"
  case "$signing_status" in
    unsigned)
      printf '  UNSIGNED %s\n' "$AAB"
      ;;
    debug-signed)
      printf '  BLOCKED  %s is signed with Android debug signing material\n' "$AAB"
      ;;
    signed)
      printf '  SIGNED   %s (non-debug signing material)\n' "$AAB"
      ;;
    *)
      printf '  UNKNOWN  %s\n' "$AAB"
      ;;
  esac
else
  printf '  MISSING  %s\n' "$AAB"
fi

printf '\n'
if [[ -x "scripts/check-release-signing-config.sh" ]]; then
  set +e
  scripts/check-release-signing-config.sh
  signing_config_exit="$?"
  set -e
  if [[ "$signing_config_exit" == "0" ]]; then
    printf '  READY   visible signing inputs passed self-check\n'
  else
    printf '  BLOCKED visible signing inputs are incomplete or inconsistent\n'
  fi
else
  printf '  MISSING scripts/check-release-signing-config.sh\n'
fi

printf '\nPrivacy policy:\n'
if [[ -s "docs/PRIVACY_POLICY.md" ]]; then
  printf '  OK      docs/PRIVACY_POLICY.md\n'
else
  printf '  MISSING docs/PRIVACY_POLICY.md\n'
fi
if [[ -s "docs/privacy-policy.html" ]]; then
  printf '  OK      docs/privacy-policy.html\n'
else
  printf '  MISSING docs/privacy-policy.html\n'
fi
if [[ -z "${SEEMOPS_PRIVACY_POLICY_URL:-}" ]]; then
  printf '  BLOCKED SEEMOPS_PRIVACY_POLICY_URL is not set\n'
elif privacy_problem="$(privacy_url_issue "$SEEMOPS_PRIVACY_POLICY_URL")"; then
  printf '  REVIEW  SEEMOPS_PRIVACY_POLICY_URL %s: %s\n' "$privacy_problem" "$SEEMOPS_PRIVACY_POLICY_URL"
else
  printf '  READY   %s\n' "$SEEMOPS_PRIVACY_POLICY_URL"
  if command -v curl >/dev/null 2>&1; then
    hosted_html_file="$(mktemp)"
    if effective_url="$(fetch_hosted_privacy_policy "$SEEMOPS_PRIVACY_POLICY_URL" "$hosted_html_file")"; then
      printf '  OK      hosted privacy-policy URL is reachable\n'
      if [[ "$effective_url" != "$SEEMOPS_PRIVACY_POLICY_URL" ]]; then
        if effective_problem="$(privacy_url_issue "$effective_url")"; then
          printf '  REVIEW  hosted privacy-policy redirects to a URL that %s: %s\n' "$effective_problem" "$effective_url"
        else
          printf '  OK      hosted privacy-policy final URL is HTTPS: %s\n' "$effective_url"
        fi
      fi
      if contains_expected_privacy_text "$(cat "$hosted_html_file")"; then
        printf '  OK      hosted privacy-policy content matches expected text\n'
      else
        printf '  REVIEW  hosted privacy-policy page is reachable but missing expected Seemops text\n'
      fi
      if cmp -s "$hosted_html_file" "docs/privacy-policy.html"; then
        printf '  OK      hosted privacy-policy content exactly matches docs/privacy-policy.html\n'
      else
        printf '  REVIEW  hosted privacy-policy content does not exactly match docs/privacy-policy.html\n'
        local_privacy_sha="$(privacy_file_sha256 "docs/privacy-policy.html")"
        hosted_privacy_sha="$(privacy_file_sha256 "$hosted_html_file")"
        [[ -n "$local_privacy_sha" ]] && printf '  INFO    local privacy-policy SHA-256: %s\n' "$local_privacy_sha"
        [[ -n "$hosted_privacy_sha" ]] && printf '  INFO    hosted privacy-policy SHA-256: %s\n' "$hosted_privacy_sha"
      fi
    else
      printf '  REVIEW  hosted privacy-policy URL is not reachable\n'
    fi
    rm -f "$hosted_html_file"
  else
    printf '  REVIEW  curl is unavailable; hosted privacy-policy content was not checked\n'
  fi
fi

printf '\nPlay Console handoff:\n'
if [[ -x "scripts/check-play-console-handoff.sh" ]]; then
  set +e
  handoff_output="$(scripts/check-play-console-handoff.sh 2>&1)"
  handoff_exit="$?"
  set -e
  if [[ "$handoff_exit" == "0" ]]; then
    printf '  READY   docs/PLAY_CONSOLE_SUBMISSION.md matches current app/store evidence\n'
  else
    printf '  BLOCKED docs/PLAY_CONSOLE_SUBMISSION.md is missing evidence or out of sync\n'
    printf '%s\n' "$handoff_output" | sed 's/^/  DETAIL  /'
  fi
else
  printf '  MISSING scripts/check-play-console-handoff.sh\n'
fi

printf '\nManual QA:\n'
if [[ -s "docs/MANUAL_QA_REPORT.md" ]]; then
  printf '  OK      docs/MANUAL_QA_REPORT.md\n'
else
  printf '  MISSING docs/MANUAL_QA_REPORT.md\n'
fi
if [[ "${SEEMOPS_MANUAL_QA_CONFIRMED:-}" == "1" ]]; then
  printf '  READY   SEEMOPS_MANUAL_QA_CONFIRMED=1\n'
else
  printf '  BLOCKED SEEMOPS_MANUAL_QA_CONFIRMED is not set to 1\n'
fi
expected_app_version="$(current_app_version)"
for field in "Device model" "Android version" "App version" "Tester" "Date"; do
  value="$(sed -n "s/^- ${field}:[[:space:]]*//p" docs/MANUAL_QA_REPORT.md | head -n 1)"
  if [[ -z "$value" || "$value" == "TODO" ]]; then
    printf '  REVIEW  %s is not filled in docs/MANUAL_QA_REPORT.md\n' "$field"
  elif [[ "$field" == "App version" && -n "$expected_app_version" && "$value" != "$expected_app_version" ]]; then
    printf '  REVIEW  App version is %s; expected current app version %s\n' "$value" "$expected_app_version"
  elif [[ "$field" == "Date" && ! "$value" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
    printf '  REVIEW  Date must use YYYY-MM-DD format (current: %s)\n' "$value"
  elif [[ "$field" == "App version" ]]; then
    printf '  OK      App version matches current build: %s\n' "$value"
  else
    printf '  OK      %s recorded\n' "$field"
  fi
done
result_value="$(sed -n 's/^- Result:[[:space:]]*//p' docs/MANUAL_QA_REPORT.md | head -n 1)"
if [[ "$result_value" == "Passed" ]]; then
  printf '  OK      Result is Passed\n'
else
  printf '  REVIEW  Result is %s; set to Passed only after real-device QA passes\n' "${result_value:-missing}"
fi
read -r checklist_rows passed_rows todo_rows failed_rows invalid_rows < <(manual_qa_checklist_stats)
if (( checklist_rows > 0 )); then
  printf '  OK      checklist rows found: %s\n' "$checklist_rows"
else
  printf '  REVIEW  checklist has no rows\n'
fi
if (( todo_rows > 0 || failed_rows > 0 )); then
  printf '  REVIEW  checklist still contains TODO or Failed rows (%s TODO, %s Failed)\n' "$todo_rows" "$failed_rows"
else
  printf '  OK      checklist has no TODO or Failed rows\n'
fi
if (( invalid_rows > 0 )); then
  printf '  REVIEW  checklist contains %s invalid status row(s)\n' "$invalid_rows"
else
  printf '  OK      checklist statuses are valid\n'
fi
if (( passed_rows == checklist_rows && passed_rows >= MIN_MANUAL_QA_ROWS )); then
  printf '  OK      checklist Passed rows: %s/%s\n' "$passed_rows" "$checklist_rows"
elif (( checklist_rows < MIN_MANUAL_QA_ROWS )); then
  printf '  REVIEW  checklist rows: %s; expected at least %s\n' "$checklist_rows" "$MIN_MANUAL_QA_ROWS"
else
  printf '  REVIEW  checklist Passed rows: %s/%s; every row must be Passed\n' "$passed_rows" "$checklist_rows"
fi
if [[ -x "scripts/check-manual-qa-report.sh" ]]; then
  set +e
  manual_qa_check_output="$(scripts/check-manual-qa-report.sh --require-confirmation 2>&1)"
  manual_qa_check_exit="$?"
  set -e
  if grep -Fq "High-risk evidence-note rows present: 16" <<<"$manual_qa_check_output"; then
    printf '  OK      high-risk evidence-note rows present: 16\n'
  fi
  if grep -Fq "Passed high-risk row needs concrete Notes evidence" <<<"$manual_qa_check_output"; then
    printf '  REVIEW  one or more Passed high-risk rows still need concrete Notes evidence\n'
  fi
  if [[ "$manual_qa_check_exit" == "0" ]]; then
    printf '  READY   manual QA report checker passed\n'
  else
    printf '  BLOCKED manual QA report checker has release-blocking issues\n'
  fi
else
  printf '  MISSING scripts/check-manual-qa-report.sh\n'
fi
if [[ -x "scripts/check-manual-qa-evidence-packet.sh" ]]; then
  evidence_packet_dir=""
  expected_evidence_fixtures_dir=""
  if [[ -d "build/manual-qa/evidence" ]]; then
    evidence_packet_dir="build/manual-qa/evidence"
    expected_evidence_fixtures_dir="build/manual-qa/fixtures"
  else
    latest_package="$(find build/play-store-upload -mindepth 1 -maxdepth 1 -type d -name 'seemops-trinkspiel-*' -print 2>/dev/null | sort | tail -n 1)"
    if [[ -n "$latest_package" && -d "$latest_package/manual-qa-evidence" ]]; then
      evidence_packet_dir="$latest_package/manual-qa-evidence"
      expected_evidence_fixtures_dir="manual-qa-fixtures"
    fi
  fi

  if [[ -n "$evidence_packet_dir" ]]; then
    set +e
    evidence_packet_check_output="$(scripts/check-manual-qa-evidence-packet.sh --fixtures-dir "$expected_evidence_fixtures_dir" "$evidence_packet_dir" 2>&1)"
    evidence_packet_check_exit="$?"
    set -e
    if grep -Fq "checklist-index.md contains exactly 34 row(s)" <<<"$evidence_packet_check_output"; then
      printf '  OK      manual QA evidence packet checklist rows: 34\n'
    fi
    if grep -Fq "evidence-notes-template.md contains exactly 16 high-risk prompt row(s)" <<<"$evidence_packet_check_output"; then
      printf '  OK      manual QA evidence packet high-risk prompts: 16\n'
    fi
    if grep -Fq "summary.json has exact row counts" <<<"$evidence_packet_check_output"; then
      printf '  OK      manual QA evidence packet summary counts are exact\n'
    fi
    if [[ "$evidence_packet_check_exit" == "0" ]]; then
      printf '  READY   manual QA evidence packet is valid: %s\n' "$evidence_packet_dir"
    else
      printf '  REVIEW  manual QA evidence packet has issues: %s\n' "$evidence_packet_dir"
    fi
  else
    printf '  REVIEW  manual QA evidence packet has not been generated; run scripts/prepare-manual-qa.sh --guide-only\n'
  fi
else
  printf '  MISSING scripts/check-manual-qa-evidence-packet.sh\n'
fi

printf '\nLint:\n'
LINT_TEXT="app/build/reports/lint-results-debug.txt"
if [[ -f "$LINT_TEXT" ]]; then
  if grep -q "No issues found" "$LINT_TEXT"; then
    printf '  OK      No issues found\n'
  else
    printf '  REVIEW  %s\n' "$LINT_TEXT"
  fi
else
  printf '  MISSING %s\n' "$LINT_TEXT"
fi

printf '\nUnit tests:\n'
TEST_FILES=(app/build/test-results/testDebugUnitTest/TEST-*.xml)
if (( ${#TEST_FILES[@]} == 0 )); then
  printf '  MISSING test result XML files\n'
else
  total=0
  failures=0
  errors=0
  skipped=0
  for file in "${TEST_FILES[@]}"; do
    attrs="$(head -n 2 "$file" | tail -n 1)"
    tests_value="$(sed -n 's/.* tests="\([0-9]*\)".*/\1/p' <<<"$attrs")"
    failures_value="$(sed -n 's/.* failures="\([0-9]*\)".*/\1/p' <<<"$attrs")"
    errors_value="$(sed -n 's/.* errors="\([0-9]*\)".*/\1/p' <<<"$attrs")"
    skipped_value="$(sed -n 's/.* skipped="\([0-9]*\)".*/\1/p' <<<"$attrs")"
    total=$(( total + ${tests_value:-0} ))
    failures=$(( failures + ${failures_value:-0} ))
    errors=$(( errors + ${errors_value:-0} ))
    skipped=$(( skipped + ${skipped_value:-0} ))
	  done
	  if (( total >= MIN_UNIT_TESTS && failures == 0 && errors == 0 )); then
	    printf '  OK      %s tests, %s failures, %s errors, %s skipped\n' "$total" "$failures" "$errors" "$skipped"
	    unit_source_mtime="$(latest_source_mtime_for app/src/main/java app/src/test/java app/build.gradle.kts)"
	    print_result_freshness "unit test results" "$unit_source_mtime" "${TEST_FILES[@]}" || true
	  else
	    printf '  REVIEW  %s tests, %s failures, %s errors, %s skipped; expected at least %s clean tests\n' \
	      "$total" "$failures" "$errors" "$skipped" "$MIN_UNIT_TESTS"
  fi
fi

printf '\nContent review:\n'
if [[ -s "docs/CONTENT_REVIEW.md" ]]; then
  printf '  OK      docs/CONTENT_REVIEW.md\n'
else
  printf '  MISSING docs/CONTENT_REVIEW.md\n'
fi
BUILT_IN_PACKS_TEST="app/build/test-results/testDebugUnitTest/TEST-com.snupai.trinkspiel.data.BuiltInPacksTest.xml"
if [[ -f "$BUILT_IN_PACKS_TEST" ]]; then
  attrs="$(head -n 2 "$BUILT_IN_PACKS_TEST" | tail -n 1)"
  tests_value="$(sed -n 's/.* tests="\([0-9]*\)".*/\1/p' <<<"$attrs")"
  failures_value="$(sed -n 's/.* failures="\([0-9]*\)".*/\1/p' <<<"$attrs")"
  errors_value="$(sed -n 's/.* errors="\([0-9]*\)".*/\1/p' <<<"$attrs")"
  if (( ${tests_value:-0} >= MIN_BUILT_IN_PACK_TESTS && ${failures_value:-0} == 0 && ${errors_value:-0} == 0 )); then
    printf '  OK      BuiltInPacksTest content guardrails (%s tests)\n' "$tests_value"
  else
    printf '  REVIEW  BuiltInPacksTest result has %s tests, %s failures, %s errors; expected at least %s clean tests\n' \
      "${tests_value:-0}" "${failures_value:-0}" "${errors_value:-0}" "$MIN_BUILT_IN_PACK_TESTS"
  fi
else
  printf '  MISSING %s\n' "$BUILT_IN_PACKS_TEST"
  printf '  ACTION  Run ./gradlew :app:testDebugUnitTest --no-daemon\n'
fi

printf '\nConnected Android tests:\n'
ANDROID_TEST_FILES=(app/build/outputs/androidTest-results/connected/debug/TEST-*.xml)
connected_results_current=0
if (( ${#ANDROID_TEST_FILES[@]} > 0 )); then
  total=0
  failures=0
  errors=0
  skipped=0
  for file in "${ANDROID_TEST_FILES[@]}"; do
    attrs="$(head -n 2 "$file" | tail -n 1)"
    tests_value="$(sed -n 's/.* tests="\([0-9]*\)".*/\1/p' <<<"$attrs")"
    failures_value="$(sed -n 's/.* failures="\([0-9]*\)".*/\1/p' <<<"$attrs")"
    errors_value="$(sed -n 's/.* errors="\([0-9]*\)".*/\1/p' <<<"$attrs")"
    skipped_value="$(sed -n 's/.* skipped="\([0-9]*\)".*/\1/p' <<<"$attrs")"
    total=$(( total + ${tests_value:-0} ))
    failures=$(( failures + ${failures_value:-0} ))
    errors=$(( errors + ${errors_value:-0} ))
    skipped=$(( skipped + ${skipped_value:-0} ))
  done
  if (( total >= MIN_CONNECTED_ANDROID_TESTS && failures == 0 && errors == 0 )); then
    printf '  OK      %s tests, %s failures, %s errors, %s skipped\n' "$total" "$failures" "$errors" "$skipped"
    android_test_source_mtime="$(latest_source_mtime_for app/src/main/java app/src/androidTest/java app/build.gradle.kts)"
    if print_result_freshness "connected Android test results" "$android_test_source_mtime" "${ANDROID_TEST_FILES[@]}"; then
      connected_results_current=1
    fi
  else
    printf '  REVIEW  %s tests, %s failures, %s errors, %s skipped; expected at least %s clean tests\n' \
      "$total" "$failures" "$errors" "$skipped" "$MIN_CONNECTED_ANDROID_TESTS"
  fi

  for required_test in "${REQUIRED_CONNECTED_TESTS[@]}"; do
    IFS='|' read -r class_name test_name label <<<"$required_test"
    found=0
    for file in "${ANDROID_TEST_FILES[@]}"; do
      if grep -F "classname=\"$class_name\"" "$file" | grep -Fq "name=\"$test_name\""; then
        found=1
        break
      fi
    done
    if (( found == 1 )); then
      printf '  OK      %s\n' "$label"
    else
      printf '  REVIEW  missing %s.%s (%s)\n' "$class_name" "$test_name" "$label"
    fi
  done
else
  printf '  MISSING connected Android test result XML files\n'
fi
if command -v adb >/dev/null 2>&1; then
  if should_probe_adb_devices; then
    attached="$(adb devices | awk 'NR > 1 && $2 == "device" { count++ } END { print count + 0 }')"
    if [[ "$attached" == "0" ]]; then
      if [[ "$connected_results_current" == "1" ]]; then
        printf '  INFO    No attached Android device/emulator; connected test evidence is already current\n'
      else
        printf '  BLOCKED No attached Android device/emulator\n'
      fi
    else
      printf '  READY   %s attached device(s); run scripts/run-connected-ui-tests.sh\n' "$attached"
    fi
  elif [[ "$connected_results_current" == "1" ]]; then
    printf '  INFO    ADB server is not running; connected test evidence is already current\n'
    printf '  INFO    Set SEEMOPS_RELEASE_STATUS_CHECK_ADB=1 to probe attached devices\n'
  else
    printf '  REVIEW  ADB server is not running; device probe skipped to keep release-status non-mutating\n'
    printf '  ACTION  Attach/start a device and run scripts/run-connected-ui-tests.sh, or set SEEMOPS_RELEASE_STATUS_CHECK_ADB=1 to probe now\n'
  fi
else
  printf '  BLOCKED adb is not available; '
  android_sdk_tools_hint
fi

printf '\nStore screenshots:\n'
SCREENSHOT_DIR="docs/store-assets/screenshots/phone"
SCREENSHOT_FILES=(
  "$SCREENSHOT_DIR/01-first-run-setup.png"
  "$SCREENSHOT_DIR/02-game-ready.png"
  "$SCREENSHOT_DIR/03-card-drawn.png"
  "$SCREENSHOT_DIR/04-entry-manager.png"
  "$SCREENSHOT_DIR/05-settings-diagnostics.png"
  "$SCREENSHOT_DIR/06-settings-legal.png"
)
missing=0
stale=0
latest_ui_mtime="$(latest_ui_source_mtime)"
for screenshot in "${SCREENSHOT_FILES[@]}"; do
  if [[ -s "$screenshot" ]]; then
    size="$(ls -lh "$screenshot" | awk '{ print $5 }')"
    printf '  OK      %s (%s)\n' "$screenshot" "$size"
    if (( latest_ui_mtime > 0 )) && (( $(file_mtime "$screenshot") < latest_ui_mtime )); then
      stale=$(( stale + 1 ))
    fi
  else
    printf '  MISSING %s\n' "$screenshot"
    missing=$(( missing + 1 ))
  fi
done
if (( missing > 0 )); then
  printf '  ACTION  Run scripts/capture-store-screenshots.sh with a device/emulator attached\n'
fi
if (( missing == 0 && stale > 0 )); then
  printf '  REVIEW  %s screenshot(s) are older than the current UI source files\n' "$stale"
  printf '  ACTION  Run scripts/capture-store-screenshots.sh with a device/emulator attached\n'
elif (( missing == 0 )); then
  printf '  OK      screenshots are current with UI source files\n'
fi
if (( missing == 0 )); then
  if screenshot_polish_output="$(scripts/check-store-screenshot-polish.sh "${SCREENSHOT_FILES[@]}" 2>&1)"; then
    printf '  OK      screenshots have clean demo-mode status bars\n'
  else
    printf '  REVIEW  screenshot status bars need cleanup; recapture with scripts/capture-store-screenshots.sh\n'
    printf '%s\n' "$screenshot_polish_output" | sed 's/^/          /'
  fi
fi

printf '\nStore graphics:\n'
STORE_GRAPHICS=(
  "docs/store-assets/feature-graphic.png:1024:500"
  "docs/store-assets/store-icon.png:512:512"
)
graphics_missing=0
for graphic_spec in "${STORE_GRAPHICS[@]}"; do
  IFS=':' read -r graphic expected_width expected_height <<<"$graphic_spec"
  if [[ ! -s "$graphic" ]]; then
    printf '  MISSING %s\n' "$graphic"
    graphics_missing=$(( graphics_missing + 1 ))
    continue
  fi

  size="$(ls -lh "$graphic" | awk '{ print $5 }')"
  if command -v sips >/dev/null 2>&1; then
    actual_width="$(sips -g pixelWidth "$graphic" | awk '/pixelWidth/ { print $2 }')"
    actual_height="$(sips -g pixelHeight "$graphic" | awk '/pixelHeight/ { print $2 }')"
    if [[ "$actual_width" == "$expected_width" && "$actual_height" == "$expected_height" ]]; then
      printf '  OK      %s (%s, %sx%s)\n' "$graphic" "$size" "$actual_width" "$actual_height"
    else
      printf '  REVIEW  %s (%s, got %sx%s, expected %sx%s)\n' \
        "$graphic" "$size" "$actual_width" "$actual_height" "$expected_width" "$expected_height"
    fi
  else
    printf '  OK      %s (%s; install sips to verify dimensions)\n' "$graphic" "$size"
  fi
done
if (( graphics_missing > 0 )); then
  printf '  ACTION  Run scripts/generate-store-assets.sh\n'
fi

printf '\nLauncher icons:\n'
LAUNCHER_RESOURCES=(
  "app/src/main/res/mipmap-mdpi/ic_launcher.webp:48:48"
  "app/src/main/res/mipmap-hdpi/ic_launcher.webp:72:72"
  "app/src/main/res/mipmap-xhdpi/ic_launcher.webp:96:96"
  "app/src/main/res/mipmap-xxhdpi/ic_launcher.webp:144:144"
  "app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp:192:192"
)
launcher_missing=0
for launcher_spec in "${LAUNCHER_RESOURCES[@]}"; do
  IFS=':' read -r launcher expected_width expected_height <<<"$launcher_spec"
  if [[ ! -s "$launcher" ]]; then
    printf '  MISSING %s\n' "$launcher"
    launcher_missing=$(( launcher_missing + 1 ))
    continue
  fi

  size="$(ls -lh "$launcher" | awk '{ print $5 }')"
  if command -v sips >/dev/null 2>&1; then
    actual_width="$(sips -g pixelWidth "$launcher" | awk '/pixelWidth/ { print $2 }')"
    actual_height="$(sips -g pixelHeight "$launcher" | awk '/pixelHeight/ { print $2 }')"
    if [[ "$actual_width" == "$expected_width" && "$actual_height" == "$expected_height" ]]; then
      printf '  OK      %s (%s, %sx%s)\n' "$launcher" "$size" "$actual_width" "$actual_height"
    else
      printf '  REVIEW  %s (%s, got %sx%s, expected %sx%s)\n' \
        "$launcher" "$size" "$actual_width" "$actual_height" "$expected_width" "$expected_height"
    fi
  else
    printf '  OK      %s (%s; install sips to verify dimensions)\n' "$launcher" "$size"
  fi
done
for launcher_xml in \
  app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml \
  app/src/main/res/drawable/ic_launcher_foreground.xml \
  app/src/main/res/drawable/ic_launcher_monochrome.xml \
  app/src/main/res/values/ic_launcher_background.xml
do
  if [[ -s "$launcher_xml" ]]; then
    printf '  OK      %s\n' "$launcher_xml"
  else
    printf '  MISSING %s\n' "$launcher_xml"
    launcher_missing=$(( launcher_missing + 1 ))
  fi
done
STALE_FOREGROUNDS=(app/src/main/res/mipmap-*/ic_launcher_foreground.webp)
if (( ${#STALE_FOREGROUNDS[@]} > 0 )); then
  printf '  REVIEW  stale foreground WebP files remain: %s\n' "${STALE_FOREGROUNDS[*]}"
fi
if (( launcher_missing > 0 )); then
  printf '  ACTION  Run scripts/generate-launcher-icons.sh\n'
fi
