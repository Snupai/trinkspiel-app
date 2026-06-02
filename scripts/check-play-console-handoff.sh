#!/usr/bin/env bash
set -euo pipefail
shopt -s nullglob

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

HANDOFF="docs/PLAY_CONSOLE_SUBMISSION.md"
failures=0

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

require_contains() {
  local file="$1"
  local needle="$2"
  local label="$3"

  if [[ ! -s "$file" ]]; then
    fail "Cannot check missing file: $file"
    return
  fi

  if grep -Fq -- "$needle" "$file"; then
    ok "$label"
  else
    fail "$label is missing or out of sync"
  fi
}

extract_gradle_string() {
  local key="$1"
  sed -n "s/.*${key} = \"\\([^\"]*\\)\".*/\\1/p" app/build.gradle.kts | head -n 1
}

extract_gradle_number() {
  local key="$1"
  sed -n "s/.*${key} = \\([0-9]*\\).*/\\1/p" app/build.gradle.kts | head -n 1
}

printf 'Play Console handoff check\n'

app_name="$(sed -n 's/.*<string name="app_name">\([^<]*\)<\/string>.*/\1/p' app/src/main/res/values/strings.xml | head -n 1)"
application_id="$(extract_gradle_string "applicationId")"
version_name="$(extract_gradle_string "versionName")"
version_code="$(extract_gradle_number "versionCode")"

if [[ -n "$app_name" ]]; then
  ok "App name source: $app_name"
else
  fail "Could not read app name from app/src/main/res/values/strings.xml"
fi
if [[ -n "$application_id" ]]; then
  ok "Application ID source: $application_id"
else
  fail "Could not read applicationId from app/build.gradle.kts"
fi
if [[ -n "$version_name" ]]; then
  ok "Version name source: $version_name"
else
  fail "Could not read versionName from app/build.gradle.kts"
fi
if [[ -n "$version_code" ]]; then
  ok "Version code source: $version_code"
else
  fail "Could not read versionCode from app/build.gradle.kts"
fi

for file in \
  "$HANDOFF" \
  docs/PLAY_STORE_METADATA.md \
  docs/DATA_SAFETY.md \
  docs/CONTENT_REVIEW.md \
  docs/MANUAL_QA_REPORT.md \
  docs/RELEASE_NOTES.md \
  docs/privacy-policy.html \
  docs/store-assets/feature-graphic.png \
  docs/store-assets/store-icon.png
do
  require_file "$file"
done

screenshots=(docs/store-assets/screenshots/phone/*.png)
if (( ${#screenshots[@]} >= 6 )); then
  ok "Phone screenshots referenced by handoff (${#screenshots[@]} file(s))"
else
  fail "Expected at least 6 phone screenshots referenced by handoff; found ${#screenshots[@]}"
fi

require_contains "$HANDOFF" "| App name | ${app_name} |" "Handoff app name matches strings.xml"
require_contains "$HANDOFF" "| Package name | \`${application_id}\` |" "Handoff package name matches Gradle"
require_contains "$HANDOFF" "| Version name | \`${version_name}\` |" "Handoff version name matches Gradle"
require_contains "$HANDOFF" "| Version code | \`${version_code}\` |" "Handoff version code matches Gradle"

require_contains "$HANDOFF" "- No login is required." "Handoff app access says no login"
require_contains "$HANDOFF" "- The first launch shows the 18+ safety setup before gameplay." "Handoff mentions first-run 18+ setup"
require_contains "$HANDOFF" "- Ads: No." "Handoff ads answer"
require_contains "$HANDOFF" "- Ads SDKs: None." "Handoff ads SDK answer"
require_contains "$HANDOFF" "| Data collected by developer | No |" "Handoff data collection answer"
require_contains "$HANDOFF" "| Data shared with third parties by developer | No |" "Handoff data sharing answer"
require_contains "$HANDOFF" "| Analytics or tracking SDKs | No |" "Handoff analytics/tracking answer"
require_contains "$HANDOFF" "| Account creation | No |" "Handoff account answer"
require_contains "$HANDOFF" "| Server-side gameplay storage | No |" "Handoff server storage answer"
require_contains "$HANDOFF" "INTERNET is used only for optional user-configured manual Backend-Sync and Backend-Invite actions" "Handoff optional internet/sync disclosure"
require_contains "$HANDOFF" "- Alcohol/drinking-game theme." "Handoff content-rating alcohol declaration"
require_contains "$HANDOFF" "- Adult/legal-drinking-age audience." "Handoff adult audience declaration"
require_contains "$HANDOFF" "- No real-money gambling." "Handoff gambling declaration"

require_contains docs/DATA_SAFETY.md "Data collected by developer: No" "Data safety doc agrees on developer collection"
require_contains docs/DATA_SAFETY.md "Data shared with third parties by developer: No" "Data safety doc agrees on developer sharing"
require_contains docs/DATA_SAFETY.md "App includes ads: No" "Data safety doc agrees on ads"
require_contains docs/DATA_SAFETY.md "Analytics/SDK tracking: No" "Data safety doc agrees on analytics"
require_contains docs/DATA_SAFETY.md "Account required: No" "Data safety doc agrees on account requirement"
require_contains docs/DATA_SAFETY.md "optional manual Backend-Sync sends card-library snapshots only to the user-configured Backend URL" "Data safety doc explains optional backend sync"

require_contains docs/PLAY_STORE_METADATA.md "$app_name" "Store metadata includes app name"
require_contains docs/PLAY_STORE_METADATA.md "Offline, ohne Konto, optionaler Karten-Backend-Sync, ohne Werbung, ohne Tracking" "Store metadata matches no account/ads/tracking claim"
require_contains docs/privacy-policy.html "does not use analytics" "Privacy policy matches analytics claim"
require_contains docs/privacy-policy.html "does not send gameplay data to a server" "Privacy policy matches server-storage claim"
require_contains docs/privacy-policy.html "optional Backend-Sync feature" "Privacy policy explains optional backend sync"

restricted_dependency_pattern='play-services-ads|firebase-analytics|crashlytics|appsflyer|adjust|facebook|amplitude|segment|mixpanel'
if grep -Eiq "$restricted_dependency_pattern" app/build.gradle.kts; then
  fail "Gradle dependencies include an ads, analytics, crash, or attribution SDK while handoff claims none"
else
  ok "No obvious ads/analytics/crash/attribution SDK dependencies in app/build.gradle.kts"
fi

if grep -q "android.permission.INTERNET" app/src/main/AndroidManifest.xml; then
  require_contains "$HANDOFF" "No gameplay server dependency; local play works offline" "Handoff distinguishes offline gameplay from optional internet permission"
  require_contains docs/PLAY_STORE_METADATA.md "optionaler Karten-Backend-Sync" "Store metadata discloses optional backend sync"
  ok "Manifest INTERNET permission is documented for optional user-configured Backend-Sync"
else
  ok "Manifest does not request INTERNET permission"
fi

if grep -q 'android:allowBackup="false"' app/src/main/AndroidManifest.xml; then
  ok "Manifest disables automatic Android backup"
else
  fail "Manifest should keep android:allowBackup=\"false\" for the current local-only data-safety story"
fi

if (( failures > 0 )); then
  printf '\nPlay Console handoff check failed with %s issue(s).\n' "$failures" >&2
  exit 1
fi

printf '\nPlay Console handoff check passed.\n'
