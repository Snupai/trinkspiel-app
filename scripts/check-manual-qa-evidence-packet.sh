#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

PACKET_DIR=""
EXPECTED_FIXTURES_DIR=""

usage() {
  printf '%s\n' "Usage: scripts/check-manual-qa-evidence-packet.sh [--fixtures-dir DIR] [PACKET_DIR]"
  printf '%s\n' "Validates a generated manual-QA evidence packet."
  printf '%s\n' "When PACKET_DIR is omitted, build/manual-qa/evidence is used if present; otherwise the latest package manual-qa-evidence folder is used."
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --fixtures-dir)
      EXPECTED_FIXTURES_DIR="${2:-}"
      if [[ -z "$EXPECTED_FIXTURES_DIR" ]]; then
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
      if [[ -n "$PACKET_DIR" ]]; then
        usage >&2
        exit 64
      fi
      PACKET_DIR="$1"
      shift
      ;;
  esac
done

if [[ -z "$PACKET_DIR" ]]; then
  if [[ -d "build/manual-qa/evidence" ]]; then
    PACKET_DIR="build/manual-qa/evidence"
  else
    latest_package="$(find build/play-store-upload -mindepth 1 -maxdepth 1 -type d -name 'seemops-trinkspiel-*' -print 2>/dev/null | sort | tail -n 1)"
    if [[ -n "$latest_package" && -d "$latest_package/manual-qa-evidence" ]]; then
      PACKET_DIR="$latest_package/manual-qa-evidence"
    fi
  fi
fi

failures=0

ok() {
  printf '  OK      %s\n' "$1"
}

fail() {
  printf '  FAIL    %s\n' "$1"
  failures=$(( failures + 1 ))
}

info() {
  printf '  INFO    %s\n' "$1"
}

require_file() {
  local file="$1"
  if [[ -s "$PACKET_DIR/$file" ]]; then
    ok "$file"
  else
    fail "Missing packet file: $file"
  fi
}

require_contains() {
  local file="$1"
  local expected="$2"
  local label="$3"
  if grep -Fq -- "$expected" "$PACKET_DIR/$file"; then
    ok "$label"
  else
    fail "$file is missing expected text: $expected"
  fi
}

printf 'Manual QA evidence packet check\n'

if [[ -z "$PACKET_DIR" || ! -d "$PACKET_DIR" ]]; then
  printf '  FAIL    Manual QA evidence packet not found. Run scripts/prepare-manual-qa.sh --guide-only or pass PACKET_DIR.\n' >&2
  exit 1
fi
PACKET_DIR="$(cd "$PACKET_DIR" && pwd)"
ok "Packet directory: $PACKET_DIR"

for file in \
  README.md \
  tester-run-sheet.md \
  evidence-notes-template.md \
  checklist-index.md \
  screenshots/README.md \
  files/README.md \
  summary.json \
  checksums.sha256
do
  require_file "$file"
done

if [[ -s "$PACKET_DIR/checksums.sha256" ]]; then
  if (cd "$PACKET_DIR" && shasum -a 256 -c checksums.sha256 >/dev/null); then
    ok "checksums.sha256 verifies"
  else
    fail "checksums.sha256 verification failed"
  fi
fi

require_contains "README.md" "does not replace \`docs/MANUAL_QA_REPORT.md\`" "packet defers to final report"
require_contains "README.md" "High-risk evidence rows: 16" "packet records high-risk row count"
require_contains "README.md" "tester-run-sheet.md" "packet documents tester run sheet"
require_contains "README.md" "Keep screenshots/files privacy-safe" "packet warns against private captures"
require_contains "tester-run-sheet.md" "Manual QA Tester Run Sheet" "run sheet has expected title"
require_contains "tester-run-sheet.md" "Tester action" "run sheet carries tester actions"
require_contains "tester-run-sheet.md" "Expected proof" "run sheet carries expected proof guidance"
require_contains "tester-run-sheet.md" "Paste concrete device evidence into Notes" "run sheet explains high-risk report updates"
require_contains "evidence-notes-template.md" "Replace each \`TODO evidence:\` prompt" "template explains report-note replacement"
require_contains "files/README.md" "seemops_qa_card_pack.json" "packet names card-pack fixture"
require_contains "files/README.md" "seemops_qa_backup.json" "packet names backup fixture"
require_contains "files/README.md" "seemops_qa_transfer_package.json" "packet names transfer fixture"
require_contains "files/README.md" "seed-manual-qa-last-issue.sh" "packet explains last-issue seed helper"

checklist_rows="$(grep -Ec '^\| [0-9]+ \|' "$PACKET_DIR/checklist-index.md" 2>/dev/null || true)"
if (( checklist_rows == 34 )); then
  ok "checklist-index.md contains exactly 34 row(s)"
else
  fail "checklist-index.md contains ${checklist_rows:-0} row(s); expected exactly 34"
fi

high_risk_rows="$(grep -Ec '^\| [0-9]+ \|.*TODO evidence:' "$PACKET_DIR/evidence-notes-template.md" 2>/dev/null || true)"
if (( high_risk_rows == 16 )); then
  ok "evidence-notes-template.md contains exactly 16 high-risk prompt row(s)"
else
  fail "evidence-notes-template.md contains ${high_risk_rows:-0} high-risk prompt row(s); expected exactly 16"
fi

run_sheet_rows="$(grep -Ec '^\| [0-9]+ \|' "$PACKET_DIR/tester-run-sheet.md" 2>/dev/null || true)"
if (( run_sheet_rows == 34 )); then
  ok "tester-run-sheet.md contains exactly 34 row(s)"
else
  fail "tester-run-sheet.md contains ${run_sheet_rows:-0} row(s); expected exactly 34"
fi

run_sheet_high_risk_rows="$(grep -Ec '^\| [0-9]+ \|.*High-risk evidence' "$PACKET_DIR/tester-run-sheet.md" 2>/dev/null || true)"
if (( run_sheet_high_risk_rows == 16 )); then
  ok "tester-run-sheet.md marks exactly 16 high-risk row(s)"
else
  fail "tester-run-sheet.md marks ${run_sheet_high_risk_rows:-0} high-risk row(s); expected exactly 16"
fi

if ! command -v python3 >/dev/null 2>&1; then
  fail "Cannot validate summary.json without python3"
elif summary_output="$(python3 - "$PACKET_DIR/summary.json" "$EXPECTED_FIXTURES_DIR" <<'PY'
import json
import sys

path = sys.argv[1]
expected_fixtures_dir = sys.argv[2]
with open(path, "r", encoding="utf-8") as handle:
    data = json.load(handle)

expected = {
    "checklist_rows": 34,
    "required_rows": 34,
    "high_risk_evidence_rows": 16,
    "run_sheet_rows": 34,
    "run_sheet_high_risk_rows": 16,
}
errors = []
for key, value in expected.items():
    if data.get(key) != value:
        errors.append(f"{key}={data.get(key)!r}, expected {value}")
for key in ("missing_required_rows", "missing_high_risk_rows"):
    if data.get(key) != []:
        errors.append(f"{key} must be an empty array")
if not isinstance(data.get("metadata"), dict):
    errors.append("metadata must be an object")
fixtures_dir = data.get("fixtures_dir")
if not isinstance(fixtures_dir, str) or not fixtures_dir:
    errors.append("fixtures_dir must be a nonempty string")
elif expected_fixtures_dir and fixtures_dir != expected_fixtures_dir:
    errors.append(f"fixtures_dir={fixtures_dir!r}, expected {expected_fixtures_dir!r}")

if errors:
    for error in errors:
        print(error)
    raise SystemExit(1)

print(
    "checklist_rows=34 required_rows=34 high_risk_evidence_rows=16 missing=0 fixtures_dir=%s"
    % fixtures_dir
)
PY
)"; then
  ok "summary.json has exact row counts"
  info "$summary_output"
else
  fail "summary.json validation failed"
  while IFS= read -r line; do
    [[ -n "$line" ]] && info "$line"
  done <<<"$summary_output"
fi

if (( failures > 0 )); then
  printf '\nManual QA evidence packet check failed with %s issue(s).\n' "$failures" >&2
  exit 1
fi

printf '\nManual QA evidence packet check passed.\n'
