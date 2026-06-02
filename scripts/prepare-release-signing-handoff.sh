#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
source "$ROOT_DIR/scripts/signing-utils.sh"

OUT_DIR="build/release-signing-handoff"

usage() {
  printf '%s\n' "Usage: scripts/prepare-release-signing-handoff.sh [--out DIR]"
  printf '%s\n' "Writes a non-secret release-signing handoff folder with templates and current checker output."
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --out)
      OUT_DIR="${2:-}"
      if [[ -z "$OUT_DIR" ]]; then
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

version_name="$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' app/build.gradle.kts | head -n 1)"
version_code="$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' app/build.gradle.kts | head -n 1)"
version_name="${version_name:-unknown}"
version_code="${version_code:-unknown}"

aab_path="app/build/outputs/bundle/release/app-release.aab"
aab_status="missing"
if [[ -f "$aab_path" ]]; then
  aab_status="$(release_aab_signing_status "$aab_path")"
fi

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

set +e
scripts/check-release-signing-config.sh > "$OUT_DIR/check-release-signing-config.txt" 2>&1
signing_check_exit="$?"
set -e

cat > "$OUT_DIR/release-signing.env.template" <<'EOF'
# Fill these on a trusted local machine. Do not commit real values.
export SEEMOPS_RELEASE_STORE_FILE="signing/seemops-release.keystore"
export SEEMOPS_RELEASE_STORE_PASSWORD="replace-with-real-store-password"
export SEEMOPS_RELEASE_KEY_ALIAS="seemops-release"
export SEEMOPS_RELEASE_KEY_PASSWORD="replace-with-real-key-password"
EOF

cat > "$OUT_DIR/release-signing.properties.template" <<'EOF'
# Copy to signing/release-signing.properties on a trusted local machine.
# Do not commit the real file.
SEEMOPS_RELEASE_STORE_FILE=signing/seemops-release.keystore
SEEMOPS_RELEASE_STORE_PASSWORD=replace-with-real-store-password
SEEMOPS_RELEASE_KEY_ALIAS=seemops-release
SEEMOPS_RELEASE_KEY_PASSWORD=replace-with-real-key-password
EOF

cat > "$OUT_DIR/README.md" <<EOF
# Seemops Release Signing Handoff

Generated: $(date '+%Y-%m-%d %H:%M:%S %Z')

App version: ${version_name} (${version_code})
Current release AAB signing status: ${aab_status}
Signing config check exit code: ${signing_check_exit}

This folder is safe to share in the Play Store handoff package. It contains no keystore and no real signing passwords.

## Files

- \`check-release-signing-config.txt\`: current non-secret signing checker output.
- \`release-signing.env.template\`: environment-variable template for a trusted machine.
- \`release-signing.properties.template\`: ignored local properties template for \`signing/release-signing.properties\`.
- \`checksums.sha256\`: checksum evidence for this handoff folder.

## Signing Steps

1. On the trusted signing machine, create or provide a real release keystore. Use \`scripts/create-release-keystore.sh\` if a new local keystore is needed.
2. Configure one of the supported signing sources:
   - Export the four \`SEEMOPS_RELEASE_*\` variables from \`release-signing.env.template\`.
   - Or create \`signing/release-signing.properties\` from \`release-signing.properties.template\`.
3. Keep local signing files private:

\`\`\`sh
chmod 600 signing/seemops-release.keystore
[[ ! -f signing/release-signing.properties ]] || chmod 600 signing/release-signing.properties
\`\`\`

4. Run the signing input check:

\`\`\`sh
scripts/check-release-signing-config.sh
\`\`\`

It must report \`READY\`, including private file permissions, a private-key alias, a successful temporary signing probe, and no Android debug signing material.

5. Rebuild and verify the release bundle:

\`\`\`sh
./gradlew :app:bundleRelease --no-daemon
scripts/verify-release-ready.sh --skip-build
\`\`\`

The release gate must report that \`app-release.aab\` is signed with non-debug signing material.
EOF

(
  cd "$OUT_DIR"
  find . -type f ! -name "checksums.sha256" -print0 \
    | sort -z \
    | xargs -0 shasum -a 256 \
    > checksums.sha256
)

printf 'Release-signing handoff folder written to %s\n' "$OUT_DIR"
printf 'Release AAB signing status: %s\n' "$aab_status"
printf 'Signing config check exit code: %s\n' "$signing_check_exit"
