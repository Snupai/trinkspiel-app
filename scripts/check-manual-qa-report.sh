#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

REPORT="${SEEMOPS_MANUAL_QA_REPORT:-docs/MANUAL_QA_REPORT.md}"
REQUIRE_CONFIRMATION=0
LIST_REQUIRED_CHECKS=0
LIST_EVIDENCE_CHECKS=0
MIN_PASSED_ROWS=34

required_checks() {
  cat <<'EOF'
Fresh install opens the 18+ first-run setup screen.
Gameplay is blocked until age and safety confirmations are accepted.
First-run setup can start with the Classic Starter pack.
First-run setup can start with another selected built-in starter pack.
First-run setup can start with an empty deck.
First-run setup defaults to `Locker` intensity unless changed.
Standard packs load without duplicates, including the Spieleabend Pack and Feierabend Pack.
Card draw, skip, complete, and undo work.
Local session recap updates after drawing cards and stays readable on the game screen.
Pack templates create paused draft cards that can be edited and activated.
Custom card add, edit, pause/reactivate, and delete work from the entry manager.
Card-pack export creates a readable JSON file through the Android file picker.
Card-pack import shows the validation preview before applying a file.
Full backup export creates a readable JSON file through the Android file picker.
Full backup import restores cards and settings after preview confirmation.
Device-transfer package shares as a JSON attachment through Android's chooser.
Device-transfer package can be received on another device or emulator and opened/shared directly to Seemops.
Received device-transfer package imports through the backup preview flow and restores the expected cards/settings.
Diagnostics report can be shared/exported and does not include card text or player names.
Last-issue shortcut is disabled without a crash summary, then share/export/delete work when a stored issue exists.
Support request can be prepared through Android's share sheet and does not include card text, player names, or crash stack traces.
App/version/legal section is visible in settings.
Privacy policy can be viewed from settings and contains the expected no-ads/no-analytics/no-server-collection wording.
Privacy policy can be shared from settings through Android's share sheet.
Round reset clears active card and recap state.
Score reset clears player score totals without removing players.
Player reset clears the player panel.
Age/safety reset returns to the first-run gate with start disabled until confirmations are accepted.
Dark, light, and system themes render legibly.
Large font scale does not clip primary setup/game actions, settings actions, player chips, or recap content.
Icon-only controls and important actions have clear TalkBack labels.
Touch targets remain comfortable on a phone-sized device.
Light/dark contrast remains readable in setup, gameplay, entry manager, settings, and dialogs.
Store screenshots still match the current UI.
EOF
}

required_evidence_checks() {
  cat <<'EOF'
Card-pack export creates a readable JSON file through the Android file picker.
Card-pack import shows the validation preview before applying a file.
Full backup export creates a readable JSON file through the Android file picker.
Full backup import restores cards and settings after preview confirmation.
Device-transfer package shares as a JSON attachment through Android's chooser.
Device-transfer package can be received on another device or emulator and opened/shared directly to Seemops.
Received device-transfer package imports through the backup preview flow and restores the expected cards/settings.
Diagnostics report can be shared/exported and does not include card text or player names.
Last-issue shortcut is disabled without a crash summary, then share/export/delete work when a stored issue exists.
Support request can be prepared through Android's share sheet and does not include card text, player names, or crash stack traces.
Privacy policy can be shared from settings through Android's share sheet.
Dark, light, and system themes render legibly.
Large font scale does not clip primary setup/game actions, settings actions, player chips, or recap content.
Icon-only controls and important actions have clear TalkBack labels.
Touch targets remain comfortable on a phone-sized device.
Light/dark contrast remains readable in setup, gameplay, entry manager, settings, and dialogs.
EOF
}

usage() {
  printf '%s\n' "Usage: scripts/check-manual-qa-report.sh [--require-confirmation] [--list-required-checks] [--list-evidence-checks]"
  printf '%s\n' "Validates the manual QA report fields and checklist rows."
  printf '%s\n' "--require-confirmation also requires SEEMOPS_MANUAL_QA_CONFIRMED=1."
  printf '%s\n' "--list-required-checks prints the release-required checklist rows and exits."
  printf '%s\n' "--list-evidence-checks prints high-risk rows that need concrete Notes evidence and exits."
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --require-confirmation)
      REQUIRE_CONFIRMATION=1
      shift
      ;;
    --list-required-checks)
      LIST_REQUIRED_CHECKS=1
      shift
      ;;
    --list-evidence-checks)
      LIST_EVIDENCE_CHECKS=1
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

if [[ "$LIST_REQUIRED_CHECKS" == "1" ]]; then
  required_checks
  exit 0
fi

if [[ "$LIST_EVIDENCE_CHECKS" == "1" ]]; then
  required_evidence_checks
  exit 0
fi

failures=0

ok() {
  printf '  OK      %s\n' "$1"
}

fail() {
  printf '  FAIL    %s\n' "$1"
  failures=$(( failures + 1 ))
}

action() {
  printf '  ACTION  %s\n' "$1"
}

field_value() {
  local field="$1"
  sed -n "s/^- ${field}:[[:space:]]*//p" "$REPORT" | head -n 1
}

checklist_stats() {
  local report="$1"
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
  ' "$report"
}

checklist_has_check() {
  local report="$1"
  local required_check="$2"
  awk -v required_check="$required_check" '
    function trim(value) {
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
      return value
    }
    BEGIN {
      in_table = 0
      found = 0
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
      if (column_count >= 5 && trim(columns[3]) == required_check) {
        found = 1
      }
    }
    END {
      exit found ? 0 : 1
    }
  ' "$report"
}

checklist_status_and_notes() {
  local report="$1"
  local required_check="$2"
  awk -v required_check="$required_check" '
    function trim(value) {
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
      return value
    }
    BEGIN {
      in_table = 0
      found = 0
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
      if (column_count >= 5 && trim(columns[3]) == required_check) {
        print trim(columns[4]) "\t" trim(columns[5])
        found = 1
      }
    }
    END {
      exit found ? 0 : 1
    }
  ' "$report"
}

note_has_concrete_evidence() {
  local note="$1"
  local compact_note
  compact_note="$(printf '%s' "$note" | tr -d '[:space:]')"

  if [[ -z "$compact_note" ]]; then
    return 1
  fi
  if [[ "$note" == *"TODO evidence:"* ]]; then
    return 1
  fi
  if [[ "$note" =~ ^[[:space:]]*(TODO|TBD|OK|Ok|ok|Passed|passed|Pass|pass|N/A|n/a|NA|na)[[:space:]]*$ ]]; then
    return 1
  fi
  if (( ${#compact_note} < 10 )); then
    return 1
  fi

  return 0
}

current_app_version() {
  local version_name version_code
  version_name="$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' app/build.gradle.kts | head -n 1)"
  version_code="$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' app/build.gradle.kts | head -n 1)"
  if [[ -n "$version_name" && -n "$version_code" ]]; then
    printf '%s (%s)' "$version_name" "$version_code"
  fi
}

printf 'Manual QA report check\n'

if [[ -s "$REPORT" ]]; then
  ok "$REPORT"
else
  fail "Missing $REPORT"
fi

if [[ "$REQUIRE_CONFIRMATION" == "1" ]]; then
  if [[ "${SEEMOPS_MANUAL_QA_CONFIRMED:-}" == "1" ]]; then
    ok "SEEMOPS_MANUAL_QA_CONFIRMED=1"
  else
    fail "SEEMOPS_MANUAL_QA_CONFIRMED is not set to 1"
  fi
fi

if [[ -s "$REPORT" ]]; then
  expected_app_version="$(current_app_version)"
  for field in "Device model" "Android version" "App version" "Tester" "Date"; do
    value="$(field_value "$field")"
    if [[ -z "$value" || "$value" == "TODO" ]]; then
      fail "$field is not filled"
    elif [[ "$field" == "App version" && -n "$expected_app_version" && "$value" != "$expected_app_version" ]]; then
      fail "App version is $value; expected current app version $expected_app_version"
    elif [[ "$field" == "Date" && ! "$value" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
      fail "Date must use YYYY-MM-DD format (current: $value)"
    else
      ok "$field: $value"
    fi
  done

  result="$(field_value "Result")"
  if [[ "$result" == "Passed" ]]; then
    ok "Result: Passed"
  else
    fail "Result must be Passed before release (current: ${result:-missing})"
  fi

  read -r checklist_rows passed_rows todo_rows failed_rows invalid_rows < <(checklist_stats "$REPORT")
  if (( checklist_rows > 0 )); then
    ok "Checklist rows found: ${checklist_rows}"
  else
    fail "Checklist has no rows"
  fi

  required_check_count=0
  missing_required_checks=0
  while IFS= read -r required_check; do
    [[ -z "$required_check" ]] && continue
    required_check_count=$(( required_check_count + 1 ))
    if ! checklist_has_check "$REPORT" "$required_check"; then
      fail "Checklist is missing required check: $required_check"
      missing_required_checks=$(( missing_required_checks + 1 ))
    fi
  done < <(required_checks)
	  if (( missing_required_checks == 0 )); then
	    ok "Required checklist checks present: ${required_check_count}"
	  fi

	  evidence_check_count=0
	  missing_evidence_checks=0
	  incomplete_evidence_notes=0
	  while IFS= read -r evidence_check; do
	    [[ -z "$evidence_check" ]] && continue
	    evidence_check_count=$(( evidence_check_count + 1 ))
	    if row_data="$(checklist_status_and_notes "$REPORT" "$evidence_check")"; then
	      status="${row_data%%$'\t'*}"
	      notes="${row_data#*$'\t'}"
	      if [[ "$status" == "Passed" ]] && ! note_has_concrete_evidence "$notes"; then
	        fail "Passed high-risk row needs concrete Notes evidence: $evidence_check"
	        incomplete_evidence_notes=$(( incomplete_evidence_notes + 1 ))
	      fi
	    else
	      fail "Checklist is missing high-risk evidence row: $evidence_check"
	      missing_evidence_checks=$(( missing_evidence_checks + 1 ))
	    fi
	  done < <(required_evidence_checks)
	  if (( missing_evidence_checks == 0 )); then
	    ok "High-risk evidence-note rows present: ${evidence_check_count}"
	  fi
	  if (( incomplete_evidence_notes == 0 )); then
	    ok "High-risk Passed rows include concrete Notes evidence"
	  fi
	
	  if (( todo_rows > 0 || failed_rows > 0 )); then
	    fail "Checklist still contains TODO or Failed rows (${todo_rows} TODO, ${failed_rows} Failed)"
  else
    ok "Checklist has no TODO or Failed rows"
  fi

  if (( invalid_rows > 0 )); then
    fail "Checklist contains ${invalid_rows} row(s) with an invalid status"
  else
    ok "Checklist statuses are valid"
  fi

  if (( passed_rows == checklist_rows && passed_rows >= MIN_PASSED_ROWS )); then
    ok "Checklist Passed rows: ${passed_rows}/${checklist_rows}"
  elif (( checklist_rows < MIN_PASSED_ROWS )); then
    fail "Checklist has ${checklist_rows} rows; expected at least ${MIN_PASSED_ROWS}"
  else
    fail "Checklist has ${passed_rows}/${checklist_rows} Passed rows; every row must be Passed"
  fi
fi

if (( failures > 0 )); then
  printf '\nManual QA report check failed with %s issue(s).\n' "$failures"
  action "Run scripts/prepare-manual-qa.sh --update-report --tester \"Your Name\" on a phone-sized device."
  action "Complete every checklist row by hand, set Result: Passed, then export SEEMOPS_MANUAL_QA_CONFIRMED=1 for the final gate."
  exit 1
fi

printf '\nManual QA report check passed.\n'
