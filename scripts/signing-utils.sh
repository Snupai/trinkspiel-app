#!/usr/bin/env bash

android_debug_signing_text() {
  local text="$1"
  grep -Eiq '(^|[[:space:]])(CN=Android Debug|androiddebugkey)([[:space:],:]|$)|Alias name:[[:space:]]*androiddebugkey' <<<"$text"
}

android_debug_keystore_path() {
  local path="$1"
  local lower
  lower="$(printf '%s' "$path" | tr '[:upper:]' '[:lower:]')"
  [[ "$lower" == */.android/debug.keystore || "$lower" == */debug.keystore || "$lower" == "debug.keystore" ]]
}

android_debug_key_alias() {
  local alias="$1"
  local lower
  lower="$(printf '%s' "$alias" | tr '[:upper:]' '[:lower:]')"
  [[ "$lower" == "androiddebugkey" ]]
}

android_debug_keystore_secret() {
  [[ "$1" == "android" ]]
}

release_aab_signing_status() {
  local aab="$1"
  local verify_output

  if [[ ! -f "$aab" ]]; then
    printf 'missing'
    return 0
  fi

  if ! command -v jarsigner >/dev/null 2>&1; then
    printf 'unknown'
    return 0
  fi

  verify_output="$(jarsigner -verify -verbose -certs "$aab" 2>&1 || true)"
  if grep -qi "jar is unsigned" <<<"$verify_output"; then
    printf 'unsigned'
  elif grep -qi "jar verified" <<<"$verify_output"; then
    if android_debug_signing_text "$verify_output"; then
      printf 'debug-signed'
    else
      printf 'signed'
    fi
  else
    printf 'unknown'
  fi
}
