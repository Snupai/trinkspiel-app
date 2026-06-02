#!/usr/bin/env bash

PRIVACY_TEXT_MARKERS=(
  "Seemops Trinkspiel"
  "does not require an account"
  "does not show ads"
  "does not use analytics"
  "does not send gameplay data to a server"
  "Automatic Android cloud backup is disabled"
  "do not include card text"
  "intended only for adults of legal drinking age"
)

contains_expected_privacy_text() {
  local html="$1"
  local marker
  for marker in "${PRIVACY_TEXT_MARKERS[@]}"; do
    if ! grep -Fq "$marker" <<<"$html"; then
      return 1
    fi
  done
  ! grep -Eq "TODO|your-domain\\.example|example\\.com" <<<"$html"
}

privacy_file_sha256() {
  local file="$1"
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$file" | awk '{ print $1 }'
  elif command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$file" | awk '{ print $1 }'
  fi
}

privacy_url_issue() {
  local url="$1"
  local authority host

  if [[ ! "$url" =~ ^https://[^[:space:]]+\.[^[:space:]]+ ]]; then
    printf 'must be a public HTTPS URL'
    return 0
  fi

  authority="${url#https://}"
  authority="${authority%%/*}"
  host="${authority%%:*}"
  host="$(printf '%s' "$host" | tr '[:upper:]' '[:lower:]')"

  if [[ -z "$host" ||
    "$host" == "localhost" ||
    "$host" == *.localhost ||
    "$host" == *.local ||
    "$host" == *.test ||
    "$host" == *.invalid ||
    "$host" == *.example ||
    "$host" == "example.com" ||
    "$host" == "example.org" ||
    "$host" == "example.net" ||
    "$host" == "your-domain.example" ||
    "$host" =~ ^127\. ||
    "$host" =~ ^10\. ||
    "$host" =~ ^192\.168\. ||
    "$host" =~ ^172\.(1[6-9]|2[0-9]|3[0-1])\. ||
    "$host" == "0.0.0.0" ]]; then
    printf 'must not use a localhost, private, reserved, or example host'
    return 0
  fi

  return 1
}

fetch_hosted_privacy_policy() {
  local url="$1"
  local output_file="$2"
  local effective_url

  effective_url="$(curl -fsSL --max-time 15 --retry 1 -w '%{url_effective}' -o "$output_file" "$url" 2>/dev/null)" || return 1
  printf '%s' "$effective_url"
}
