#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

OUT_DIR="build/manual-qa/fixtures"

usage() {
  printf '%s\n' "Usage: scripts/generate-manual-qa-fixtures.sh [--out DIR]"
  printf '%s\n' "Writes known-good JSON files for manual QA import, backup restore, and transfer-package checks."
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

mkdir -p "$OUT_DIR"

card_pack_file="$OUT_DIR/seemops_qa_card_pack.json"
backup_file="$OUT_DIR/seemops_qa_backup.json"
transfer_file="$OUT_DIR/seemops_qa_transfer_package.json"

cat > "$card_pack_file" <<'JSON'
{
  "version": 3,
  "packName": "Seemops Manual QA Card Pack",
  "cards": [
    {
      "text": "Manual QA import card: everyone says their current score.",
      "drinks": 1,
      "category": "challenge",
      "packName": "Seemops Manual QA Card Pack",
      "enabled": true
    },
    {
      "text": "Manual QA paused import card: activate this after preview.",
      "drinks": 2,
      "category": "rule",
      "packName": "Seemops Manual QA Card Pack",
      "enabled": false
    }
  ]
}
JSON

cat > "$backup_file" <<'JSON'
{
  "type": "seemops.backup",
  "version": 1,
  "cards": [
    {
      "text": "Manual QA backup card: restored from full backup.",
      "drinks": 1,
      "category": "truth",
      "packName": "Seemops Manual QA Backup",
      "enabled": true
    },
    {
      "text": "Manual QA backup rule: this card should stay paused.",
      "drinks": 2,
      "category": "rule",
      "packName": "Seemops Manual QA Backup",
      "enabled": false
    }
  ],
  "settings": {
    "players": [
      "QA Lena",
      "QA Mika"
    ],
    "playerStats": [
      {
        "name": "QA Lena",
        "team": "team_a",
        "points": 3,
        "drinks": 4
      },
      {
        "name": "QA Mika",
        "team": "team_b",
        "points": 2,
        "drinks": 5
      }
    ],
    "currentPlayerIndex": 0,
    "mode": "classic",
    "intensity": "low",
    "ageGateAccepted": true,
    "safetyNoticeAccepted": true,
    "firstRunSetupCompleted": true,
    "themeMode": "system",
    "dynamicColors": false,
    "drinkSingular": "QA Sip",
    "drinkPlural": "QA Sips",
    "excludedPackNames": [],
    "customCategoryIds": [
      "truth",
      "challenge",
      "rule",
      "everyone",
      "duel",
      "mini_game",
      "spicy"
    ]
  }
}
JSON

cp "$backup_file" "$transfer_file"

if command -v python3 >/dev/null 2>&1; then
  python3 - "$card_pack_file" "$backup_file" "$transfer_file" <<'PY'
import json
import sys

card_pack_path, backup_path, transfer_path = sys.argv[1:]
with open(card_pack_path, "r", encoding="utf-8") as handle:
    card_pack = json.load(handle)
with open(backup_path, "r", encoding="utf-8") as handle:
    backup = json.load(handle)
with open(transfer_path, "r", encoding="utf-8") as handle:
    transfer = json.load(handle)

assert card_pack["version"] == 3
assert card_pack["packName"] == "Seemops Manual QA Card Pack"
assert len(card_pack["cards"]) >= 2
assert backup["type"] == "seemops.backup"
assert backup["settings"]["players"] == ["QA Lena", "QA Mika"]
assert transfer == backup
PY
fi

printf 'Manual QA fixtures written to %s\n' "$OUT_DIR"
printf -- '- %s\n' "$card_pack_file"
printf -- '- %s\n' "$backup_file"
printf -- '- %s\n' "$transfer_file"
