#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

RUN_BUILD=1
CAPTURE_SCREENSHOTS=0
OUT_ROOT="build/play-store-upload"

usage() {
  printf '%s\n' "Usage: scripts/finalize-play-store-release.sh [--skip-build] [--capture-screenshots] [--out DIR]"
  printf '%s\n' "Runs the final Play Store sequence, packages evidence, and exits 0 only when upload-ready."
  printf '%s\n' "Requires release signing inputs, SEEMOPS_PRIVACY_POLICY_URL, and SEEMOPS_MANUAL_QA_CONFIRMED=1 for success."
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-build)
      RUN_BUILD=0
      shift
      ;;
    --capture-screenshots)
      CAPTURE_SCREENSHOTS=1
      shift
      ;;
    --out)
      OUT_ROOT="${2:-}"
      if [[ -z "$OUT_ROOT" ]]; then
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

section() {
  printf '\n%s\n' "$1"
}

run_build_tasks() {
  ./gradlew \
    :app:testDebugUnitTest \
    :app:lintDebug \
    :app:assembleDebug \
    :app:assembleRelease \
    :app:assembleDebugAndroidTest \
    :app:bundleRelease \
    --no-daemon
}

release_blocker_status() {
  local file="$1"
  local id="$2"
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

signing_exit=0
privacy_exit=0
screenshot_exit=0
build_exit=0
preflight_exit=0
package_exit=0
package_check_exit=0
package_dir=""

section "Release signing inputs"
set +e
scripts/check-release-signing-config.sh
signing_exit="$?"
set -e

section "Privacy policy URL"
set +e
scripts/check-privacy-policy-url.sh
privacy_exit="$?"
set -e

if [[ "$CAPTURE_SCREENSHOTS" == "1" ]]; then
  section "Store screenshot capture"
  set +e
  scripts/capture-store-screenshots.sh
  screenshot_exit="$?"
  set -e
else
  printf '\nStore screenshot capture\n'
  printf '  SKIP    not requested; preflight still checks screenshot freshness\n'
fi

if [[ "$RUN_BUILD" == "1" ]]; then
  section "Release build and tests"
  set +e
  run_build_tasks
  build_exit="$?"
  set -e
else
  section "Release build and tests"
  printf '  SKIP    using existing build outputs\n'
fi

if [[ "$build_exit" == "0" ]]; then
  section "Release preflight"
  set +e
  scripts/verify-release-ready.sh --skip-build
  preflight_exit="$?"
  set -e

  section "Play Store package"
  set +e
  package_output="$(scripts/prepare-play-store-upload.sh --skip-build --out "$OUT_ROOT" 2>&1)"
  package_exit="$?"
  set -e
  printf '%s\n' "$package_output"
  package_dir="$(sed -n 's/^Play Store package written to //p' <<<"$package_output" | tail -n 1)"
  if [[ -n "$package_dir" ]]; then
    section "Play Store package check"
    set +e
    scripts/check-play-store-package.sh "$package_dir"
    package_check_exit="$?"
    set -e
  fi
else
  preflight_exit=1
  package_exit=1
  package_check_exit=1
  section "Release preflight"
  printf '  SKIP    build failed; not checking stale artifacts\n'
  section "Play Store package"
  printf '  SKIP    build failed; not packaging stale artifacts\n'
fi

section "Release blocker report"
set +e
scripts/release-blockers.sh --no-fail
blocker_report_exit="$?"
set -e

section "Final release summary"
printf '  signing config:   %s\n' "$signing_exit"
printf '  privacy URL:      %s\n' "$privacy_exit"
printf '  screenshots:      %s\n' "$screenshot_exit"
printf '  build/tests:      %s\n' "$build_exit"
printf '  preflight:        %s\n' "$preflight_exit"
printf '  package:          %s\n' "$package_exit"
printf '  package check:    %s\n' "$package_check_exit"
if [[ -n "$package_dir" ]]; then
  printf '  package folder:   %s\n' "$package_dir"
fi

if [[ "$signing_exit" == "0" &&
  "$privacy_exit" == "0" &&
  "$screenshot_exit" == "0" &&
  "$build_exit" == "0" &&
  "$preflight_exit" == "0" &&
  "$package_exit" == "0" &&
  "$package_check_exit" == "0" ]]; then
  printf '\nFinal release package is upload-ready.\n'
  exit 0
fi

printf '\nFinal release is not upload-ready yet.\n' >&2
if [[ "$signing_exit" != "0" ]]; then
  printf '  ACTION  Configure release signing and rerun scripts/check-release-signing-config.sh.\n' >&2
fi
if [[ -z "${SEEMOPS_PRIVACY_POLICY_URL:-}" ]]; then
  printf '  ACTION  Set SEEMOPS_PRIVACY_POLICY_URL to the final public HTTPS privacy-policy URL.\n' >&2
fi
if [[ "$privacy_exit" != "0" ]]; then
  printf '  ACTION  Host docs/privacy-policy.html and rerun scripts/check-privacy-policy-url.sh.\n' >&2
fi
if [[ "${SEEMOPS_MANUAL_QA_CONFIRMED:-}" != "1" ]]; then
  printf '  ACTION  Complete docs/MANUAL_QA_REPORT.md and set SEEMOPS_MANUAL_QA_CONFIRMED=1.\n' >&2
fi
if [[ -n "$package_dir" &&
  "$(release_blocker_status "$package_dir/status/release-blockers.json" connected_android_tests)" == "blocked" ]]; then
  printf '  ACTION  Attach a device or start an emulator, then run scripts/run-connected-ui-tests.sh.\n' >&2
fi
if [[ "$preflight_exit" != "0" ]]; then
  printf '  ACTION  Review scripts/verify-release-ready.sh --skip-build output.\n' >&2
fi
if [[ "$package_exit" != "0" ]]; then
  printf '  ACTION  Fix package inputs and rerun scripts/prepare-play-store-upload.sh.\n' >&2
fi
if [[ "$package_check_exit" != "0" ]]; then
  printf '  ACTION  Review scripts/check-play-store-package.sh output.\n' >&2
fi
if [[ "$blocker_report_exit" != "0" ]]; then
  printf '  ACTION  Review scripts/release-blockers.sh output.\n' >&2
fi
exit 1
