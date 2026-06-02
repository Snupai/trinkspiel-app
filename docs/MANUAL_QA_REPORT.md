# Manual QA Report

Last updated: 2026-06-02

Use this report for the final real-device pass before public release. The automated connected Android UI/import tests are useful, but they do not replace a hands-on check on at least one phone-sized device.

## Required Before Release

- Device model: TODO
- Android version: TODO
- App version: TODO
- Tester: TODO
- Date: TODO
- Result: Not completed

## Checklist

Use `Passed` only after checking the item on a real phone-sized device. Leave `TODO` until it has been checked, and use `Failed` if it needs more work. The release checker requires every checklist row to be exactly `Passed`, verifies that the required 34 named checks are present, and requires concrete Notes evidence for high-risk rows that start with `TODO evidence:`.
The `App version` field must match the current Gradle version exactly, for example `3.0 (3)`, and `Date` must use `YYYY-MM-DD`.
Running `scripts/prepare-manual-qa.sh --update-report` refreshes the device/app/date metadata, writes `build/manual-qa/manual-qa-guide.md`, writes `build/manual-qa/fixtures/`, resets `Result` to `Not completed`, resets every checklist row to `TODO`, and restores the high-risk evidence placeholders so old pass statuses or old device notes cannot be reused by accident.
It also writes `build/manual-qa/evidence/`, an evidence packet with a row-by-row tester run sheet, the high-risk Notes template, screenshot/file naming guidance, and privacy-safe capture rules.
Running `scripts/prepare-manual-qa.sh --guide-only` writes the same tester guide, fixtures, and evidence packet without using ADB, which is useful before a phone is attached.
Running `scripts/prepare-manual-qa-evidence-packet.sh` refreshes only the evidence packet from the current report and the checker-owned required row lists.
Running `scripts/generate-manual-qa-fixtures.sh` refreshes only the known-good card-pack, full-backup, and transfer-package JSON files for Android file-picker/share-target checks.
For the last-issue row, first confirm the no-crash buttons are disabled, then run `scripts/seed-manual-qa-last-issue.sh` on the debug QA install to create a fake local crash summary for share/export/delete checks.
Rows that mention Android chooser, share sheet, file picker, TalkBack, or font scale must be checked through the actual Android system UI on the QA device.

| Area | Check | Status | Notes |
| --- | --- | --- | --- |
| First run | Fresh install opens the 18+ first-run setup screen. | TODO | |
| First run | Gameplay is blocked until age and safety confirmations are accepted. | TODO | |
| First run | First-run setup can start with the Classic Starter pack. | TODO | |
| First run | First-run setup can start with another selected built-in starter pack. | TODO | |
| First run | First-run setup can start with an empty deck. | TODO | |
| First run | First-run setup defaults to `Locker` intensity unless changed. | TODO | |
| Content | Standard packs load without duplicates, including the Spieleabend Pack and Feierabend Pack. | TODO | |
| Gameplay | Card draw, skip, complete, and undo work. | TODO | |
| Gameplay | Local session recap updates after drawing cards and stays readable on the game screen. | TODO | |
| Templates | Pack templates create paused draft cards that can be edited and activated. | TODO | |
| Entry manager | Custom card add, edit, pause/reactivate, and delete work from the entry manager. | TODO | |
| Import/export | Card-pack export creates a readable JSON file through the Android file picker. | TODO | TODO evidence: record Android picker target and exported filename. |
| Import/export | Card-pack import shows the validation preview before applying a file. | TODO | TODO evidence: use `seemops_qa_card_pack.json`; record preview counts before applying. |
| Backup | Full backup export creates a readable JSON file through the Android file picker. | TODO | TODO evidence: record Android picker target and exported filename. |
| Backup | Full backup import restores cards and settings after preview confirmation. | TODO | TODO evidence: use `seemops_qa_backup.json`; record restored cards/settings signal. |
| Transfer | Device-transfer package shares as a JSON attachment through Android's chooser. | TODO | TODO evidence: record chooser target and attachment filename/MIME. |
| Transfer | Device-transfer package can be received on another device or emulator and opened/shared directly to Seemops. | TODO | TODO evidence: use `seemops_qa_transfer_package.json`; record receiving device and open/share path. |
| Transfer | Received device-transfer package imports through the backup preview flow and restores the expected cards/settings. | TODO | TODO evidence: record preview counts and restored settings/player signal. |
| Diagnostics | Diagnostics report can be shared/exported and does not include card text or player names. | TODO | TODO evidence: record share/export target and private-data spot check. |
| Diagnostics | Last-issue shortcut is disabled without a crash summary, then share/export/delete work when a stored issue exists. | TODO | TODO evidence: record disabled state, seed helper run, and share/export/delete result. |
| Support | Support request can be prepared through Android's share sheet and does not include card text, player names, or crash stack traces. | TODO | TODO evidence: record share target and private-data spot check. |
| Legal | App/version/legal section is visible in settings. | TODO | |
| Legal | Privacy policy can be viewed from settings and contains the expected no-ads/no-analytics/no-server-collection wording. | TODO | |
| Legal | Privacy policy can be shared from settings through Android's share sheet. | TODO | TODO evidence: record Android share target and policy title/snippet. |
| Settings | Round reset clears active card and recap state. | TODO | |
| Settings | Score reset clears player score totals without removing players. | TODO | |
| Settings | Player reset clears the player panel. | TODO | |
| Settings | Age/safety reset returns to the first-run gate with start disabled until confirmations are accepted. | TODO | |
| Theme | Dark, light, and system themes render legibly. | TODO | TODO evidence: record checked theme modes and any device display setting used. |
| Accessibility | Large font scale does not clip primary setup/game actions, settings actions, player chips, or recap content. | TODO | TODO evidence: record Android font/display size and checked screens. |
| Accessibility | Icon-only controls and important actions have clear TalkBack labels. | TODO | TODO evidence: record TalkBack labels sampled for top-bar and row actions. |
| Accessibility | Touch targets remain comfortable on a phone-sized device. | TODO | TODO evidence: record device size and checked compact controls. |
| Accessibility | Light/dark contrast remains readable in setup, gameplay, entry manager, settings, and dialogs. | TODO | TODO evidence: record screens checked in both light and dark mode. |
| Store | Store screenshots still match the current UI. | TODO | |

## Final Confirmation

Before running the final release gate, set:

```sh
export SEEMOPS_MANUAL_QA_CONFIRMED=1
```

Only set this after the required fields are filled, `Result` is set to `Passed`, and every checklist row is marked `Passed`. The final release gate validates these fields when `SEEMOPS_MANUAL_QA_CONFIRMED=1`.

You can check the report before the final gate with:

```sh
scripts/check-manual-qa-report.sh --require-confirmation
```
