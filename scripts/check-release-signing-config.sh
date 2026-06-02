#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
source "$ROOT_DIR/scripts/signing-utils.sh"

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  printf '%s\n' "Usage: scripts/check-release-signing-config.sh"
  printf '%s\n' "Validates the visible local release-signing inputs without printing secrets."
  printf '%s\n' "Checks Gradle properties files, environment variables, and signing/release-signing.properties."
  exit 0
fi

DEFAULT_STORE_FILE="signing/seemops-release.keystore"
SIGNING_PROPERTIES_FILE="signing/release-signing.properties"
GRADLE_USER_HOME_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}"
GRADLE_PROPERTIES_FILES=(
  "$GRADLE_USER_HOME_DIR/gradle.properties"
  "gradle.properties"
)

failures=0

ok() {
  printf '  OK      %s\n' "$1"
}

missing() {
  printf '  MISSING %s\n' "$1"
  failures=$(( failures + 1 ))
}

review() {
  printf '  REVIEW  %s\n' "$1"
  failures=$(( failures + 1 ))
}

info() {
  printf '  INFO    %s\n' "$1"
}

action() {
  printf '  ACTION  %s\n' "$1"
}

property_from_file() {
  local file="$1"
  local key="$2"

  if [[ ! -f "$file" ]]; then
    return 0
  fi

  awk -F= -v wanted="$key" '
    /^[[:space:]]*#/ { next }
    /^[[:space:]]*$/ { next }
    {
      key = $1
      sub(/^[[:space:]]+/, "", key)
      sub(/[[:space:]]+$/, "", key)
      if (key == wanted) {
        value = substr($0, index($0, "=") + 1)
        sub(/^[[:space:]]+/, "", value)
        sub(/[[:space:]]+$/, "", value)
        print value
        exit
      }
    }
  ' "$file"
}

configured_value() {
  local key="$1"
  local file value

  for file in "${GRADLE_PROPERTIES_FILES[@]}"; do
    value="$(property_from_file "$file" "$key")"
    if [[ -n "$value" ]]; then
      printf '%s' "$value"
      return 0
    fi
  done

  value="${!key-}"
  if [[ -n "$value" ]]; then
    printf '%s' "$value"
    return 0
  fi

  value="$(property_from_file "$SIGNING_PROPERTIES_FILE" "$key")"
  if [[ -n "$value" ]]; then
    printf '%s' "$value"
  fi
}

configured_source() {
  local key="$1"
  local file value

  for file in "${GRADLE_PROPERTIES_FILES[@]}"; do
    value="$(property_from_file "$file" "$key")"
    if [[ -n "$value" ]]; then
      printf '%s' "$file"
      return 0
    fi
  done

  value="${!key-}"
  if [[ -n "$value" ]]; then
    printf 'environment'
    return 0
  fi

  value="$(property_from_file "$SIGNING_PROPERTIES_FILE" "$key")"
  if [[ -n "$value" ]]; then
    printf '%s' "$SIGNING_PROPERTIES_FILE"
  fi
}

resolve_store_path() {
  local path="$1"
  if [[ "$path" = /* ]]; then
    printf '%s' "$path"
  else
    printf '%s/%s' "$ROOT_DIR" "$path"
  fi
}

is_placeholder_secret() {
  local value="$1"
  [[ "$value" == "change-me" || "$value" == "CHANGE_ME" || "$value" == "changeme" ]]
}

file_mode() {
  local file="$1"
  if stat -f '%Lp' "$file" >/dev/null 2>&1; then
    stat -f '%Lp' "$file"
  else
    stat -c '%a' "$file"
  fi
}

permissions_are_private() {
  local mode="$1"
  [[ "${mode: -2}" == "00" ]]
}

require_private_file_permissions() {
  local label="$1"
  local file="$2"
  local mode

  if [[ ! -f "$file" ]]; then
    return 0
  fi

  if ! mode="$(file_mode "$file")"; then
    review "Could not inspect permissions for $label: $file"
    return 0
  fi

  if permissions_are_private "$mode"; then
    ok "$label permissions are private: $file (mode $mode)"
  else
    review "$label is readable by group/others: $file (mode $mode). Run chmod 600 \"$file\"."
  fi
}

validate_temporary_signing_probe() {
  local probe_dir unsigned_jar signed_jar verify_output

  if ! command -v jar >/dev/null 2>&1 || ! command -v jarsigner >/dev/null 2>&1; then
    review "jar and jarsigner are required to validate that the configured key password can sign"
    return
  fi

  probe_dir="$(mktemp -d)"
  printf 'Seemops release signing probe\n' > "$probe_dir/probe.txt"
  unsigned_jar="$probe_dir/unsigned.jar"
  signed_jar="$probe_dir/signed.jar"

  if jar cf "$unsigned_jar" -C "$probe_dir" probe.txt >/dev/null 2>&1 &&
    jarsigner \
      -keystore "$store_path" \
      -storepass "$store_password" \
      -keypass "$key_password" \
      -signedjar "$signed_jar" \
      "$unsigned_jar" \
      "$key_alias" >/dev/null 2>&1; then
    if verify_output="$(jarsigner -verify -certs "$signed_jar" 2>&1)" &&
      grep -qi "jar verified" <<<"$verify_output"; then
      ok "Configured key password can sign a temporary verification JAR"
      if android_debug_signing_text "$verify_output"; then
        review "Temporary signing probe appears to use Android debug signing material"
      else
        ok "Temporary signing probe does not look like Android debug signing material"
      fi
    else
      review "Temporary signing probe could not be verified"
    fi
  else
    review "Configured signing values could not sign a temporary verification JAR"
  fi

  rm -rf "$probe_dir"
}

printf 'Release signing config\n'
info "Visible sources: Gradle properties files, environment variables, $SIGNING_PROPERTIES_FILE"
info "Command-line Gradle -P secrets are only validated after a signed bundle is built."

store_file="$(configured_value "SEEMOPS_RELEASE_STORE_FILE")"
store_file="${store_file:-$DEFAULT_STORE_FILE}"
store_file_source="$(configured_source "SEEMOPS_RELEASE_STORE_FILE")"
store_file_source="${store_file_source:-default path}"
store_path="$(resolve_store_path "$store_file")"

if [[ -f "$store_path" ]]; then
  ok "Keystore file exists: $store_file ($store_file_source)"
  require_private_file_permissions "Keystore file" "$store_path"
else
  missing "Keystore file does not exist: $store_file"
fi
if android_debug_keystore_path "$store_file" || android_debug_keystore_path "$store_path"; then
  review "Release store file points at an Android debug keystore path: $store_file"
fi
require_private_file_permissions "Signing properties file" "$SIGNING_PROPERTIES_FILE"

store_password="$(configured_value "SEEMOPS_RELEASE_STORE_PASSWORD")"
store_password_source="$(configured_source "SEEMOPS_RELEASE_STORE_PASSWORD")"
if [[ -z "$store_password" ]]; then
  missing "SEEMOPS_RELEASE_STORE_PASSWORD is not configured"
elif is_placeholder_secret "$store_password"; then
  review "SEEMOPS_RELEASE_STORE_PASSWORD still uses the example placeholder"
elif android_debug_keystore_secret "$store_password"; then
  review "SEEMOPS_RELEASE_STORE_PASSWORD uses the Android debug keystore default"
else
  ok "Store password is configured (${store_password_source})"
fi

key_alias="$(configured_value "SEEMOPS_RELEASE_KEY_ALIAS")"
key_alias_source="$(configured_source "SEEMOPS_RELEASE_KEY_ALIAS")"
if [[ -z "$key_alias" ]]; then
  missing "SEEMOPS_RELEASE_KEY_ALIAS is not configured"
elif android_debug_key_alias "$key_alias"; then
  review "SEEMOPS_RELEASE_KEY_ALIAS uses the Android debug key alias"
else
  ok "Key alias is configured: $key_alias (${key_alias_source})"
fi

key_password="$(configured_value "SEEMOPS_RELEASE_KEY_PASSWORD")"
key_password_source="$(configured_source "SEEMOPS_RELEASE_KEY_PASSWORD")"
if [[ -z "$key_password" ]]; then
  missing "SEEMOPS_RELEASE_KEY_PASSWORD is not configured"
elif is_placeholder_secret "$key_password"; then
  review "SEEMOPS_RELEASE_KEY_PASSWORD still uses the example placeholder"
elif android_debug_keystore_secret "$key_password"; then
  review "SEEMOPS_RELEASE_KEY_PASSWORD uses the Android debug keystore default"
else
  ok "Key password is configured (${key_password_source})"
fi

if [[ -f "$store_path" &&
  -n "$store_password" &&
  -n "$key_alias" &&
  -n "$key_password" ]] &&
  ! is_placeholder_secret "$store_password" &&
  ! is_placeholder_secret "$key_password"; then
  if ! command -v keytool >/dev/null 2>&1; then
    review "keytool is not available, so the keystore password and alias were not validated"
  elif keytool_output="$(keytool -list \
    -v \
    -keystore "$store_path" \
    -storepass "$store_password" \
    -alias "$key_alias" 2>/dev/null)"; then
    ok "Keystore opens and alias is present"
    if grep -q "PrivateKeyEntry" <<<"$keytool_output"; then
      ok "Key alias is a private-key entry"
    else
      review "Key alias exists but does not look like a PrivateKeyEntry"
    fi
    if android_debug_signing_text "$keytool_output"; then
      review "Key alias appears to use Android debug signing material"
    else
      ok "Key certificate does not look like Android debug signing material"
    fi
    validate_temporary_signing_probe
  else
    review "Keystore could not be opened with the configured store password and alias"
  fi
fi

if (( failures > 0 )); then
  action "Run scripts/prepare-release-signing-handoff.sh for non-secret setup templates and current signing evidence."
  action "Run scripts/create-release-keystore.sh if you need a keystore."
  action "Run scripts/write-signing-properties.sh or export the SEEMOPS_RELEASE_* variables on a trusted machine."
  exit 1
fi

printf '  READY   Signing inputs look complete. Rebuild with ./gradlew :app:bundleRelease --no-daemon, then run scripts/verify-release-ready.sh --skip-build.\n'
