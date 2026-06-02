#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
source "$ROOT_DIR/scripts/privacy-policy-utils.sh"

LOCAL_ONLY=0
URL="${SEEMOPS_PRIVACY_POLICY_URL:-}"
URL_ARG_SEEN=0

usage() {
  printf '%s\n' "Usage: scripts/check-privacy-policy-url.sh [--local-only] [URL]"
  printf '%s\n' "Validates the local privacy policy and, unless --local-only is used, the hosted Play Console privacy-policy URL."
  printf '%s\n' "When URL is omitted, SEEMOPS_PRIVACY_POLICY_URL is used."
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --local-only)
      LOCAL_ONLY=1
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      if [[ "$URL_ARG_SEEN" == "1" ]]; then
        usage >&2
        exit 64
      fi
      URL="$1"
      URL_ARG_SEEN=1
      shift
      ;;
  esac
done

failures=0
LOCAL_PRIVACY_HTML="docs/privacy-policy.html"

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

printf 'Privacy policy URL check\n'

if [[ -s "docs/PRIVACY_POLICY.md" ]]; then
  ok "docs/PRIVACY_POLICY.md"
else
  fail "Missing docs/PRIVACY_POLICY.md"
fi

if [[ -s "$LOCAL_PRIVACY_HTML" ]]; then
  ok "$LOCAL_PRIVACY_HTML"
  if contains_expected_privacy_text "$(cat "$LOCAL_PRIVACY_HTML")"; then
    ok "Local privacy-policy HTML contains expected Seemops privacy text"
  else
    fail "Local privacy-policy HTML is missing expected Seemops privacy text"
  fi
  local_privacy_sha="$(privacy_file_sha256 "$LOCAL_PRIVACY_HTML")"
  if [[ -n "$local_privacy_sha" ]]; then
    ok "Local privacy-policy SHA-256: $local_privacy_sha"
  fi
else
  fail "Missing $LOCAL_PRIVACY_HTML"
fi

if [[ "$LOCAL_ONLY" == "1" ]]; then
  if (( failures > 0 )); then
    exit 1
  fi
  exit 0
fi

if [[ -z "$URL" ]]; then
  fail "SEEMOPS_PRIVACY_POLICY_URL is not set"
  action "Host docs/privacy-policy.html, then set SEEMOPS_PRIVACY_POLICY_URL to the final public HTTPS URL."
elif url_problem="$(privacy_url_issue "$URL")"; then
  fail "Privacy-policy URL $url_problem: $URL"
else
  ok "Hosted privacy-policy URL is HTTPS: $URL"
  if ! command -v curl >/dev/null 2>&1; then
    fail "Cannot verify hosted privacy-policy URL without curl"
  else
    hosted_html_file="$(mktemp)"
    if effective_url="$(fetch_hosted_privacy_policy "$URL" "$hosted_html_file")"; then
      ok "Hosted privacy-policy URL is reachable"
      if [[ "$effective_url" != "$URL" ]]; then
        if effective_url_problem="$(privacy_url_issue "$effective_url")"; then
          fail "Hosted privacy-policy URL redirects to a URL that $effective_url_problem: $effective_url"
        else
          ok "Hosted privacy-policy final URL is HTTPS: $effective_url"
        fi
      fi
      hosted_html="$(cat "$hosted_html_file")"
      if contains_expected_privacy_text "$hosted_html"; then
        ok "Hosted privacy-policy content matches expected Seemops privacy text"
      else
        fail "Hosted privacy-policy URL is reachable but missing expected Seemops privacy text"
      fi
      if cmp -s "$hosted_html_file" "$LOCAL_PRIVACY_HTML"; then
        ok "Hosted privacy-policy content exactly matches docs/privacy-policy.html"
      else
        hosted_privacy_sha="$(privacy_file_sha256 "$hosted_html_file")"
        local_privacy_sha="$(privacy_file_sha256 "$LOCAL_PRIVACY_HTML")"
        fail "Hosted privacy-policy content does not exactly match docs/privacy-policy.html"
        [[ -n "$local_privacy_sha" ]] && action "Local privacy-policy SHA-256: $local_privacy_sha"
        [[ -n "$hosted_privacy_sha" ]] && action "Hosted privacy-policy SHA-256: $hosted_privacy_sha"
      fi
    else
      fail "Hosted privacy-policy URL is not reachable: $URL"
    fi
    rm -f "$hosted_html_file"
  fi
fi

if (( failures > 0 )); then
  exit 1
fi
