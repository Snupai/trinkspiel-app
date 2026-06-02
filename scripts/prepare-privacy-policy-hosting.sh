#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
source "$ROOT_DIR/scripts/privacy-policy-utils.sh"

OUT_DIR="build/privacy-policy-hosting"
LOCAL_PRIVACY_HTML="docs/privacy-policy.html"

usage() {
  printf '%s\n' "Usage: scripts/prepare-privacy-policy-hosting.sh [--out DIR]"
  printf '%s\n' "Writes a static privacy-policy hosting folder with exact-match HTML and checksum evidence."
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

if [[ ! -s "$LOCAL_PRIVACY_HTML" ]]; then
  printf 'Missing local privacy-policy HTML: %s\n' "$LOCAL_PRIVACY_HTML" >&2
  exit 1
fi

local_privacy_html="$(cat "$LOCAL_PRIVACY_HTML")"
if ! contains_expected_privacy_text "$local_privacy_html"; then
  printf 'Local privacy-policy HTML is missing expected Seemops privacy text.\n' >&2
  exit 1
fi

local_privacy_sha="$(privacy_file_sha256 "$LOCAL_PRIVACY_HTML")"
if [[ -z "$local_privacy_sha" ]]; then
  printf 'Cannot calculate SHA-256 for %s; shasum or sha256sum is required.\n' "$LOCAL_PRIVACY_HTML" >&2
  exit 1
fi

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"
cp "$LOCAL_PRIVACY_HTML" "$OUT_DIR/privacy-policy.html"
cp "$LOCAL_PRIVACY_HTML" "$OUT_DIR/index.html"

cat > "$OUT_DIR/README.md" <<EOF
# Seemops Privacy Policy Hosting

Generated: $(date '+%Y-%m-%d %H:%M:%S %Z')

This folder contains exact copies of \`docs/privacy-policy.html\` for static hosting:

- \`privacy-policy.html\`: recommended Play Console privacy-policy target.
- \`index.html\`: same content, for hosts that serve a directory root.
- \`checksums.sha256\`: SHA-256 evidence for this folder.

Canonical source: \`docs/privacy-policy.html\`
Canonical SHA-256: \`${local_privacy_sha}\`

Upload one of the HTML files to a public HTTPS URL without wrapping, minifying, rewriting, or injecting banners. The final hosted response must exactly match \`docs/privacy-policy.html\`.

After hosting, verify the final URL:

\`\`\`sh
export SEEMOPS_PRIVACY_POLICY_URL="https://your-real-domain.example/privacy-policy.html"
scripts/check-privacy-policy-url.sh
\`\`\`

Use the same verified URL in Play Console.
EOF

(
  cd "$OUT_DIR"
  find . -type f ! -name "checksums.sha256" -print0 \
    | sort -z \
    | xargs -0 shasum -a 256 \
    > checksums.sha256
)

printf 'Privacy-policy hosting folder written to %s\n' "$OUT_DIR"
printf 'Canonical privacy-policy SHA-256: %s\n' "$local_privacy_sha"
printf 'Host %s/privacy-policy.html or %s/index.html at the final public HTTPS URL, then run scripts/check-privacy-policy-url.sh.\n' "$OUT_DIR" "$OUT_DIR"
