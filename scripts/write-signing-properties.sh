#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  printf '%s\n' "Usage: scripts/write-signing-properties.sh"
  printf '%s\n' "Writes ignored local signing properties for Gradle."
  printf '%s\n' "Use only on a trusted local machine; this stores passwords on disk."
  exit 0
fi

DEFAULT_STORE_FILE="${SEEMOPS_RELEASE_STORE_FILE:-signing/seemops-release.keystore}"
DEFAULT_ALIAS="${SEEMOPS_RELEASE_KEY_ALIAS:-seemops-release}"
PROPERTIES_FILE="signing/release-signing.properties"

read -r -p "Keystore path [$DEFAULT_STORE_FILE]: " STORE_FILE
STORE_FILE="${STORE_FILE:-$DEFAULT_STORE_FILE}"

read -r -p "Key alias [$DEFAULT_ALIAS]: " KEY_ALIAS
KEY_ALIAS="${KEY_ALIAS:-$DEFAULT_ALIAS}"

read -r -s -p "Store password: " STORE_PASSWORD
printf '\n'
if [[ -z "$STORE_PASSWORD" ]]; then
  printf 'Store password cannot be empty.\n' >&2
  exit 1
fi

read -r -s -p "Key password [leave blank to reuse store password]: " KEY_PASSWORD
printf '\n'
KEY_PASSWORD="${KEY_PASSWORD:-$STORE_PASSWORD}"

mkdir -p signing
umask 077
cat > "$PROPERTIES_FILE" <<EOF
SEEMOPS_RELEASE_STORE_FILE=$STORE_FILE
SEEMOPS_RELEASE_STORE_PASSWORD=$STORE_PASSWORD
SEEMOPS_RELEASE_KEY_ALIAS=$KEY_ALIAS
SEEMOPS_RELEASE_KEY_PASSWORD=$KEY_PASSWORD
EOF

chmod 600 "$PROPERTIES_FILE"
printf 'Wrote ignored signing properties: %s\n' "$PROPERTIES_FILE"
printf 'Validating signing configuration...\n'
scripts/check-release-signing-config.sh
