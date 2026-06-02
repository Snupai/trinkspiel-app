#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  printf '%s\n' "Usage: scripts/create-release-keystore.sh"
  printf '%s\n' "Creates a local Android release keystore. Existing keystores are never overwritten."
  printf '%s\n' "Requires keytool and creates the keystore with private local file permissions."
  printf '%s\n' "Optional environment variables:"
  printf '%s\n' "  SEEMOPS_RELEASE_STORE_FILE default: signing/seemops-release.keystore"
  printf '%s\n' "  SEEMOPS_RELEASE_KEY_ALIAS  default: seemops-release"
  exit 0
fi

STORE_FILE="${SEEMOPS_RELEASE_STORE_FILE:-signing/seemops-release.keystore}"
KEY_ALIAS="${SEEMOPS_RELEASE_KEY_ALIAS:-seemops-release}"

if ! command -v keytool >/dev/null 2>&1; then
  printf 'keytool is not available. Install a JDK before creating the release keystore.\n' >&2
  exit 2
fi

if [[ -e "$STORE_FILE" ]]; then
  printf 'Refusing to overwrite existing keystore: %s\n' "$STORE_FILE" >&2
  printf 'Move it aside or set SEEMOPS_RELEASE_STORE_FILE to a new path.\n' >&2
  exit 1
fi

umask 077
mkdir -p "$(dirname "$STORE_FILE")"

keytool -genkeypair \
  -v \
  -storetype PKCS12 \
  -keystore "$STORE_FILE" \
  -alias "$KEY_ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -dname "CN=Seemops Trinkspiel, OU=Release, O=Snupai, L=Berlin, ST=Berlin, C=DE"

chmod 600 "$STORE_FILE"

printf '\nCreated release keystore: %s\n' "$STORE_FILE"
printf 'Next: run scripts/write-signing-properties.sh or export the SEEMOPS_RELEASE_* variables, then run scripts/check-release-signing-config.sh.\n'
