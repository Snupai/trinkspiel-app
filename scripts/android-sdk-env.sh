#!/usr/bin/env bash

ensure_android_sdk_tools_on_path() {
  local sdk_dir=""
  local candidate
  local home_dir="${HOME:-}"

  for candidate in \
    "${ANDROID_HOME:-}" \
    "${ANDROID_SDK_ROOT:-}" \
    "${home_dir}/Library/Android/sdk" \
    "${home_dir}/Android/Sdk" \
    "/opt/android-sdk"
  do
    if [[ -n "$candidate" && -d "$candidate" ]]; then
      sdk_dir="$candidate"
      break
    fi
  done

  if [[ -z "$sdk_dir" ]]; then
    return 0
  fi

  export ANDROID_HOME="${ANDROID_HOME:-$sdk_dir}"
  export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$sdk_dir}"

  local tool_dir
  for tool_dir in \
    "$sdk_dir/platform-tools" \
    "$sdk_dir/emulator" \
    "$sdk_dir/cmdline-tools/latest/bin"
  do
    if [[ -d "$tool_dir" ]]; then
      case ":${PATH:-}:" in
        *":$tool_dir:"*) ;;
        *) PATH="$tool_dir${PATH:+:$PATH}" ;;
      esac
    fi
  done

  export PATH
}

android_sdk_tools_hint() {
  printf '%s\n' "Set ANDROID_HOME/ANDROID_SDK_ROOT or add Android platform-tools to PATH."
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  ensure_android_sdk_tools_on_path
  if command -v adb >/dev/null 2>&1; then
    printf 'adb: %s\n' "$(command -v adb)"
  else
    android_sdk_tools_hint >&2
    exit 3
  fi
fi
