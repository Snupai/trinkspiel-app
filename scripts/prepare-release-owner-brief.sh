#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

PACKAGE_DIR=""
OUT_FILE=""

usage() {
  printf '%s\n' "Usage: scripts/prepare-release-owner-brief.sh [--package-dir DIR] [--out FILE]"
  printf '%s\n' "Writes a one-page release owner brief from a generated Play Store package."
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --package-dir)
      PACKAGE_DIR="${2:-}"
      if [[ -z "$PACKAGE_DIR" ]]; then
        usage >&2
        exit 64
      fi
      shift 2
      ;;
    --out)
      OUT_FILE="${2:-}"
      if [[ -z "$OUT_FILE" ]]; then
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

if [[ -z "$PACKAGE_DIR" ]]; then
  if [[ -s "MANIFEST.md" && -s "status/release-blockers.json" ]]; then
    PACKAGE_DIR="."
  else
    PACKAGE_DIR="$(find build/play-store-upload -mindepth 1 -maxdepth 1 -type d -name 'seemops-trinkspiel-*' -print 2>/dev/null | sort | tail -n 1)"
  fi
fi

if [[ -z "$PACKAGE_DIR" || ! -d "$PACKAGE_DIR" ]]; then
  printf 'Release package directory not found. Pass --package-dir DIR.\n' >&2
  exit 1
fi

PACKAGE_DIR="$(cd "$PACKAGE_DIR" && pwd)"
if [[ -z "$OUT_FILE" ]]; then
  OUT_FILE="$PACKAGE_DIR/OWNER_BRIEF.md"
fi

manifest="$PACKAGE_DIR/MANIFEST.md"
blockers_json="$PACKAGE_DIR/status/release-blockers.json"
if [[ ! -s "$manifest" ]]; then
  printf 'Missing required package manifest: %s\n' "$manifest" >&2
  exit 1
fi

manifest_value() {
  local label="$1"
  sed -n "s/^- ${label}:[[:space:]]*//p" "$manifest" | head -n 1
}

value_or_unknown() {
  local value="$1"
  if [[ -n "$value" ]]; then
    printf '%s\n' "$value"
  else
    printf 'unknown\n'
  fi
}

version_name="$(value_or_unknown "$(manifest_value "Version name")")"
version_code="$(value_or_unknown "$(manifest_value "Version code")")"
signing_status="$(value_or_unknown "$(manifest_value "Release AAB signing status")")"
blocker_count="$(value_or_unknown "$(manifest_value "Blockers remaining")")"
package_integrity_status="$(value_or_unknown "$(manifest_value "Package integrity status")")"
upload_ready_status="$(value_or_unknown "$(manifest_value "Upload-ready status")")"
privacy_status="$(value_or_unknown "$(manifest_value "Hosted privacy policy URL")")"
manual_qa_status="$(value_or_unknown "$(manifest_value "Manual QA status")")"
preflight_exit="$(value_or_unknown "$(manifest_value "Preflight exit code")")"
package_check_exit="$(value_or_unknown "$(manifest_value "Package completeness check exit code")")"
upload_owner_signoff_exit="$(value_or_unknown "$(manifest_value "Upload owner signoff check exit code")")"

blockers_markdown="$(mktemp)"
if [[ -s "$blockers_json" && "$(command -v python3 || true)" ]]; then
  python3 - "$blockers_json" > "$blockers_markdown" <<'PY'
import json
import sys

def clean(value):
    if value is None:
        return "missing"
    return " ".join(str(value).split()) or "missing"

try:
    with open(sys.argv[1], "r", encoding="utf-8") as handle:
        data = json.load(handle)
except Exception as exc:
    print(f"- Release blocker details unavailable: {exc}")
    raise SystemExit(0)

items = data.get("items")
if not isinstance(items, list) or not items:
    print("- Release blocker details unavailable: no items in release-blockers.json")
    raise SystemExit(0)

for item in items:
    if not isinstance(item, dict):
        continue
    item_id = clean(item.get("id"))
    status = clean(item.get("status"))
    summary = clean(item.get("summary"))
    next_action = clean(item.get("next_action"))
    print(f"- `{item_id}`: `{status}`")
    print(f"  Summary: {summary}")
    if status != "ready":
        print(f"  Next action: {next_action}")
PY
else
  printf '%s\n' "- Release blocker details unavailable: status/release-blockers.json is missing or python3 is unavailable." > "$blockers_markdown"
fi

mkdir -p "$(dirname "$OUT_FILE")"
{
  printf '# Release Owner Brief\n\n'
  printf 'Generated: %s\n\n' "$(date '+%Y-%m-%d %H:%M:%S %Z')"
  printf 'Package directory: `%s`\n\n' "$PACKAGE_DIR"
  printf '## Current Gate\n\n'
  printf -- '- Version: `%s (%s)`\n' "$version_name" "$version_code"
  printf -- '- Upload-ready status: `%s`\n' "$upload_ready_status"
  printf -- '- Blockers remaining: `%s`\n' "$blocker_count"
  printf -- '- Package integrity status: `%s`\n' "$package_integrity_status"
  printf -- '- Package completeness check exit code: `%s`\n' "$package_check_exit"
  printf -- '- Upload owner signoff check exit code: `%s`\n' "$upload_owner_signoff_exit"
  printf -- '- Release AAB signing status: `%s`\n' "$signing_status"
  printf -- '- Hosted privacy policy URL: `%s`\n' "$privacy_status"
  printf -- '- Manual QA status: `%s`\n' "$manual_qa_status"
  printf -- '- Preflight exit code: `%s`\n\n' "$preflight_exit"
  printf 'Do not upload until `MANIFEST.md` says `Upload-ready status: ready`, `Blockers remaining: 0`, and `Package integrity status: pass`.\n\n'
  printf '## Remaining Work\n\n'
  cat "$blockers_markdown"
  printf '\n## Handoff Map\n\n'
  printf -- '- Signing owner: start with `release-signing-handoff/README.md`, then use `release-signing-handoff/release-signing.env.template` or `release-signing-handoff/release-signing.properties.template`. Current non-secret checker output is in `release-signing-handoff/check-release-signing-config.txt` and `status/check-release-signing-config.txt`.\n'
  printf -- '- Privacy owner: start with `privacy-hosting/README.md`, host `privacy-hosting/privacy-policy.html` or `privacy-hosting/index.html`, then verify the final URL with the evidence in `status/check-privacy-policy-url.txt`.\n'
  printf -- '- Manual QA owner: start with `docs/manual-qa-guide.md`, `manual-qa-evidence/README.md`, and `manual-qa-evidence/tester-run-sheet.md`; use `manual-qa-fixtures/` for import/share checks, and run `tools/seed-manual-qa-last-issue.sh` for the last-issue row. Current QA checker output is in `status/check-manual-qa-report.txt`.\n'
  printf -- '- Final verifier: read `status/verify-release-ready.txt`, then run `bash tools/check-upload-owner-signoff.sh .` and `bash tools/check-play-store-package.sh .` from the package root. For the final upload package, require `--require-upload-ready`.\n\n'
  printf '## Non-Secret Package Note\n\n'
  printf 'This handoff package intentionally contains no keystore and no real signing passwords. Real signing values must stay on the trusted signing machine and be supplied through ignored local files or environment variables.\n'
} > "$OUT_FILE"

rm -f "$blockers_markdown"
printf 'Release owner brief written to %s\n' "$OUT_FILE"
