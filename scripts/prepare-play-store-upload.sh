#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
source "$ROOT_DIR/scripts/signing-utils.sh"

RUN_BUILD=1
OUT_ROOT="build/play-store-upload"

usage() {
  printf '%s\n' "Usage: scripts/prepare-play-store-upload.sh [--skip-build] [--out DIR]"
  printf '%s\n' "Builds/checks release artifacts and collects Play Console upload assets into one folder."
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-build)
      RUN_BUILD=0
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

scripts/generate-store-assets.sh
scripts/generate-launcher-icons.sh

version_name="$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' app/build.gradle.kts | head -n 1)"
version_code="$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' app/build.gradle.kts | head -n 1)"
version_name="${version_name:-unknown}"
version_code="${version_code:-unknown}"
timestamp="$(date '+%Y%m%d-%H%M%S')"
package_dir="$OUT_ROOT/seemops-trinkspiel-v${version_name}-${version_code}-${timestamp}"

rm -rf "$package_dir"
mkdir -p \
  "$package_dir/android" \
  "$package_dir/android/release-apks" \
	  "$package_dir/docs" \
	  "$package_dir/manual-qa-evidence" \
	  "$package_dir/manual-qa-fixtures" \
	  "$package_dir/privacy-hosting" \
	  "$package_dir/release-signing-handoff" \
	  "$package_dir/store-assets/screenshots/phone" \
  "$package_dir/status" \
  "$package_dir/tools"

copy_required() {
  local source="$1"
  local destination="$2"
  if [[ ! -s "$source" ]]; then
    printf 'Missing required package input: %s\n' "$source" >&2
    exit 1
  fi
  cp "$source" "$destination"
}

copy_required "app/build/outputs/bundle/release/app-release.aab" "$package_dir/android/app-release.aab"
release_apks=(app/build/outputs/apk/release/*.apk)
if (( ${#release_apks[@]} == 0 )); then
  printf 'Missing required package input: app/build/outputs/apk/release/*.apk\n' >&2
  exit 1
fi
for release_apk in "${release_apks[@]}"; do
  copy_required "$release_apk" "$package_dir/android/release-apks/$(basename "$release_apk")"
done
copy_required "app/build/outputs/apk/debug/app-debug.apk" "$package_dir/android/app-debug.apk"
copy_required "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk" "$package_dir/android/app-debug-androidTest.apk"

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
  copy_required "$doc" "$package_dir/docs/$(basename "$doc")"
done
scripts/prepare-manual-qa.sh \
  --guide-only \
  --skip-fixtures \
  --out "$package_dir/docs" \
  --fixtures-out "manual-qa-fixtures" \
  --evidence-out "$package_dir/manual-qa-evidence" \
  >/dev/null
scripts/generate-manual-qa-fixtures.sh --out "$package_dir/manual-qa-fixtures" >/dev/null
scripts/prepare-privacy-policy-hosting.sh --out "$package_dir/privacy-hosting" >/dev/null
scripts/prepare-release-signing-handoff.sh --out "$package_dir/release-signing-handoff" >/dev/null
copy_required "docs/privacy-policy.html" "$package_dir/privacy-policy.html"

for tool in \
  scripts/check-manual-qa-evidence-packet.sh \
  scripts/check-manual-qa-report.sh \
  scripts/check-play-store-package.sh \
  scripts/check-store-screenshot-polish.sh \
  scripts/check-upload-owner-signoff.sh \
	  scripts/generate-manual-qa-fixtures.sh \
	  scripts/prepare-manual-qa-evidence-packet.sh \
  scripts/prepare-release-owner-brief.sh \
	  scripts/prepare-privacy-policy-hosting.sh \
	  scripts/prepare-release-signing-handoff.sh \
	  scripts/privacy-policy-utils.sh \
  scripts/seed-manual-qa-last-issue.sh \
  scripts/signing-utils.sh
do
  copy_required "$tool" "$package_dir/tools/$(basename "$tool")"
done

copy_required "docs/store-assets/feature-graphic.png" "$package_dir/store-assets/feature-graphic.png"
copy_required "docs/store-assets/store-icon.png" "$package_dir/store-assets/store-icon.png"
for screenshot in docs/store-assets/screenshots/phone/*.png; do
  copy_required "$screenshot" "$package_dir/store-assets/screenshots/phone/$(basename "$screenshot")"
done

scripts/release-status.sh > "$package_dir/status/release-status.txt"
set +e
scripts/check-release-signing-config.sh > "$package_dir/status/check-release-signing-config.txt" 2>&1
signing_config_exit="$?"
scripts/check-privacy-policy-url.sh > "$package_dir/status/check-privacy-policy-url.txt" 2>&1
privacy_check_exit="$?"
scripts/check-play-console-handoff.sh > "$package_dir/status/check-play-console-handoff.txt" 2>&1
handoff_check_exit="$?"
scripts/check-manual-qa-report.sh --require-confirmation > "$package_dir/status/check-manual-qa-report.txt" 2>&1
manual_qa_check_exit="$?"
scripts/release-blockers.sh --no-fail > "$package_dir/status/release-blockers.md" 2>&1
blocker_report_exit="$?"
scripts/release-blockers.sh --json --no-fail > "$package_dir/status/release-blockers.json" 2>&1
blocker_json_exit="$?"
scripts/verify-release-ready.sh --skip-build > "$package_dir/status/verify-release-ready.txt" 2>&1
preflight_exit="$?"
set -e
printf 'Upload owner signoff pending until package self-check completes.\n' > "$package_dir/status/check-upload-owner-signoff.txt"

blocker_count="unknown"
release_artifacts_status="unknown"
if [[ -s "$package_dir/status/release-blockers.json" ]]; then
  if command -v python3 >/dev/null 2>&1; then
    blocker_json_values="$(python3 - "$package_dir/status/release-blockers.json" <<'PY' || true
import json
import sys

path = sys.argv[1]
try:
    with open(path, "r", encoding="utf-8") as handle:
        data = json.load(handle)
except Exception:
    print("unknown")
    print("unknown")
    raise SystemExit(0)

blocker_count = data.get("blocker_count")
if not isinstance(blocker_count, int):
    blocker_count = "unknown"

release_artifacts_status = "unknown"
items = data.get("items")
if isinstance(items, list):
    for item in items:
        if isinstance(item, dict) and item.get("id") == "release_artifacts":
            status = item.get("status")
            if isinstance(status, str) and status:
                release_artifacts_status = status
            break

print(blocker_count)
print(release_artifacts_status)
PY
)"
    blocker_count="$(sed -n '1p' <<<"$blocker_json_values")"
    release_artifacts_status="$(sed -n '2p' <<<"$blocker_json_values")"
    blocker_count="${blocker_count:-unknown}"
    release_artifacts_status="${release_artifacts_status:-unknown}"
  else
    printf 'python3 is not available; cannot parse %s for manifest blocker fields.\n' "$package_dir/status/release-blockers.json" >&2
  fi
fi

signing_status="$(release_aab_signing_status "$package_dir/android/app-release.aab")"

privacy_status="missing"
if [[ -n "${SEEMOPS_PRIVACY_POLICY_URL:-}" ]]; then
  privacy_status="$SEEMOPS_PRIVACY_POLICY_URL"
fi
manual_qa_status="missing"
if [[ "${SEEMOPS_MANUAL_QA_CONFIRMED:-}" == "1" ]]; then
  manual_qa_status="confirmed"
fi
package_check_exit=0
upload_owner_signoff_exit="pending"
package_integrity_status="pass"
upload_ready_status="blocked"
if [[ "$preflight_exit" == "0" &&
  "$signing_status" == "signed" &&
  "$signing_config_exit" == "0" &&
  "$privacy_check_exit" == "0" &&
  "$handoff_check_exit" == "0" &&
  "$manual_qa_check_exit" == "0" &&
  "$manual_qa_status" == "confirmed" &&
  "$privacy_status" != "missing" &&
  "$release_artifacts_status" == "ready" &&
  "$blocker_count" == "0" ]]; then
  upload_ready_status="ready"
fi

cat > "$package_dir/MANIFEST.md" <<EOF
# Seemops Trinkspiel Play Store Package

Generated: $(date '+%Y-%m-%d %H:%M:%S %Z')

## Version

- Version name: ${version_name}
- Version code: ${version_code}
- Release AAB signing status: ${signing_status}
- Signing config check exit code: ${signing_config_exit}
- Privacy policy check exit code: ${privacy_check_exit}
- Play Console handoff check exit code: ${handoff_check_exit}
- Manual QA report check exit code: ${manual_qa_check_exit}
- Release blocker report exit code: ${blocker_report_exit}
- Release blocker JSON exit code: ${blocker_json_exit}
- Release artifacts status: ${release_artifacts_status}
- Blockers remaining: ${blocker_count}
- Package completeness check exit code: ${package_check_exit}
- Upload owner signoff check exit code: ${upload_owner_signoff_exit}
- Package integrity status: ${package_integrity_status}
- Upload-ready status: ${upload_ready_status}
- Hosted privacy policy URL: ${privacy_status}
- Manual QA status: ${manual_qa_status}
- Preflight exit code: ${preflight_exit}

## Android Artifacts

- \`android/app-release.aab\`: Play Console app bundle upload artifact
- \`android/release-apks/*.apk\`: release APK(s) for local inspection; signed or unsigned depending on signing config
- \`android/app-debug.apk\`: debug APK for QA
- \`android/app-debug-androidTest.apk\`: connected Android test APK

## Owner Brief

- \`OWNER_BRIEF.md\`: one-page release-owner status, blocker, and handoff map

## Store Assets

- \`store-assets/feature-graphic.png\`
- \`store-assets/store-icon.png\`
- \`store-assets/screenshots/phone/*.png\`

## Store Docs

- \`docs/PLAY_STORE_METADATA.md\`
- \`docs/PLAY_CONSOLE_SUBMISSION.md\`
- \`docs/PLAY_STORE_RELEASE_CHECKLIST.md\`
- \`docs/PRIVACY_POLICY.md\`
- \`docs/privacy-policy.html\`
- \`docs/DATA_SAFETY.md\`
- \`docs/CONTENT_REVIEW.md\`
- \`docs/CURRENT_APP_STATUS.md\`
- \`docs/ASAP_FEATURES.md\`
- \`docs/MANUAL_QA_REPORT.md\`
- \`docs/manual-qa-guide.md\`
- \`docs/FINAL_RELEASE_RUNBOOK.md\`
- \`docs/RELEASE_AUDIT.md\`
- \`docs/RELEASE_NOTES.md\`

## Status

- \`status/release-status.txt\`
- \`status/check-release-signing-config.txt\`
- \`status/check-privacy-policy-url.txt\`
- \`status/check-play-console-handoff.txt\`
- \`status/check-manual-qa-report.txt\`
- \`status/check-play-store-package.txt\`
- \`status/check-upload-owner-signoff.txt\`
- \`status/release-blockers.md\`
- \`status/release-blockers.json\`
- \`status/verify-release-ready.txt\`

## Manual QA Fixtures

- \`manual-qa-fixtures/seemops_qa_card_pack.json\`: card-pack import preview/check fixture
- \`manual-qa-fixtures/seemops_qa_backup.json\`: full-backup import preview/check fixture
- \`manual-qa-fixtures/seemops_qa_transfer_package.json\`: transfer-package receive/import fixture

## Manual QA Evidence Packet

- \`manual-qa-evidence/README.md\`: evidence packet summary and privacy-safe capture rules
- \`manual-qa-evidence/tester-run-sheet.md\`: row-by-row tester actions, expected proof, and report-update guidance
- \`manual-qa-evidence/evidence-notes-template.md\`: high-risk Notes prompts for the report
- \`manual-qa-evidence/checklist-index.md\`: all manual-QA rows with high-risk markers
- \`manual-qa-evidence/screenshots/README.md\`: suggested screenshot names
- \`manual-qa-evidence/files/README.md\`: fixture/export/share evidence naming guidance
- \`manual-qa-evidence/summary.json\`: machine-readable evidence packet counts
- \`manual-qa-evidence/checksums.sha256\`: checksum evidence for the evidence packet

## Privacy Policy Hosting

- \`privacy-hosting/privacy-policy.html\`: exact static HTML to host for the Play Console privacy-policy URL
- \`privacy-hosting/index.html\`: same exact HTML for hosts that serve a directory root
- \`privacy-hosting/checksums.sha256\`: checksum evidence for the static hosting bundle
- \`privacy-hosting/README.md\`: hosting and verification instructions

## Release Signing Handoff

- \`release-signing-handoff/README.md\`: non-secret signing setup and verification instructions
- \`release-signing-handoff/check-release-signing-config.txt\`: current non-secret signing checker output
- \`release-signing-handoff/release-signing.env.template\`: environment-variable template for a trusted signing machine
- \`release-signing-handoff/release-signing.properties.template\`: ignored local properties template
- \`release-signing-handoff/checksums.sha256\`: checksum evidence for the signing handoff folder

## Verifier Tools

- \`tools/check-play-store-package.sh\`
- \`tools/check-manual-qa-evidence-packet.sh\`
- \`tools/check-manual-qa-report.sh\`
- \`tools/check-store-screenshot-polish.sh\`
- \`tools/check-upload-owner-signoff.sh\`
- \`tools/generate-manual-qa-fixtures.sh\`
- \`tools/prepare-manual-qa-evidence-packet.sh\`
- \`tools/prepare-release-owner-brief.sh\`
- \`tools/prepare-privacy-policy-hosting.sh\`
- \`tools/prepare-release-signing-handoff.sh\`
- \`tools/privacy-policy-utils.sh\`
- \`tools/seed-manual-qa-last-issue.sh\`
- \`tools/signing-utils.sh\`

From the package root, run \`bash tools/check-play-store-package.sh .\` for a package-local self-audit using the copied verifier tools.

The package is upload-ready only when \`status/verify-release-ready.txt\` reports success, the release AAB signing status is \`signed\`, the signing config check is clean for visible local signing inputs, the privacy-policy URL check is clean, the Play Console handoff check is clean, the manual QA report check is clean, the hosted privacy-policy URL is final and verified, and manual QA status is \`confirmed\`.

The root \`privacy-policy.html\` file and \`privacy-hosting/\` bundle are ready to host as the Play Console privacy-policy URL target.
Follow \`release-signing-handoff/README.md\` and \`docs/FINAL_RELEASE_RUNBOOK.md\` for the final signing, privacy hosting, QA, and upload sequence.
EOF

scripts/prepare-release-owner-brief.sh --package-dir "$package_dir" --out "$package_dir/OWNER_BRIEF.md" >/dev/null

(
  cd "$package_dir"
  find . -type f ! -path "./checksums.sha256" -print0 \
    | sort -z \
    | xargs -0 shasum -a 256 \
    > checksums.sha256
)

set +e
SEEMOPS_PACKAGE_SELF_CHECK=1 scripts/check-play-store-package.sh "$package_dir" > "$package_dir/status/check-play-store-package.txt" 2>&1
package_check_exit="$?"
set -e
if [[ "$package_check_exit" != "0" ]]; then
  package_integrity_status="failed"
  upload_ready_status="blocked"
fi
sed -i.bak "s/^- Package completeness check exit code:.*/- Package completeness check exit code: ${package_check_exit}/" "$package_dir/MANIFEST.md"
sed -i.bak "s/^- Package integrity status:.*/- Package integrity status: ${package_integrity_status}/" "$package_dir/MANIFEST.md"
sed -i.bak "s/^- Upload-ready status:.*/- Upload-ready status: ${upload_ready_status}/" "$package_dir/MANIFEST.md"
rm -f "$package_dir/MANIFEST.md.bak"

scripts/prepare-release-owner-brief.sh --package-dir "$package_dir" --out "$package_dir/OWNER_BRIEF.md" >/dev/null

set +e
scripts/check-upload-owner-signoff.sh "$package_dir" > "$package_dir/status/check-upload-owner-signoff.txt" 2>&1
upload_owner_signoff_exit="$?"
set -e
if [[ "$upload_owner_signoff_exit" != "0" ]]; then
  package_integrity_status="failed"
  upload_ready_status="blocked"
fi
sed -i.bak "s/^- Upload owner signoff check exit code:.*/- Upload owner signoff check exit code: ${upload_owner_signoff_exit}/" "$package_dir/MANIFEST.md"
sed -i.bak "s/^- Package integrity status:.*/- Package integrity status: ${package_integrity_status}/" "$package_dir/MANIFEST.md"
sed -i.bak "s/^- Upload-ready status:.*/- Upload-ready status: ${upload_ready_status}/" "$package_dir/MANIFEST.md"
rm -f "$package_dir/MANIFEST.md.bak"

scripts/prepare-release-owner-brief.sh --package-dir "$package_dir" --out "$package_dir/OWNER_BRIEF.md" >/dev/null

(
  cd "$package_dir"
  find . -type f ! -path "./checksums.sha256" -print0 \
    | sort -z \
    | xargs -0 shasum -a 256 \
    > checksums.sha256
)

printf 'Play Store package written to %s\n' "$package_dir"
printf 'Preflight exit code: %s\n' "$preflight_exit"
printf 'Release AAB signing status: %s\n' "$signing_status"
printf 'Package check exit code: %s\n' "$package_check_exit"
printf 'Upload owner signoff exit code: %s\n' "$upload_owner_signoff_exit"
if [[ "$preflight_exit" != "0" ]]; then
  printf 'Review %s/status/verify-release-ready.txt before upload.\n' "$package_dir"
fi
if [[ "$package_check_exit" != "0" ]]; then
  printf 'Review %s/status/check-play-store-package.txt before upload.\n' "$package_dir" >&2
  exit 1
fi
if [[ "$upload_owner_signoff_exit" != "0" ]]; then
  printf 'Review %s/status/check-upload-owner-signoff.txt before upload.\n' "$package_dir" >&2
  exit 1
fi
