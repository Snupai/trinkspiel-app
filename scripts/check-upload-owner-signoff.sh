#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

PACKAGE_DIR=""
REQUIRE_UPLOAD_READY=0

usage() {
  printf '%s\n' "Usage: scripts/check-upload-owner-signoff.sh [--require-upload-ready] [PACKAGE_DIR]"
  printf '%s\n' "Checks top-level Play Store package signals before an upload owner touches Play Console."
  printf '%s\n' "When PACKAGE_DIR is omitted, the latest package under build/play-store-upload is used."
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
  if [[ -s "MANIFEST.md" && -s "OWNER_BRIEF.md" && -d "status" ]]; then
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

manifest_value() {
  local label="$1"
  sed -n "s/^- ${label}:[[:space:]]*//p" "$PACKAGE_DIR/MANIFEST.md" | head -n 1
}

require_file() {
  local file="$1"
  if [[ -s "$PACKAGE_DIR/$file" ]]; then
    ok "$file"
  else
    fail "Missing package file: $file"
  fi
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

owner_brief_contains() {
  local expected="$1"
  local label="$2"
  if grep -Fq -- "$expected" "$PACKAGE_DIR/OWNER_BRIEF.md"; then
    ok "$label"
  else
    fail "OWNER_BRIEF.md is missing expected text: $expected"
  fi
}

blocker_json_field() {
  local field="$1"
  if [[ ! -s "$PACKAGE_DIR/status/release-blockers.json" ]] || ! command -v python3 >/dev/null 2>&1; then
    return 0
  fi
  python3 - "$PACKAGE_DIR/status/release-blockers.json" "$field" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as handle:
    data = json.load(handle)

field = sys.argv[2]
value = data.get(field)
if isinstance(value, bool):
    print("true" if value else "false")
elif value is not None:
    print(value)
PY
}

blocker_status() {
  local id="$1"
  if [[ ! -s "$PACKAGE_DIR/status/release-blockers.json" ]] || ! command -v python3 >/dev/null 2>&1; then
    return 0
  fi
  python3 - "$PACKAGE_DIR/status/release-blockers.json" "$id" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as handle:
    data = json.load(handle)

target = sys.argv[2]
for item in data.get("items", []):
    if isinstance(item, dict) and item.get("id") == target:
        status = item.get("status")
        if isinstance(status, str):
            print(status)
        break
PY
}

printf 'Upload owner signoff check\n'

if [[ -z "$PACKAGE_DIR" || ! -d "$PACKAGE_DIR" ]]; then
  printf '  FAIL    Package directory not found: %s\n' "${PACKAGE_DIR:-missing}" >&2
  exit 1
fi
PACKAGE_DIR="$(cd "$PACKAGE_DIR" && pwd)"
ok "Package directory: $PACKAGE_DIR"

for file in \
  MANIFEST.md \
  OWNER_BRIEF.md \
  status/release-blockers.json \
  status/release-blockers.md \
  status/check-play-store-package.txt \
  status/verify-release-ready.txt \
  android/app-release.aab
do
  require_file "$file"
done

upload_ready_status="$(manifest_value "Upload-ready status")"
package_integrity_status="$(manifest_value "Package integrity status")"
blockers_remaining="$(manifest_value "Blockers remaining")"
package_check_exit="$(manifest_value "Package completeness check exit code")"
preflight_exit="$(manifest_value "Preflight exit code")"
signing_status="$(manifest_value "Release AAB signing status")"
privacy_url="$(manifest_value "Hosted privacy policy URL")"
manual_qa_status="$(manifest_value "Manual QA status")"

if [[ "$package_integrity_status" == "pass" ]]; then
  ok "MANIFEST Package integrity status: pass"
else
  fail "MANIFEST Package integrity status is ${package_integrity_status:-missing}; expected pass"
fi
if [[ "$package_check_exit" == "0" ]]; then
  ok "MANIFEST Package completeness check exit code: 0"
else
  fail "MANIFEST Package completeness check exit code is ${package_check_exit:-missing}; expected 0"
fi
if grep -Fq "Play Store package check passed." "$PACKAGE_DIR/status/check-play-store-package.txt"; then
  ok "status/check-play-store-package.txt reports success"
elif [[ "${SEEMOPS_PACKAGE_SELF_CHECK:-}" == "1" ]]; then
  review "status/check-play-store-package.txt success footer is pending during package self-check"
else
  fail "status/check-play-store-package.txt does not report success"
fi

json_blocker_count="$(blocker_json_field "blocker_count")"
if [[ -n "$json_blocker_count" && "$blockers_remaining" == "$json_blocker_count" ]]; then
  ok "MANIFEST Blockers remaining matches release-blockers.json: $blockers_remaining"
else
  fail "MANIFEST Blockers remaining is ${blockers_remaining:-missing}; release-blockers.json blocker_count is ${json_blocker_count:-missing}"
fi
if grep -Fq "Blockers remaining: ${blockers_remaining}" "$PACKAGE_DIR/status/release-blockers.md"; then
  ok "release-blockers.md agrees on blocker count"
else
  fail "release-blockers.md does not agree on blocker count ${blockers_remaining:-missing}"
fi

owner_brief_contains "Upload-ready status: \`${upload_ready_status}\`" "OWNER_BRIEF mirrors upload-ready status"
owner_brief_contains "Blockers remaining: \`${blockers_remaining}\`" "OWNER_BRIEF mirrors blocker count"
owner_brief_contains "Package integrity status: \`${package_integrity_status}\`" "OWNER_BRIEF mirrors package integrity status"
owner_brief_contains "Do not upload until" "OWNER_BRIEF includes no-upload warning"

for required_ready_id in \
  release_artifacts \
  play_console_handoff \
  connected_android_tests \
  store_screenshots
do
  status="$(blocker_status "$required_ready_id")"
  if [[ "$status" == "ready" ]]; then
    ok "release-blockers.json ${required_ready_id}: ready"
  else
    fail "release-blockers.json ${required_ready_id} is ${status:-missing}; expected ready"
  fi
done

if [[ "$upload_ready_status" == "ready" ]]; then
  ok "MANIFEST Upload-ready status: ready"
else
  review "MANIFEST Upload-ready status is ${upload_ready_status:-missing}"
fi

if [[ "$blockers_remaining" == "0" ]]; then
  ok "MANIFEST Blockers remaining: 0"
else
  review "MANIFEST Blockers remaining: ${blockers_remaining:-missing}"
fi

if [[ "$preflight_exit" == "0" ]]; then
  ok "MANIFEST Preflight exit code: 0"
else
  review "MANIFEST Preflight exit code: ${preflight_exit:-missing}"
fi

if [[ "$signing_status" == "signed" ]]; then
  ok "MANIFEST Release AAB signing status: signed"
else
  review "MANIFEST Release AAB signing status: ${signing_status:-missing}"
fi

if [[ -n "$privacy_url" && "$privacy_url" != "missing" ]]; then
  ok "MANIFEST Hosted privacy policy URL: $privacy_url"
else
  review "MANIFEST Hosted privacy policy URL is missing"
fi

if [[ "$manual_qa_status" == "confirmed" ]]; then
  ok "MANIFEST Manual QA status: confirmed"
else
  review "MANIFEST Manual QA status: ${manual_qa_status:-missing}"
fi

if [[ "$REQUIRE_UPLOAD_READY" == "1" ]]; then
  require_manifest_value "Upload-ready status" "ready"
  require_manifest_value "Blockers remaining" "0"
  require_manifest_value "Preflight exit code" "0"
  require_manifest_value "Release AAB signing status" "signed"
  require_manifest_value "Manual QA status" "confirmed"
  if [[ -z "$privacy_url" || "$privacy_url" == "missing" ]]; then
    fail "MANIFEST Hosted privacy policy URL is missing; expected final URL"
  fi
fi

if (( failures > 0 )); then
  printf '\nUpload owner signoff check failed with %s issue(s).\n' "$failures" >&2
  exit 1
fi

if [[ "$upload_ready_status" == "ready" && "$blockers_remaining" == "0" ]]; then
  printf '\nUpload owner signoff check passed. This package is marked upload-ready.\n'
else
  printf '\nUpload owner signoff check passed. This package is internally consistent but not upload-ready yet.\n'
fi
