#!/usr/bin/env bash
set -euo pipefail
shopt -s nullglob

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT_DIR"

if [[ -s "$ROOT_DIR/scripts/privacy-policy-utils.sh" && -s "$ROOT_DIR/scripts/signing-utils.sh" ]]; then
  VERIFIER_TOOL_REF_DIR="scripts"
elif [[ -s "$ROOT_DIR/tools/privacy-policy-utils.sh" && -s "$ROOT_DIR/tools/signing-utils.sh" ]]; then
  VERIFIER_TOOL_REF_DIR="tools"
else
  printf '  FAIL    Missing verifier helper scripts under scripts/ or tools/ in %s\n' "$ROOT_DIR" >&2
  exit 1
fi

source "$ROOT_DIR/$VERIFIER_TOOL_REF_DIR/privacy-policy-utils.sh"
source "$ROOT_DIR/$VERIFIER_TOOL_REF_DIR/signing-utils.sh"

PACKAGE_DIR=""
REQUIRE_UPLOAD_READY=0

usage() {
  printf '%s\n' "Usage: check-play-store-package.sh [--require-upload-ready] [PACKAGE_DIR]"
  printf '%s\n' "Verifies a generated Play Store handoff package. When PACKAGE_DIR is omitted, the latest package under build/play-store-upload is used."
  printf '%s\n' "--require-upload-ready also requires signed AAB, clean final gates, hosted privacy URL, and confirmed manual QA."
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --require-upload-ready)
      REQUIRE_UPLOAD_READY=1
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      if [[ -n "$PACKAGE_DIR" ]]; then
        usage >&2
        exit 64
      fi
      PACKAGE_DIR="$1"
      shift
      ;;
  esac
done

if [[ -z "$PACKAGE_DIR" ]]; then
  if [[ -s "MANIFEST.md" && -s "android/app-release.aab" ]]; then
    PACKAGE_DIR="."
  else
    PACKAGE_DIR="$(find build/play-store-upload -mindepth 1 -maxdepth 1 -type d -name 'seemops-trinkspiel-*' -print 2>/dev/null | sort | tail -n 1)"
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

review() {
  printf '  REVIEW  %s\n' "$1"
}

info() {
  printf '  INFO    %s\n' "$1"
}

require_file() {
  local file="$1"
  if [[ -s "$PACKAGE_DIR/$file" ]]; then
    ok "$file"
  else
    fail "Missing package file: $file"
  fi
}

require_local_file() {
  local file="$1"
  if [[ -s "$file" ]]; then
    ok "local $file"
  else
    fail "Missing local verifier dependency: $file"
  fi
}

require_tool_matches_local() {
  local package_tool="$1"
  local local_tool="$2"

  require_file "$package_tool"
  require_local_file "$local_tool"
  if [[ -s "$PACKAGE_DIR/$package_tool" && -s "$local_tool" ]]; then
    if [[ "$package_tool" == "$local_tool" ]]; then
      ok "$package_tool available in package-local verifier snapshot"
      return
    fi
    if cmp -s "$PACKAGE_DIR/$package_tool" "$local_tool"; then
      ok "$package_tool matches $local_tool"
    else
      fail "$package_tool differs from $local_tool"
    fi
  fi
}

manifest_value() {
  local label="$1"
  sed -n "s/^- ${label}:[[:space:]]*//p" "$PACKAGE_DIR/MANIFEST.md" | head -n 1
}

require_manifest_value() {
  local label="$1"
  local expected="$2"
  local actual
  actual="$(manifest_value "$label")"
  if [[ "$actual" == "$expected" ]]; then
    ok "MANIFEST ${label}: ${actual}"
  else
    fail "MANIFEST ${label} is ${actual:-missing}; expected ${expected}"
  fi
}

require_manifest_not_value() {
  local label="$1"
  local disallowed="$2"
  local actual
  actual="$(manifest_value "$label")"
  if [[ -n "$actual" && "$actual" != "$disallowed" ]]; then
    ok "MANIFEST ${label}: ${actual}"
  else
    fail "MANIFEST ${label} is ${actual:-missing}; expected a final value"
  fi
}

release_blockers_json_count() {
  local file="$PACKAGE_DIR/status/release-blockers.json"
  if [[ ! -s "$file" ]] || ! command -v python3 >/dev/null 2>&1; then
    return 0
  fi

  python3 - "$file" <<'PY'
import json
import sys

try:
    with open(sys.argv[1], "r", encoding="utf-8") as handle:
        data = json.load(handle)
except Exception:
    raise SystemExit(0)

blocker_count = data.get("blocker_count")
if isinstance(blocker_count, int):
    print(blocker_count)
PY
}

blocker_status() {
  local id="$1"
  local file="$PACKAGE_DIR/status/release-blockers.json"
  if [[ ! -s "$file" ]] || ! command -v python3 >/dev/null 2>&1; then
    return 0
  fi

  python3 - "$file" "$id" <<'PY'
import json
import sys

try:
    with open(sys.argv[1], "r", encoding="utf-8") as handle:
        data = json.load(handle)
except Exception:
    raise SystemExit(0)

target_id = sys.argv[2]
items = data.get("items")
if not isinstance(items, list):
    raise SystemExit(0)

for item in items:
    if isinstance(item, dict) and item.get("id") == target_id:
        status = item.get("status")
        if isinstance(status, str):
            print(status)
        break
PY
}

require_blocker_status() {
  local id="$1"
  local expected="$2"
  local actual
  actual="$(blocker_status "$id")"
  if [[ "$actual" == "$expected" ]]; then
    ok "release-blockers.json ${id}: ${actual}"
  else
    fail "release-blockers.json ${id} is ${actual:-missing}; expected ${expected}"
  fi
}

require_status_contains() {
  local file="$1"
  local expected="$2"
  local label="$3"
  if grep -Fq -- "$expected" "$PACKAGE_DIR/$file"; then
    ok "$label"
  else
    fail "$file is missing expected evidence: $expected"
  fi
}

manual_qa_guide_check_count() {
  local file="$1"
  grep -c '^- \[ \]' "$PACKAGE_DIR/$file" 2>/dev/null || true
}

validate_manual_qa_fixtures() {
  local output
  if ! command -v python3 >/dev/null 2>&1; then
    fail "Cannot validate manual QA fixtures without python3"
    return
  fi

  if output="$(python3 - "$PACKAGE_DIR/manual-qa-fixtures" <<'PY'
import json
import pathlib
import sys

fixture_dir = pathlib.Path(sys.argv[1])
paths = {
    "card_pack": fixture_dir / "seemops_qa_card_pack.json",
    "backup": fixture_dir / "seemops_qa_backup.json",
    "transfer": fixture_dir / "seemops_qa_transfer_package.json",
}
errors = []
data = {}
for label, path in paths.items():
    if not path.is_file() or path.stat().st_size == 0:
        errors.append(f"{path.name} is missing or empty")
        continue
    try:
        data[label] = json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        errors.append(f"{path.name} is not valid JSON: {exc}")

card_pack = data.get("card_pack")
if isinstance(card_pack, dict):
    if card_pack.get("version") != 3:
        errors.append("card pack version must be 3")
    if card_pack.get("packName") != "Seemops Manual QA Card Pack":
        errors.append("card pack name mismatch")
    cards = card_pack.get("cards")
    if not isinstance(cards, list) or len(cards) < 2:
        errors.append("card pack must contain at least two cards")
    elif not any(card.get("enabled") is False for card in cards if isinstance(card, dict)):
        errors.append("card pack must include a paused card for activation checks")

backup = data.get("backup")
transfer = data.get("transfer")
if isinstance(backup, dict):
    if backup.get("type") != "seemops.backup":
        errors.append("backup type must be seemops.backup")
    cards = backup.get("cards")
    settings = backup.get("settings")
    if not isinstance(cards, list) or len(cards) < 2:
        errors.append("backup must contain at least two cards")
    if not isinstance(settings, dict):
        errors.append("backup settings must be an object")
    else:
        if settings.get("players") != ["QA Lena", "QA Mika"]:
            errors.append("backup player fixture mismatch")
        if settings.get("firstRunSetupCompleted") is not True:
            errors.append("backup must complete first-run setup for restore checks")
        if settings.get("intensity") != "low":
            errors.append("backup intensity must be low for an obvious restore signal")

if isinstance(backup, dict) and isinstance(transfer, dict) and transfer != backup:
    errors.append("transfer fixture must match the full backup fixture")

if errors:
    for error in errors:
        print(error)
    raise SystemExit(1)

print("card_pack_cards=%d backup_cards=%d transfer_matches_backup=true" % (
    len(card_pack["cards"]),
    len(backup["cards"]),
))
PY
  )"; then
    ok "manual QA fixtures are valid JSON"
    info "manual QA fixtures ${output}"
  else
    fail "manual QA fixtures validation failed"
    while IFS= read -r line; do
      [[ -n "$line" ]] && info "$line"
    done <<<"$output"
  fi
}

validate_manual_qa_evidence_packet() {
  local checklist_rows high_risk_rows run_sheet_rows run_sheet_high_risk_rows summary_output checker_output

  if [[ -x "$VERIFIER_TOOL_REF_DIR/check-manual-qa-evidence-packet.sh" ]]; then
    if checker_output="$(bash "$VERIFIER_TOOL_REF_DIR/check-manual-qa-evidence-packet.sh" --fixtures-dir manual-qa-fixtures "$PACKAGE_DIR/manual-qa-evidence" 2>&1)"; then
      ok "manual QA evidence packet standalone checker passed"
    else
      fail "manual QA evidence packet standalone checker failed"
      while IFS= read -r line; do
        [[ -n "$line" ]] && info "$line"
      done <<<"$checker_output"
    fi
  else
    fail "Missing manual QA evidence packet checker: $VERIFIER_TOOL_REF_DIR/check-manual-qa-evidence-packet.sh"
  fi

  if (cd "$PACKAGE_DIR/manual-qa-evidence" && shasum -a 256 -c checksums.sha256 >/dev/null); then
    ok "manual-qa-evidence/checksums.sha256 verifies"
  else
    fail "manual-qa-evidence/checksums.sha256 verification failed"
  fi

  require_status_contains "manual-qa-evidence/README.md" "does not replace \`docs/MANUAL_QA_REPORT.md\`" "manual QA evidence packet defers to final report"
  require_status_contains "manual-qa-evidence/README.md" "High-risk evidence rows: 16" "manual QA evidence packet records high-risk row count"
  require_status_contains "manual-qa-evidence/README.md" "tester-run-sheet.md" "manual QA evidence packet documents tester run sheet"
  require_status_contains "manual-qa-evidence/README.md" "Keep screenshots/files privacy-safe" "manual QA evidence packet warns against private captures"
  require_status_contains "manual-qa-evidence/tester-run-sheet.md" "Manual QA Tester Run Sheet" "manual QA run sheet has expected title"
  require_status_contains "manual-qa-evidence/tester-run-sheet.md" "Tester action" "manual QA run sheet carries tester actions"
  require_status_contains "manual-qa-evidence/tester-run-sheet.md" "Expected proof" "manual QA run sheet carries expected proof guidance"
  require_status_contains "manual-qa-evidence/tester-run-sheet.md" "Paste concrete device evidence into Notes" "manual QA run sheet explains high-risk report updates"
  require_status_contains "manual-qa-evidence/evidence-notes-template.md" "TODO evidence:" "manual QA evidence template carries evidence prompts"
  require_status_contains "manual-qa-evidence/evidence-notes-template.md" "Replace each \`TODO evidence:\` prompt" "manual QA evidence template explains report-note replacement"
  require_status_contains "manual-qa-evidence/files/README.md" "seemops_qa_card_pack.json" "manual QA evidence packet names card-pack fixture"
  require_status_contains "manual-qa-evidence/files/README.md" "seemops_qa_backup.json" "manual QA evidence packet names backup fixture"
  require_status_contains "manual-qa-evidence/files/README.md" "seemops_qa_transfer_package.json" "manual QA evidence packet names transfer fixture"
  require_status_contains "manual-qa-evidence/files/README.md" "seed-manual-qa-last-issue.sh" "manual QA evidence packet explains last-issue seed helper"

  checklist_rows="$(grep -Ec '^\| [0-9]+ \|' "$PACKAGE_DIR/manual-qa-evidence/checklist-index.md" 2>/dev/null || true)"
  if (( checklist_rows == 34 )); then
    ok "manual QA evidence checklist index contains ${checklist_rows} row(s)"
  else
    fail "manual QA evidence checklist index contains ${checklist_rows:-0} row(s); expected exactly 34"
  fi

  high_risk_rows="$(grep -Ec '^\| [0-9]+ \|.*TODO evidence:' "$PACKAGE_DIR/manual-qa-evidence/evidence-notes-template.md" 2>/dev/null || true)"
  if (( high_risk_rows == 16 )); then
    ok "manual QA evidence template contains ${high_risk_rows} high-risk prompt(s)"
  else
    fail "manual QA evidence template contains ${high_risk_rows:-0} high-risk prompt(s); expected exactly 16"
  fi

  run_sheet_rows="$(grep -Ec '^\| [0-9]+ \|' "$PACKAGE_DIR/manual-qa-evidence/tester-run-sheet.md" 2>/dev/null || true)"
  if (( run_sheet_rows == 34 )); then
    ok "manual QA run sheet contains ${run_sheet_rows} row(s)"
  else
    fail "manual QA run sheet contains ${run_sheet_rows:-0} row(s); expected exactly 34"
  fi

  run_sheet_high_risk_rows="$(grep -Ec '^\| [0-9]+ \|.*High-risk evidence' "$PACKAGE_DIR/manual-qa-evidence/tester-run-sheet.md" 2>/dev/null || true)"
  if (( run_sheet_high_risk_rows == 16 )); then
    ok "manual QA run sheet marks ${run_sheet_high_risk_rows} high-risk row(s)"
  else
    fail "manual QA run sheet marks ${run_sheet_high_risk_rows:-0} high-risk row(s); expected exactly 16"
  fi

  if ! command -v python3 >/dev/null 2>&1; then
    fail "Cannot validate manual QA evidence summary without python3"
    return
  fi

  if summary_output="$(python3 - "$PACKAGE_DIR/manual-qa-evidence/summary.json" <<'PY'
import json
import sys

path = sys.argv[1]
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
metadata = data.get("metadata")
if not isinstance(metadata, dict):
    errors.append("metadata must be an object")
fixtures_dir = data.get("fixtures_dir")
if fixtures_dir != "manual-qa-fixtures":
    errors.append(f"fixtures_dir={fixtures_dir!r}, expected 'manual-qa-fixtures'")

if errors:
    for error in errors:
        print(error)
    raise SystemExit(1)

print("checklist_rows=34 required_rows=34 high_risk_evidence_rows=16 missing=0")
PY
  )"; then
    ok "manual QA evidence summary has exact row counts"
    info "manual QA evidence summary ${summary_output}"
  else
    fail "manual QA evidence summary validation failed"
    while IFS= read -r line; do
      [[ -n "$line" ]] && info "$line"
    done <<<"$summary_output"
  fi
}

validate_privacy_policy_hosting() {
  if cmp -s "$PACKAGE_DIR/privacy-hosting/privacy-policy.html" "$PACKAGE_DIR/docs/privacy-policy.html"; then
    ok "privacy-hosting/privacy-policy.html matches docs/privacy-policy.html"
  else
    fail "privacy-hosting/privacy-policy.html differs from docs/privacy-policy.html"
  fi

  if cmp -s "$PACKAGE_DIR/privacy-hosting/index.html" "$PACKAGE_DIR/docs/privacy-policy.html"; then
    ok "privacy-hosting/index.html matches docs/privacy-policy.html"
  else
    fail "privacy-hosting/index.html differs from docs/privacy-policy.html"
  fi

  if contains_expected_privacy_text "$(cat "$PACKAGE_DIR/privacy-hosting/privacy-policy.html")"; then
    ok "privacy-hosting HTML contains expected Seemops privacy text"
  else
    fail "privacy-hosting HTML is missing expected Seemops privacy text"
  fi

  if (cd "$PACKAGE_DIR/privacy-hosting" && shasum -a 256 -c checksums.sha256 >/dev/null); then
    ok "privacy-hosting/checksums.sha256 verifies"
  else
    fail "privacy-hosting/checksums.sha256 verification failed"
  fi

  require_status_contains "privacy-hosting/README.md" "final hosted response must exactly match" "privacy-hosting README explains exact hosted-content requirement"
  require_status_contains "privacy-hosting/README.md" "scripts/check-privacy-policy-url.sh" "privacy-hosting README explains final URL verification"
}

validate_release_signing_handoff() {
  if (cd "$PACKAGE_DIR/release-signing-handoff" && shasum -a 256 -c checksums.sha256 >/dev/null); then
    ok "release-signing-handoff/checksums.sha256 verifies"
  else
    fail "release-signing-handoff/checksums.sha256 verification failed"
  fi

  require_status_contains "release-signing-handoff/README.md" "contains no keystore and no real signing passwords" "release-signing handoff README states it is non-secret"
  require_status_contains "release-signing-handoff/README.md" "scripts/check-release-signing-config.sh" "release-signing handoff README explains signing validation"
  require_status_contains "release-signing-handoff/README.md" "./gradlew :app:bundleRelease --no-daemon" "release-signing handoff README explains bundle rebuild"
  require_status_contains "release-signing-handoff/check-release-signing-config.txt" "Release signing config" "release-signing handoff includes signing checker output"
  require_status_contains "release-signing-handoff/release-signing.env.template" "replace-with-real-store-password" "release-signing env template keeps placeholder store password"
  require_status_contains "release-signing-handoff/release-signing.properties.template" "replace-with-real-key-password" "release-signing properties template keeps placeholder key password"

  if awk -F= '
    /^SEEMOPS_RELEASE_(STORE|KEY)_PASSWORD=/ {
      if ($2 !~ /^replace-with-real-/) {
        found = 1
      }
    }
    END {
      exit found ? 0 : 1
    }
  ' "$PACKAGE_DIR"/release-signing-handoff/release-signing.*.template; then
    fail "release-signing handoff appears to include a non-placeholder signing password"
  else
    ok "release-signing handoff contains only placeholder signing passwords"
  fi
}

validate_owner_brief() {
  require_status_contains "OWNER_BRIEF.md" "Upload-ready status: \`$(manifest_value "Upload-ready status")\`" "OWNER_BRIEF mirrors manifest upload-ready status"
  require_status_contains "OWNER_BRIEF.md" "Blockers remaining: \`$(manifest_value "Blockers remaining")\`" "OWNER_BRIEF mirrors manifest blocker count"
  require_status_contains "OWNER_BRIEF.md" "Package integrity status: \`$(manifest_value "Package integrity status")\`" "OWNER_BRIEF mirrors manifest package integrity"
  require_status_contains "OWNER_BRIEF.md" "Do not upload until" "OWNER_BRIEF includes no-upload gate"
  require_status_contains "OWNER_BRIEF.md" "release-signing-handoff/README.md" "OWNER_BRIEF points to release signing handoff"
  require_status_contains "OWNER_BRIEF.md" "privacy-hosting/README.md" "OWNER_BRIEF points to privacy hosting handoff"
  require_status_contains "OWNER_BRIEF.md" "docs/manual-qa-guide.md" "OWNER_BRIEF points to manual QA guide"
  require_status_contains "OWNER_BRIEF.md" "manual-qa-evidence/README.md" "OWNER_BRIEF points to manual QA evidence packet"
  require_status_contains "OWNER_BRIEF.md" "manual-qa-fixtures/" "OWNER_BRIEF points to manual QA fixtures"
  require_status_contains "OWNER_BRIEF.md" "tools/check-upload-owner-signoff.sh" "OWNER_BRIEF points to upload owner signoff checker"
  require_status_contains "OWNER_BRIEF.md" "tools/check-play-store-package.sh" "OWNER_BRIEF points to package-local verifier"
  require_status_contains "OWNER_BRIEF.md" "contains no keystore and no real signing passwords" "OWNER_BRIEF states signing handoff is non-secret"
}

validate_upload_owner_signoff() {
  local output
  if [[ -x "$VERIFIER_TOOL_REF_DIR/check-upload-owner-signoff.sh" ]]; then
    if output="$(bash "$VERIFIER_TOOL_REF_DIR/check-upload-owner-signoff.sh" "$PACKAGE_DIR" 2>&1)"; then
      ok "upload owner signoff checker passed"
      if grep -Fq "internally consistent but not upload-ready yet" <<<"$output"; then
        review "upload owner signoff marks package internally consistent but not upload-ready"
      elif grep -Fq "This package is marked upload-ready." <<<"$output"; then
        ok "upload owner signoff marks package upload-ready"
      fi
    else
      fail "upload owner signoff checker failed"
      while IFS= read -r line; do
        [[ -n "$line" ]] && info "$line"
      done <<<"$output"
    fi
  else
    fail "Missing upload owner signoff checker: $VERIFIER_TOOL_REF_DIR/check-upload-owner-signoff.sh"
  fi

  if [[ "${SEEMOPS_PACKAGE_SELF_CHECK:-}" == "1" &&
    -s "$PACKAGE_DIR/status/check-upload-owner-signoff.txt" &&
    "$(head -n 1 "$PACKAGE_DIR/status/check-upload-owner-signoff.txt")" == "Upload owner signoff pending until package self-check completes." ]]; then
    review "status/check-upload-owner-signoff.txt is pending during package self-check"
  else
    require_status_contains "status/check-upload-owner-signoff.txt" "Upload owner signoff check passed." "upload owner signoff status reports success"
    if grep -Fq "internally consistent but not upload-ready yet" "$PACKAGE_DIR/status/check-upload-owner-signoff.txt"; then
      review "upload owner signoff status marks package internally consistent but not upload-ready"
    elif grep -Fq "This package is marked upload-ready." "$PACKAGE_DIR/status/check-upload-owner-signoff.txt"; then
      ok "upload owner signoff status marks package upload-ready"
    else
      fail "status/check-upload-owner-signoff.txt does not include a final upload-ready/not-ready decision"
    fi
  fi
}

validate_release_blockers_json() {
  local file="status/release-blockers.json"
  local output

  if [[ ! -s "$PACKAGE_DIR/$file" ]]; then
    fail "Missing package file: $file"
    return
  fi

  if ! command -v python3 >/dev/null 2>&1; then
    fail "Cannot validate $file structure without python3"
    return
  fi

  if output="$(python3 - "$PACKAGE_DIR/$file" <<'PY'
import json
import sys

expected_ids = [
    "release_artifacts",
    "release_signing",
    "privacy_policy_url",
    "play_console_handoff",
    "manual_qa",
    "connected_android_tests",
    "store_screenshots",
]
path = sys.argv[1]
try:
    with open(path, "r", encoding="utf-8") as handle:
        data = json.load(handle)
except Exception as exc:
    print(f"JSON parse error: {exc}")
    raise SystemExit(1)

errors = []
if not isinstance(data, dict):
    errors.append("root must be an object")
items = data.get("items")
if not isinstance(items, list):
    errors.append("items must be an array")
    items = []

seen_ids = []
blocked = 0
for index, item in enumerate(items):
    if not isinstance(item, dict):
        errors.append(f"items[{index}] must be an object")
        continue
    item_id = item.get("id")
    status = item.get("status")
    seen_ids.append(item_id)
    if item_id not in expected_ids:
        errors.append(f"unexpected item id: {item_id!r}")
    if status not in ("ready", "blocked"):
        errors.append(f"{item_id or f'items[{index}]'} has invalid status: {status!r}")
    elif status != "ready":
        blocked += 1
    for field in ("summary", "next_action"):
        value = item.get(field)
        if not isinstance(value, str) or not value.strip():
            errors.append(f"{item_id or f'items[{index}]'} has empty {field}")

missing_ids = [item_id for item_id in expected_ids if item_id not in seen_ids]
duplicate_ids = sorted({item_id for item_id in seen_ids if seen_ids.count(item_id) > 1})
if missing_ids:
    errors.append("missing item ids: " + ", ".join(missing_ids))
if duplicate_ids:
    errors.append("duplicate item ids: " + ", ".join(str(item_id) for item_id in duplicate_ids))

blocker_count = data.get("blocker_count")
if not isinstance(blocker_count, int):
    errors.append("blocker_count must be an integer")
elif blocker_count != blocked:
    errors.append(f"blocker_count {blocker_count} does not match non-ready item count {blocked}")

generated_at = data.get("generated_at")
if not isinstance(generated_at, str) or not generated_at.strip():
    errors.append("generated_at must be a nonempty string")

if errors:
    for error in errors:
        print(error)
    raise SystemExit(1)

print(f"items={len(items)} blocked={blocked} blocker_count={blocker_count}")
PY
  )"; then
    ok "release-blockers.json is valid structured JSON"
    info "release-blockers.json ${output}"
  else
    fail "release-blockers.json structured validation failed"
    while IFS= read -r line; do
      [[ -n "$line" ]] && info "$line"
    done <<<"$output"
  fi
}

release_blockers_markdown_count() {
  sed -n 's/^Blockers remaining:[[:space:]]*//p' "$PACKAGE_DIR/status/release-blockers.md" | head -n 1
}

release_blockers_markdown_status() {
  local id="$1"
  sed -n "s/^- ${id}:[[:space:]]*//p" "$PACKAGE_DIR/status/release-blockers.md" | head -n 1
}

validate_release_blockers_markdown() {
  local json_count markdown_count id json_status markdown_status

  if [[ ! -s "$PACKAGE_DIR/status/release-blockers.md" ]]; then
    fail "Missing package file: status/release-blockers.md"
    return
  fi
  if [[ ! -s "$PACKAGE_DIR/status/release-blockers.json" ]]; then
    fail "Missing package file: status/release-blockers.json"
    return
  fi

  json_count="$(release_blockers_json_count)"
  markdown_count="$(release_blockers_markdown_count)"
  if [[ -n "$json_count" && "$markdown_count" == "$json_count" ]]; then
    ok "release-blockers.md Blockers remaining matches JSON: $markdown_count"
  else
    fail "release-blockers.md Blockers remaining is ${markdown_count:-missing}; expected JSON blocker_count ${json_count:-missing}"
  fi

  for id in \
    release_artifacts \
    release_signing \
    privacy_policy_url \
    play_console_handoff \
    manual_qa \
    connected_android_tests \
    store_screenshots
  do
    json_status="$(blocker_status "$id")"
    markdown_status="$(release_blockers_markdown_status "$id")"
    if [[ -n "$json_status" && "$markdown_status" == "$json_status" ]]; then
      ok "release-blockers.md ${id}: ${markdown_status}"
    else
      fail "release-blockers.md ${id} is ${markdown_status:-missing}; expected JSON status ${json_status:-missing}"
    fi
  done
}

require_dimensions() {
  local file="$1"
  local expected_width="$2"
  local expected_height="$3"
  local actual_width actual_height

  if [[ ! -s "$PACKAGE_DIR/$file" ]]; then
    fail "Missing image: $file"
    return
  fi
  if ! command -v sips >/dev/null 2>&1; then
    review "Cannot verify image dimensions without sips: $file"
    return
  fi

  actual_width="$(sips -g pixelWidth "$PACKAGE_DIR/$file" 2>/dev/null | awk '/pixelWidth/ { print $2 }')"
  actual_height="$(sips -g pixelHeight "$PACKAGE_DIR/$file" 2>/dev/null | awk '/pixelHeight/ { print $2 }')"
  if [[ "$actual_width" == "$expected_width" && "$actual_height" == "$expected_height" ]]; then
    ok "$file (${actual_width}x${actual_height})"
  else
    fail "$file has ${actual_width}x${actual_height}; expected ${expected_width}x${expected_height}"
  fi
}

checksum_path_is_ignored() {
  local path="$1"
  local package_check_exit

  if [[ "$path" == "./checksums.sha256" ]]; then
    return 0
  fi

  if [[ "${SEEMOPS_PACKAGE_SELF_CHECK:-}" == "1" && "$path" == "./status/check-play-store-package.txt" ]]; then
    return 0
  fi

  package_check_exit="$(manifest_value "Package completeness check exit code")"
  if [[ "$package_check_exit" == "pending" && "$path" == "./status/check-play-store-package.txt" ]]; then
    return 0
  fi

  return 1
}

check_checksum_coverage() {
  local expected_file actual_file missing_file unexpected_file
  expected_file="$(mktemp)"
  actual_file="$(mktemp)"

  (
    cd "$PACKAGE_DIR"
    while IFS= read -r file; do
      if ! checksum_path_is_ignored "$file"; then
        printf '%s\n' "$file"
      fi
    done < <(find . -type f -print | sort)
  ) > "$expected_file"

  while IFS= read -r file; do
    if ! checksum_path_is_ignored "$file"; then
      printf '%s\n' "$file"
    fi
  done < <(awk '{ print $2 }' "$PACKAGE_DIR/checksums.sha256" | sort) > "$actual_file"

  missing_file="$(comm -23 "$expected_file" "$actual_file" | head -n 1)"
  unexpected_file="$(comm -13 "$expected_file" "$actual_file" | head -n 1)"

  rm -f "$expected_file" "$actual_file"

  if [[ -n "$missing_file" ]]; then
    fail "checksums.sha256 is missing package file: $missing_file"
  elif [[ -n "$unexpected_file" ]]; then
    fail "checksums.sha256 references an unexpected file: $unexpected_file"
  else
    ok "checksums.sha256 covers package files"
  fi
}

printf 'Play Store package check\n'

if [[ -z "$PACKAGE_DIR" || ! -d "$PACKAGE_DIR" ]]; then
  printf '  FAIL    Package directory not found: %s\n' "${PACKAGE_DIR:-missing}" >&2
  exit 1
fi
PACKAGE_DIR="$(cd "$PACKAGE_DIR" && pwd)"
ok "Package directory: $PACKAGE_DIR"
require_local_file "$VERIFIER_TOOL_REF_DIR/privacy-policy-utils.sh"
require_local_file "$VERIFIER_TOOL_REF_DIR/signing-utils.sh"

for file in \
  MANIFEST.md \
  OWNER_BRIEF.md \
  checksums.sha256 \
  android/app-release.aab \
  android/app-debug.apk \
  android/app-debug-androidTest.apk \
  privacy-policy.html \
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
  docs/manual-qa-guide.md \
  docs/FINAL_RELEASE_RUNBOOK.md \
  docs/RELEASE_AUDIT.md \
  docs/RELEASE_NOTES.md \
  manual-qa-evidence/README.md \
  manual-qa-evidence/tester-run-sheet.md \
  manual-qa-evidence/evidence-notes-template.md \
  manual-qa-evidence/checklist-index.md \
  manual-qa-evidence/screenshots/README.md \
  manual-qa-evidence/files/README.md \
  manual-qa-evidence/summary.json \
  manual-qa-evidence/checksums.sha256 \
  status/release-status.txt \
  status/check-release-signing-config.txt \
  status/check-privacy-policy-url.txt \
  status/check-play-console-handoff.txt \
  status/check-manual-qa-report.txt \
  status/check-play-store-package.txt \
  status/check-upload-owner-signoff.txt \
  status/release-blockers.md \
  status/release-blockers.json \
  status/verify-release-ready.txt \
  tools/check-play-store-package.sh \
	  tools/check-manual-qa-evidence-packet.sh \
	  tools/check-manual-qa-report.sh \
	  tools/check-store-screenshot-polish.sh \
	  tools/check-upload-owner-signoff.sh \
	  tools/generate-manual-qa-fixtures.sh \
	  tools/prepare-manual-qa-evidence-packet.sh \
	  tools/prepare-release-owner-brief.sh \
	  tools/prepare-privacy-policy-hosting.sh \
	  tools/prepare-release-signing-handoff.sh \
	  tools/privacy-policy-utils.sh \
	  tools/seed-manual-qa-last-issue.sh \
	  tools/signing-utils.sh \
	  manual-qa-fixtures/seemops_qa_card_pack.json \
	  manual-qa-fixtures/seemops_qa_backup.json \
	  manual-qa-fixtures/seemops_qa_transfer_package.json \
	  privacy-hosting/privacy-policy.html \
	  privacy-hosting/index.html \
	  privacy-hosting/README.md \
	  privacy-hosting/checksums.sha256 \
	  release-signing-handoff/README.md \
	  release-signing-handoff/check-release-signing-config.txt \
	  release-signing-handoff/release-signing.env.template \
	  release-signing-handoff/release-signing.properties.template \
	  release-signing-handoff/checksums.sha256 \
	  store-assets/feature-graphic.png \
  store-assets/store-icon.png
do
  require_file "$file"
done
require_tool_matches_local "tools/check-play-store-package.sh" "$VERIFIER_TOOL_REF_DIR/check-play-store-package.sh"
require_tool_matches_local "tools/check-manual-qa-evidence-packet.sh" "$VERIFIER_TOOL_REF_DIR/check-manual-qa-evidence-packet.sh"
require_tool_matches_local "tools/check-manual-qa-report.sh" "$VERIFIER_TOOL_REF_DIR/check-manual-qa-report.sh"
require_tool_matches_local "tools/check-store-screenshot-polish.sh" "$VERIFIER_TOOL_REF_DIR/check-store-screenshot-polish.sh"
require_tool_matches_local "tools/check-upload-owner-signoff.sh" "$VERIFIER_TOOL_REF_DIR/check-upload-owner-signoff.sh"
require_tool_matches_local "tools/generate-manual-qa-fixtures.sh" "$VERIFIER_TOOL_REF_DIR/generate-manual-qa-fixtures.sh"
require_tool_matches_local "tools/prepare-manual-qa-evidence-packet.sh" "$VERIFIER_TOOL_REF_DIR/prepare-manual-qa-evidence-packet.sh"
require_tool_matches_local "tools/prepare-release-owner-brief.sh" "$VERIFIER_TOOL_REF_DIR/prepare-release-owner-brief.sh"
require_tool_matches_local "tools/prepare-privacy-policy-hosting.sh" "$VERIFIER_TOOL_REF_DIR/prepare-privacy-policy-hosting.sh"
require_tool_matches_local "tools/prepare-release-signing-handoff.sh" "$VERIFIER_TOOL_REF_DIR/prepare-release-signing-handoff.sh"
require_tool_matches_local "tools/privacy-policy-utils.sh" "$VERIFIER_TOOL_REF_DIR/privacy-policy-utils.sh"
require_tool_matches_local "tools/seed-manual-qa-last-issue.sh" "$VERIFIER_TOOL_REF_DIR/seed-manual-qa-last-issue.sh"
require_tool_matches_local "tools/signing-utils.sh" "$VERIFIER_TOOL_REF_DIR/signing-utils.sh"

release_apks=("$PACKAGE_DIR"/android/release-apks/*.apk)
if (( ${#release_apks[@]} > 0 )); then
  ok "android/release-apks (${#release_apks[@]} file(s))"
else
  fail "Missing release APKs under android/release-apks/*.apk"
fi

screenshots=("$PACKAGE_DIR"/store-assets/screenshots/phone/*.png)
if (( ${#screenshots[@]} >= 6 )); then
  ok "phone screenshots (${#screenshots[@]} file(s))"
else
  fail "Expected at least 6 phone screenshots; found ${#screenshots[@]}"
fi

if [[ -s "$PACKAGE_DIR/checksums.sha256" ]]; then
  if (cd "$PACKAGE_DIR" && shasum -a 256 -c checksums.sha256 >/dev/null); then
    ok "checksums.sha256 verifies"
  else
    fail "checksums.sha256 verification failed"
  fi
  check_checksum_coverage
fi

validate_release_blockers_json
validate_release_blockers_markdown
validate_manual_qa_fixtures
validate_manual_qa_evidence_packet
validate_privacy_policy_hosting
validate_release_signing_handoff
validate_owner_brief
validate_upload_owner_signoff

if cmp -s "$PACKAGE_DIR/privacy-policy.html" "$PACKAGE_DIR/docs/privacy-policy.html"; then
  ok "Root privacy-policy.html matches docs/privacy-policy.html"
else
  fail "Root privacy-policy.html differs from docs/privacy-policy.html"
fi
if contains_expected_privacy_text "$(cat "$PACKAGE_DIR/docs/privacy-policy.html")"; then
  ok "Package privacy-policy HTML contains expected Seemops privacy text"
else
  fail "Package privacy-policy HTML is missing expected Seemops privacy text"
fi

require_dimensions "store-assets/feature-graphic.png" 1024 500
require_dimensions "store-assets/store-icon.png" 512 512
PACKAGE_SCREENSHOT_FILES=(
  store-assets/screenshots/phone/01-first-run-setup.png
  store-assets/screenshots/phone/02-game-ready.png
  store-assets/screenshots/phone/03-card-drawn.png
  store-assets/screenshots/phone/04-entry-manager.png
  store-assets/screenshots/phone/05-settings-diagnostics.png
  store-assets/screenshots/phone/06-settings-legal.png
)
for screenshot in "${PACKAGE_SCREENSHOT_FILES[@]}"; do
  require_dimensions "$screenshot" 1080 2400
done
PACKAGE_SCREENSHOT_PATHS=()
for screenshot in "${PACKAGE_SCREENSHOT_FILES[@]}"; do
  PACKAGE_SCREENSHOT_PATHS+=("$PACKAGE_DIR/$screenshot")
done
if bash "$VERIFIER_TOOL_REF_DIR/check-store-screenshot-polish.sh" "${PACKAGE_SCREENSHOT_PATHS[@]}"; then
  ok "package screenshots have clean demo-mode status bars"
else
  fail "Package screenshots need clean demo-mode status bars"
fi

manifest_signing_status="$(manifest_value "Release AAB signing status")"
actual_signing_status="$(release_aab_signing_status "$PACKAGE_DIR/android/app-release.aab")"
if [[ "$manifest_signing_status" == "$actual_signing_status" ]]; then
  ok "MANIFEST signing status matches AAB: $actual_signing_status"
else
  fail "MANIFEST signing status is ${manifest_signing_status:-missing}, but AAB appears ${actual_signing_status}"
fi

blocker_count="$(release_blockers_json_count)"
if [[ -n "$blocker_count" ]]; then
  require_manifest_value "Blockers remaining" "$blocker_count"
  if [[ "$blocker_count" == "0" ]]; then
    ok "release-blockers.json blocker_count is 0"
  else
    review "release-blockers.json blocker_count is ${blocker_count}"
  fi
else
  fail "Could not read blocker_count from status/release-blockers.json"
fi
for required_blocker_id in \
  release_artifacts \
  release_signing \
  privacy_policy_url \
  play_console_handoff \
  manual_qa \
  connected_android_tests \
  store_screenshots
do
  if [[ -n "$(blocker_status "$required_blocker_id")" ]]; then
    ok "release-blockers.json contains ${required_blocker_id}"
  else
    fail "release-blockers.json is missing ${required_blocker_id}"
  fi
done
require_blocker_status "release_artifacts" "ready"
require_blocker_status "play_console_handoff" "ready"
require_blocker_status "connected_android_tests" "ready"
require_blocker_status "store_screenshots" "ready"
require_manifest_value "Release blocker report exit code" "0"
require_manifest_value "Release blocker JSON exit code" "0"
require_manifest_value "Release artifacts status" "ready"
require_status_contains "status/check-privacy-policy-url.txt" "Local privacy-policy HTML contains expected Seemops privacy text" "privacy checker confirms local hosted-page content"
require_status_contains "status/check-privacy-policy-url.txt" "Local privacy-policy SHA-256:" "privacy checker records local privacy-policy fingerprint"
require_status_contains "status/check-manual-qa-report.txt" "Checklist rows found: 34" "manual QA checker sees expanded checklist"
require_status_contains "status/check-manual-qa-report.txt" "Required checklist checks present: 34" "manual QA checker confirms required checklist content"
require_status_contains "status/check-manual-qa-report.txt" "High-risk evidence-note rows present: 16" "manual QA checker requires evidence notes for high-risk rows"
require_status_contains "status/check-manual-qa-report.txt" "High-risk Passed rows include concrete Notes evidence" "manual QA checker rejects silent high-risk passes"
manual_qa_guide_count="$(manual_qa_guide_check_count "docs/manual-qa-guide.md")"
if (( manual_qa_guide_count >= 34 )); then
  ok "manual QA guide contains ${manual_qa_guide_count} checklist item(s)"
else
  fail "manual QA guide contains ${manual_qa_guide_count:-0} checklist item(s); expected at least 34"
fi
require_status_contains "docs/manual-qa-guide.md" "- App version: $(manifest_value "Version name") ($(manifest_value "Version code"))" "manual QA guide matches package app version"
require_status_contains "docs/manual-qa-guide.md" "Android chooser/share sheet" "manual QA guide calls out Android share-sheet checks"
require_status_contains "docs/manual-qa-guide.md" "Android file picker" "manual QA guide calls out Android file-picker checks"
require_status_contains "docs/manual-qa-guide.md" "TODO evidence:" "manual QA guide carries high-risk evidence-note prompts"
require_status_contains "docs/manual-qa-guide.md" "from the generated package root" "manual QA guide points at package-root fixtures"
if grep -Fq "docs/fixtures" "$PACKAGE_DIR/docs/manual-qa-guide.md"; then
  fail "manual QA guide references missing docs/fixtures path"
else
  ok "manual QA guide does not reference docs/fixtures"
fi
require_status_contains "docs/manual-qa-guide.md" "seemops_qa_card_pack.json" "manual QA guide names card-pack import fixture"
require_status_contains "docs/manual-qa-guide.md" "seemops_qa_backup.json" "manual QA guide names backup import fixture"
require_status_contains "docs/manual-qa-guide.md" "seemops_qa_transfer_package.json" "manual QA guide names transfer import fixture"
require_status_contains "docs/manual-qa-guide.md" "seed-manual-qa-last-issue.sh" "manual QA guide explains last-issue seeding helper"
require_status_contains "docs/manual-qa-guide.md" "Accessibility: large font scale, TalkBack labels, touch targets, and light/dark contrast." "manual QA guide calls out accessibility checks"
require_status_contains "status/verify-release-ready.txt" "debug APK are current with app build inputs" "preflight confirms debug APK freshness"
require_status_contains "status/verify-release-ready.txt" "release APK are current with app build inputs" "preflight confirms release APK freshness"
require_status_contains "status/verify-release-ready.txt" "release AAB are current with app build inputs" "preflight confirms release AAB freshness"
require_status_contains "status/verify-release-ready.txt" "Android test APK are current with app build inputs" "preflight confirms Android test APK freshness"

if grep -Fq "android/release-apks/*.apk" "$PACKAGE_DIR/MANIFEST.md"; then
  ok "MANIFEST references flexible release APK path"
else
  fail "MANIFEST does not reference android/release-apks/*.apk"
fi
if grep -Fq "tools/check-play-store-package.sh" "$PACKAGE_DIR/MANIFEST.md"; then
  ok "MANIFEST references verifier tools"
else
  fail "MANIFEST does not reference verifier tools"
fi
if grep -Fq "manual-qa-fixtures/seemops_qa_card_pack.json" "$PACKAGE_DIR/MANIFEST.md"; then
  ok "MANIFEST references manual QA fixtures"
else
  fail "MANIFEST does not reference manual QA fixtures"
fi
if grep -Fq "manual-qa-evidence/README.md" "$PACKAGE_DIR/MANIFEST.md"; then
  ok "MANIFEST references manual QA evidence packet"
else
  fail "MANIFEST does not reference manual QA evidence packet"
fi
if grep -Fq "privacy-hosting/privacy-policy.html" "$PACKAGE_DIR/MANIFEST.md"; then
  ok "MANIFEST references privacy hosting bundle"
else
  fail "MANIFEST does not reference privacy hosting bundle"
fi
if grep -Fq "release-signing-handoff/README.md" "$PACKAGE_DIR/MANIFEST.md"; then
  ok "MANIFEST references release signing handoff"
else
  fail "MANIFEST does not reference release signing handoff"
fi
if grep -Fq "OWNER_BRIEF.md" "$PACKAGE_DIR/MANIFEST.md"; then
  ok "MANIFEST references owner brief"
else
  fail "MANIFEST does not reference owner brief"
fi

if [[ "$REQUIRE_UPLOAD_READY" == "1" ]]; then
  require_manifest_value "Release AAB signing status" "signed"
  require_manifest_value "Signing config check exit code" "0"
  require_status_contains "status/check-release-signing-config.txt" "Configured key password can sign a temporary verification JAR" "signing checker confirms key password can sign"
  require_manifest_value "Privacy policy check exit code" "0"
  require_manifest_value "Play Console handoff check exit code" "0"
  require_manifest_value "Manual QA report check exit code" "0"
  require_manifest_value "Package completeness check exit code" "0"
  require_manifest_value "Upload owner signoff check exit code" "0"
  require_manifest_value "Package integrity status" "pass"
  require_manifest_value "Upload-ready status" "ready"
  require_manifest_value "Blockers remaining" "0"
  require_manifest_value "Preflight exit code" "0"
  require_manifest_value "Manual QA status" "confirmed"
  require_manifest_not_value "Hosted privacy policy URL" "missing"
  require_status_contains "status/check-privacy-policy-url.txt" "Hosted privacy-policy content exactly matches docs/privacy-policy.html" "privacy checker confirms exact hosted privacy-policy content"
  if [[ "$blocker_count" != "0" ]]; then
    fail "release-blockers.json blocker_count is ${blocker_count:-missing}; expected 0"
  fi
else
  package_integrity_status="$(manifest_value "Package integrity status")"
  upload_ready_status="$(manifest_value "Upload-ready status")"
  upload_owner_signoff_exit="$(manifest_value "Upload owner signoff check exit code")"
  if [[ "$package_integrity_status" == "pass" ]]; then
    ok "MANIFEST package integrity status is pass"
  elif [[ "$package_integrity_status" == "pending" ]]; then
    review "MANIFEST package integrity status is pending"
  elif [[ -n "$package_integrity_status" ]]; then
    fail "MANIFEST package integrity status is ${package_integrity_status}"
  fi

  if [[ "$upload_owner_signoff_exit" == "0" ]]; then
    ok "MANIFEST upload owner signoff check exit code is 0"
  elif [[ "${SEEMOPS_PACKAGE_SELF_CHECK:-}" == "1" && "$upload_owner_signoff_exit" == "pending" ]]; then
    review "MANIFEST upload owner signoff check exit code is pending during package self-check"
  else
    fail "MANIFEST upload owner signoff check exit code is ${upload_owner_signoff_exit:-missing}; expected 0"
  fi

  if [[ "$upload_ready_status" == "ready" &&
    "$(manifest_value "Preflight exit code")" == "0" &&
    "$manifest_signing_status" == "signed" &&
    "$(manifest_value "Manual QA status")" == "confirmed" &&
    "$(manifest_value "Hosted privacy policy URL")" != "missing" ]]; then
    ok "MANIFEST indicates upload-ready status"
  else
    review "MANIFEST indicates this package is not upload-ready yet"
  fi
fi

if (( failures > 0 )); then
  printf '\nPlay Store package check failed with %s issue(s).\n' "$failures" >&2
  exit 1
fi

printf '\nPlay Store package check passed.\n'
