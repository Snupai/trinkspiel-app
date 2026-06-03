#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT_DIR"

REPORT="docs/MANUAL_QA_REPORT.md"
OUT_DIR="build/manual-qa/evidence"
FIXTURES_DIR="build/manual-qa/fixtures"

if [[ -s "$ROOT_DIR/scripts/check-manual-qa-report.sh" ]]; then
  CHECKER="scripts/check-manual-qa-report.sh"
elif [[ -s "$ROOT_DIR/tools/check-manual-qa-report.sh" ]]; then
  CHECKER="tools/check-manual-qa-report.sh"
else
  printf 'Missing check-manual-qa-report.sh under scripts/ or tools/ in %s\n' "$ROOT_DIR" >&2
  exit 1
fi

usage() {
  printf '%s\n' "Usage: scripts/prepare-manual-qa-evidence-packet.sh [--report FILE] [--out DIR] [--fixtures-dir DIR]"
  printf '%s\n' "Writes a tester-facing evidence packet from docs/MANUAL_QA_REPORT.md and the manual-QA release gate row lists."
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --report)
      REPORT="${2:-}"
      if [[ -z "$REPORT" ]]; then
        usage >&2
        exit 64
      fi
      shift 2
      ;;
    --out)
      OUT_DIR="${2:-}"
      if [[ -z "$OUT_DIR" ]]; then
        usage >&2
        exit 64
      fi
      shift 2
      ;;
    --fixtures-dir)
      FIXTURES_DIR="${2:-}"
      if [[ -z "$FIXTURES_DIR" ]]; then
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

if [[ ! -s "$REPORT" ]]; then
  printf 'Missing manual QA report: %s\n' "$REPORT" >&2
  exit 1
fi
if ! command -v python3 >/dev/null 2>&1; then
  printf 'python3 is required to write the manual QA evidence packet.\n' >&2
  exit 1
fi

required_checks_file="$(mktemp)"
evidence_checks_file="$(mktemp)"
trap 'rm -f "$required_checks_file" "$evidence_checks_file"' EXIT

bash "$CHECKER" --list-required-checks > "$required_checks_file"
bash "$CHECKER" --list-evidence-checks > "$evidence_checks_file"

python3 - "$REPORT" "$OUT_DIR" "$FIXTURES_DIR" "$required_checks_file" "$evidence_checks_file" <<'PY'
import datetime as dt
import hashlib
import json
import pathlib
import re
import shutil
import sys

report_path = pathlib.Path(sys.argv[1])
out_dir = pathlib.Path(sys.argv[2])
fixtures_dir = sys.argv[3]
required_checks_path = pathlib.Path(sys.argv[4])
evidence_checks_path = pathlib.Path(sys.argv[5])

report_text = report_path.read_text(encoding="utf-8")
required_checks = [
    line.strip()
    for line in required_checks_path.read_text(encoding="utf-8").splitlines()
    if line.strip()
]
evidence_checks = [
    line.strip()
    for line in evidence_checks_path.read_text(encoding="utf-8").splitlines()
    if line.strip()
]
evidence_set = set(evidence_checks)
canonical_prompts = {
    "Card-pack export creates a readable JSON file through the Android file picker.": "TODO evidence: record Android picker target and exported filename.",
    "Card-pack import shows the validation preview before applying a file.": "TODO evidence: use `seemops_qa_card_pack.json`; record preview counts before applying.",
    "Full backup export creates a readable JSON file through the Android file picker.": "TODO evidence: record Android picker target and exported filename.",
    "Full backup import restores cards and settings after preview confirmation.": "TODO evidence: use `seemops_qa_backup.json`; record restored cards/settings signal.",
    "Device-transfer package shares as a JSON attachment through Android's chooser.": "TODO evidence: record chooser target and attachment filename/MIME.",
    "Device-transfer package can be received on another device or emulator and opened/shared directly to Seemops.": "TODO evidence: use `seemops_qa_transfer_package.json`; record receiving device and open/share path.",
    "Received device-transfer package imports through the backup preview flow and restores the expected cards/settings.": "TODO evidence: record preview counts and restored settings/player signal.",
    "Diagnostics report can be shared/exported and does not include card text or player names.": "TODO evidence: record share/export target and private-data spot check.",
    "Last-issue shortcut is disabled without a crash summary, then share/export/delete work when a stored issue exists.": "TODO evidence: record disabled state, seed helper run, and share/export/delete result.",
    "Support request can be prepared through Android's share sheet and does not include card text, player names, or crash stack traces.": "TODO evidence: record share target and private-data spot check.",
    "Privacy policy can be shared from settings through Android's share sheet.": "TODO evidence: record Android share target and policy title/snippet.",
    "Dark, light, and system themes render legibly.": "TODO evidence: record checked theme modes and any device display setting used.",
    "Large font scale does not clip primary setup/game actions, settings actions, player chips, or recap content.": "TODO evidence: record Android font/display size and checked screens.",
    "Icon-only controls and important actions have clear TalkBack labels.": "TODO evidence: record TalkBack labels sampled for top-bar and row actions.",
    "Touch targets remain comfortable on a phone-sized device.": "TODO evidence: record device size and checked compact controls.",
    "Light/dark contrast remains readable in setup, gameplay, entry manager, settings, and dialogs.": "TODO evidence: record screens checked in both light and dark mode.",
}

run_sheet_actions = {
    "Fresh install opens the 18+ first-run setup screen.": "Install or clear the QA build, launch Seemops, and observe the first screen before accepting anything.",
    "Gameplay is blocked until age and safety confirmations are accepted.": "Try to start with one or both first-run confirmations unchecked, then accept both confirmations.",
    "First-run setup can start with the Classic Starter pack.": "Choose the Classic Starter pack in first-run setup, add any needed players, and start the game.",
    "First-run setup can start with another selected built-in starter pack.": "Return to a fresh setup state, choose any non-Classic built-in pack, and start the game.",
    "First-run setup can start with an empty deck.": "Return to a fresh setup state, choose the empty-start path, and confirm the resulting empty/custom-card state.",
    "First-run setup defaults to `Locker` intensity unless changed.": "Open first-run setup on a clean install and check the selected intensity before changing anything.",
    "Standard packs load without duplicates, including the Spieleabend Pack and Feierabend Pack.": "Open pack selection/filter surfaces and confirm the standard packs appear once, including Spieleabend and Feierabend.",
    "Card draw, skip, complete, and undo work.": "Start a playable session, draw a card, use skip, complete a card, then undo and compare the visible state.",
    "Local session recap updates after drawing cards and stays readable on the game screen.": "Draw several cards with players/scores visible, then read the recap panel without scrolling surprises or clipped text.",
    "Pack templates create paused draft cards that can be edited and activated.": "Open Pack-Vorlagen, create a template draft, edit its text/options, then activate it.",
    "Custom card add, edit, pause/reactivate, and delete work from the entry manager.": "Create a custom card, edit it, pause it, reactivate it, and delete it from the entry manager.",
    "Card-pack export creates a readable JSON file through the Android file picker.": "Export a card pack through Android's file picker and open or inspect the exported JSON target.",
    "Card-pack import shows the validation preview before applying a file.": "Import seemops_qa_card_pack.json through Android's file picker and stop at the validation preview before applying.",
    "Full backup export creates a readable JSON file through the Android file picker.": "Export a full backup through Android's file picker and open or inspect the exported JSON target.",
    "Full backup import restores cards and settings after preview confirmation.": "Import seemops_qa_backup.json through Android's file picker, review the preview, confirm, and inspect restored state.",
    "Device-transfer package shares as a JSON attachment through Android's chooser.": "Use the device-transfer share action and inspect Android's chooser target plus the shared attachment details.",
    "Device-transfer package can be received on another device or emulator and opened/shared directly to Seemops.": "Send or open seemops_qa_transfer_package.json on a second device/emulator or share it directly to Seemops.",
    "Received device-transfer package imports through the backup preview flow and restores the expected cards/settings.": "Confirm the received transfer package preview, apply it, and inspect restored cards/settings/player signal.",
    "Diagnostics report can be shared/exported and does not include card text or player names.": "Share or export diagnostics, choose a target, and spot-check the generated text for private card/player data.",
    "Last-issue shortcut is disabled without a crash summary, then share/export/delete work when a stored issue exists.": "Confirm no-crash buttons are disabled, run the seed helper, then test last-issue share, export, and delete.",
    "Support request can be prepared through Android's share sheet and does not include card text, player names, or crash stack traces.": "Prepare a support request through Android's share sheet and spot-check the shared text before sending.",
    "App/version/legal section is visible in settings.": "Open settings and locate the app/version/legal section.",
    "Privacy policy can be viewed from settings and contains the expected no-ads/no-analytics/no-server-collection wording.": "Open the privacy policy from settings and scan for the expected no ads, no analytics, and no server collection commitments.",
    "Privacy policy can be shared from settings through Android's share sheet.": "Use the privacy share action from settings and inspect Android's share sheet target plus the shared title/text.",
    "Round reset clears active card and recap state.": "Create an active round with recap content, run Runde zurücksetzen, and confirm the active card/recap clear.",
    "Score reset clears player score totals without removing players.": "Create player scores, run Scores zurücksetzen, and confirm players remain with cleared totals.",
    "Player reset clears the player panel.": "Create players, run Spieler löschen, and confirm the player panel is empty.",
    "Age/safety reset returns to the first-run gate with start disabled until confirmations are accepted.": "Run Alters-/Sicherheitshinweis zurücksetzen and confirm the app returns to first-run with start disabled until confirmations are checked.",
    "Dark, light, and system themes render legibly.": "Switch through dark, light, and system themes, then inspect setup, game, entry manager, settings, and dialogs.",
    "Large font scale does not clip primary setup/game actions, settings actions, player chips, or recap content.": "Increase Android font/display size, reopen the target screens, and inspect primary actions, settings rows, player chips, and recap.",
    "Icon-only controls and important actions have clear TalkBack labels.": "Enable TalkBack or equivalent accessibility inspection and sample top-bar icons plus row action buttons.",
    "Touch targets remain comfortable on a phone-sized device.": "Use a phone-sized device and tap compact top-bar, row, chip, and dialog controls without precision problems.",
    "Light/dark contrast remains readable in setup, gameplay, entry manager, settings, and dialogs.": "Inspect the named screens and dialogs in both light and dark theme, including disabled/enabled controls.",
    "Store screenshots still match the current UI.": "Compare the six generated Play Store phone screenshots against the current first-run, game, entry, settings, and legal UI.",
}

standard_proofs = {
    "Fresh install opens the 18+ first-run setup screen.": "First-run setup is visible on launch, with no path into gameplay before setup.",
    "Gameplay is blocked until age and safety confirmations are accepted.": "Start remains unavailable until both confirmations are accepted.",
    "First-run setup can start with the Classic Starter pack.": "Game starts and Classic Starter cards are available.",
    "First-run setup can start with another selected built-in starter pack.": "Game starts with the selected non-Classic pack available.",
    "First-run setup can start with an empty deck.": "Game opens in the expected empty/custom-card state without built-in starter cards.",
    "First-run setup defaults to `Locker` intensity unless changed.": "Locker is selected on clean setup before tester input.",
    "Standard packs load without duplicates, including the Spieleabend Pack and Feierabend Pack.": "Each standard pack appears once and can be selected/filtered.",
    "Card draw, skip, complete, and undo work.": "Visible card, completion/skip result, score/session state, and undo result all match the expected flow.",
    "Local session recap updates after drawing cards and stays readable on the game screen.": "Recap totals/recent cards update and remain readable on the phone display.",
    "Pack templates create paused draft cards that can be edited and activated.": "Draft starts paused, edits persist, and activation makes it playable.",
    "Custom card add, edit, pause/reactivate, and delete work from the entry manager.": "Card lifecycle changes are visible in the entry manager and game filters.",
    "App/version/legal section is visible in settings.": "Settings shows the app/version/legal section with current app identity.",
    "Privacy policy can be viewed from settings and contains the expected no-ads/no-analytics/no-server-collection wording.": "Privacy dialog/page contains the expected local-only privacy commitments.",
    "Round reset clears active card and recap state.": "Active card and recap reset without changing unrelated settings.",
    "Score reset clears player score totals without removing players.": "Players remain present and score totals are zero/cleared.",
    "Player reset clears the player panel.": "Player list/panel is empty after reset.",
    "Age/safety reset returns to the first-run gate with start disabled until confirmations are accepted.": "First-run gate appears and start is disabled until confirmations are checked.",
    "Store screenshots still match the current UI.": "Screenshots match the current UI structure and no outdated screen is present.",
}

def clean(value):
    return " ".join(value.strip().split())

def md_cell(value):
    return str(value).replace("|", "\\|").replace("\n", "<br>")

fields = {}
for line in report_text.splitlines():
    match = re.match(r"^- ([^:]+):\s*(.*)$", line)
    if match:
        fields[match.group(1)] = match.group(2).strip()

rows = []
in_table = False
for line in report_text.splitlines():
    if re.match(r"^\|\s*Area\s*\|\s*Check\s*\|\s*Status\s*\|\s*Notes\s*\|", line):
        in_table = True
        continue
    if in_table and re.match(r"^\|\s*-+\s*\|", line):
        continue
    if in_table and not line.startswith("|"):
        in_table = False
    if in_table and line.startswith("|"):
        columns = [clean(column) for column in line.strip().strip("|").split("|")]
        if len(columns) >= 4:
            rows.append({
                "area": columns[0],
                "check": columns[1],
                "status": columns[2],
                "notes": columns[3],
                "high_risk": columns[1] in evidence_set,
                "required": columns[1] in set(required_checks),
            })

missing_required = [check for check in required_checks if check not in {row["check"] for row in rows}]
missing_evidence = [check for check in evidence_checks if check not in {row["check"] for row in rows}]

if out_dir.exists():
    shutil.rmtree(out_dir)
(out_dir / "screenshots").mkdir(parents=True, exist_ok=True)
(out_dir / "files").mkdir(parents=True, exist_ok=True)

generated = dt.datetime.now().astimezone().strftime("%Y-%m-%d %H:%M:%S %Z")

def slug(value):
    slugged = re.sub(r"[^a-z0-9]+", "-", value.lower()).strip("-")
    return slugged[:72] or "evidence"

def prompt_from_notes(check, notes):
    if check in canonical_prompts:
        return canonical_prompts[check]
    if notes.startswith("TODO evidence:"):
        return notes
    if notes:
        return notes
    return "Record concrete device evidence before marking this row Passed."

summary = {
    "generated": generated,
    "report": str(report_path),
    "checklist_rows": len(rows),
    "required_rows": len(required_checks),
    "high_risk_evidence_rows": len(evidence_checks),
    "run_sheet_rows": len(rows),
    "run_sheet_high_risk_rows": sum(1 for row in rows if row["high_risk"]),
    "missing_required_rows": missing_required,
    "missing_high_risk_rows": missing_evidence,
    "fixtures_dir": fixtures_dir,
    "metadata": fields,
}

(out_dir / "README.md").write_text(
    "# Manual QA Evidence Packet\n\n"
    f"Generated: {generated}\n\n"
    f"Source report: `{report_path}`\n\n"
    "This packet does not replace `docs/MANUAL_QA_REPORT.md`. The release gate still requires the report fields, `Result: Passed`, every checklist row marked `Passed`, concrete high-risk Notes evidence, and `SEEMOPS_MANUAL_QA_CONFIRMED=1`.\n\n"
    "## Summary\n\n"
    f"- Checklist rows in report: {len(rows)}\n"
    f"- Required checklist rows expected by checker: {len(required_checks)}\n"
    f"- High-risk evidence rows: {len(evidence_checks)}\n"
    f"- Fixture directory for import/share checks: `{fixtures_dir}`\n"
    f"- Missing required rows: {len(missing_required)}\n"
    f"- Missing high-risk rows: {len(missing_evidence)}\n\n"
    "## Files\n\n"
    "- `checklist-index.md`: all report rows grouped in release-check order, with high-risk rows marked.\n"
    "- `tester-run-sheet.md`: row-by-row tester actions, expected proof, and report-update guidance.\n"
    "- `evidence-notes-template.md`: the exact high-risk rows and the concrete evidence each row should capture before pasting into the report Notes column.\n"
    "- `screenshots/README.md`: suggested screenshot naming for rows where a screenshot helps the reviewer or tester remember what was checked.\n"
    "- `files/README.md`: fixture, export, and share-target evidence naming guidance.\n"
    "- `summary.json`: machine-readable row counts and metadata snapshot.\n\n"
    "Keep screenshots/files privacy-safe: do not capture private card text, player names, phone numbers, email addresses, or unrelated files.\n",
    encoding="utf-8",
)

checklist_lines = [
    "# Manual QA Checklist Index",
    "",
    f"Generated: {generated}",
    "",
    "| # | Area | Risk | Status | Check | Notes |",
    "| --- | --- | --- | --- | --- | --- |",
]
for index, row in enumerate(rows, 1):
    risk = "High-risk evidence" if row["high_risk"] else "Standard"
    checklist_lines.append(
        f"| {index} | {row['area']} | {risk} | {row['status']} | {row['check']} | {row['notes']} |"
    )
(out_dir / "checklist-index.md").write_text("\n".join(checklist_lines) + "\n", encoding="utf-8")

evidence_lines = [
    "# High-Risk Evidence Notes Template",
    "",
    f"Generated: {generated}",
    "",
    "Use these rows while filling the Notes column in `docs/MANUAL_QA_REPORT.md`. Replace each `TODO evidence:` prompt with concrete device evidence before marking the row `Passed`.",
    "",
    "| # | Area | Check | Evidence to capture | Report Notes draft |",
    "| --- | --- | --- | --- | --- |",
]
evidence_index = 0
for row in rows:
    if not row["high_risk"]:
        continue
    evidence_index += 1
    prompt = prompt_from_notes(row["check"], row["notes"])
    evidence_lines.append(
        f"| {evidence_index} | {row['area']} | {row['check']} | {prompt} | TODO: replace with concrete device evidence. |"
    )
(out_dir / "evidence-notes-template.md").write_text("\n".join(evidence_lines) + "\n", encoding="utf-8")

run_sheet_lines = [
    "# Manual QA Tester Run Sheet",
    "",
    f"Generated: {generated}",
    "",
    "Use this run sheet while performing the phone-sized real-device QA pass. It is an action guide; `docs/MANUAL_QA_REPORT.md` remains the release gate.",
    "",
    "Rules for the tester:",
    "",
    "- Leave a row as `TODO` until the action was checked on the QA device.",
    "- Use `Failed` for any mismatch, clipping, inaccessible control, or Android handoff problem.",
    "- For high-risk rows, replace the report's `TODO evidence:` note with concrete proof before marking the row `Passed`.",
    "- Do not capture or paste private card text, player names, phone numbers, email addresses, or unrelated files.",
    "",
    "| # | Area | Risk | Tester action | Expected proof | Report update |",
    "| --- | --- | --- | --- | --- | --- |",
]
for index, row in enumerate(rows, 1):
    action = run_sheet_actions.get(
        row["check"],
        f"Perform this exact checklist item on the QA device: {row['check']}",
    )
    if row["high_risk"]:
        risk = "High-risk evidence"
        expected_proof = prompt_from_notes(row["check"], row["notes"])
        report_update = "Paste concrete device evidence into Notes, then mark `Passed`."
    else:
        risk = "Standard"
        expected_proof = standard_proofs.get(
            row["check"],
            "Observable pass/fail state on the device; add a brief note if anything is ambiguous.",
        )
        report_update = "Mark `Passed` after the action succeeds; leave `TODO` until checked or use `Failed` if it needs a fix."
    run_sheet_lines.append(
        "| {index} | {area} | {risk} | {action} | {proof} | {report_update} |".format(
            index=index,
            area=md_cell(row["area"]),
            risk=md_cell(risk),
            action=md_cell(action),
            proof=md_cell(expected_proof),
            report_update=md_cell(report_update),
        )
    )
(out_dir / "tester-run-sheet.md").write_text("\n".join(run_sheet_lines) + "\n", encoding="utf-8")

screenshot_lines = [
    "# Screenshot Evidence Folder",
    "",
    "Screenshots are optional unless your release owner requires them, but they are useful for high-risk Android system UI and accessibility checks.",
    "",
    "Suggested names:",
    "",
]
for index, row in enumerate(rows, 1):
    if row["high_risk"] or row["area"] in {"First run", "Gameplay", "Accessibility", "Theme"}:
        screenshot_lines.append(f"- `{index:02d}-{slug(row['check'])}.png`: {row['check']}")
(out_dir / "screenshots" / "README.md").write_text("\n".join(screenshot_lines) + "\n", encoding="utf-8")

files_lines = [
    "# File And Share Evidence Folder",
    "",
    "Use this folder for tester notes about Android picker/share-target behavior and any exported filenames. Do not store private user content here.",
    "",
    "Fixture files used during QA:",
    "",
    f"- `{fixtures_dir}/seemops_qa_card_pack.json`: card-pack import preview.",
    f"- `{fixtures_dir}/seemops_qa_backup.json`: full-backup restore preview.",
    f"- `{fixtures_dir}/seemops_qa_transfer_package.json`: device-transfer receive/import flow.",
    "",
    "Suggested notes/files:",
    "",
    "- `card-pack-export.txt`: Android picker target, exported filename, and JSON readability spot check.",
    "- `backup-export.txt`: Android picker target, exported filename, and restore preview signal.",
    "- `transfer-share.txt`: chooser target, attachment filename, MIME type, receiving device, and import preview result.",
    "- `diagnostics-support-privacy-share.txt`: share targets plus private-data spot checks.",
    "- `last-issue.txt`: disabled state, seed helper run, share/export target, and delete result.",
    "",
    "For the last-issue row, first confirm the no-crash buttons are disabled, then run `scripts/seed-manual-qa-last-issue.sh` from the repo or `bash tools/seed-manual-qa-last-issue.sh` from a generated package root.",
]
(out_dir / "files" / "README.md").write_text("\n".join(files_lines) + "\n", encoding="utf-8")

(out_dir / "summary.json").write_text(json.dumps(summary, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

checksum_lines = []
for path in sorted(out_dir.rglob("*")):
    if path.is_file() and path.name != "checksums.sha256":
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        checksum_lines.append(f"{digest}  {path.relative_to(out_dir)}")
(out_dir / "checksums.sha256").write_text("\n".join(checksum_lines) + "\n", encoding="utf-8")
PY

printf 'Manual QA evidence packet written to %s\n' "$OUT_DIR"
