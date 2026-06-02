# Release Audit

Last updated: 2026-06-02

## Current Build Status

- Debug APK builds.
- Minified/shrunk release APK builds.
- Release AAB builds.
- Unit tests pass.
- Debug lint reports no issues.
- Android test APK compiles.
- Connected tests pass on `Medium_Phone_API_36.1` via `scripts/run-connected-ui-tests.sh`: 26 tests, 0 skipped, 0 failed. The current release-gate evidence covers transfer-package attachment URIs, direct incoming-share preview, incoming import confirmation, starter draw/session recap, pack template drafts, skip/complete/undo gameplay, active card-library switching, theme/privacy controls, settings release options, entry CRUD controls, external-contributor review defaults, player/scoreboard controls, last-issue controls, settings reset controls, round/score reset controls, repository level-table moves, and FileProvider share paths.
- Release AAB is currently unsigned until real signing credentials are configured.
- Release signing config self-check sees the default keystore file, but still needs the store password, key alias, and key password.
- Store screenshots are current with UI source files: six 1080x2400 phone screenshots were regenerated with `scripts/capture-store-screenshots.sh`, and `scripts/check-store-screenshot-polish.sh` verifies clean demo-mode status bars.

## Implemented App Scope

- Offline drinking-game card draw flow
- Safety notice
- First-run 18+ setup gate before gameplay
- 128-card built-in pack library, including Spieleabend Pack and Feierabend Pack
- First-run built-in starter-pack picker plus empty-start path
- Built-in card content review checklist and automated safety guardrails
- Custom card CRUD with connected coverage for add, pause/reactivate, edit, and delete controls
- Search, sort, filter, import preview, export, share, pause/activate, and bulk visible-card actions
- Pack templates for paused, editable draft packs with category and intensity guidance
- Modes, intensity, pack toggles, and custom categories
- Safer first-run default intensity with higher-intensity modes left as explicit choices
- Players, player rename, teams, scoreboard, manual adjustments, skip, undo, reset, and connected coverage for player clearing plus the first-run gate reset
- Local session recap for current round totals, recent drawn cards, and top player standings
- Accessibility and UI polish for scalable setup/game action buttons, wrapped long settings actions, player chips, non-clickable card metadata pills, collision-resistant header/player/scoreboard rows, item-specific entry-manager action labels, and explicit status/navigation bar contrast
- Full app backup/restore with confirmation preview before applying imports
- Offline device-transfer package share/import path using a file-backed full-backup JSON attachment, Android share/open intents, and Android file pickers
- Polished custom theme, tighter component shapes, and dynamic color settings
- Privacy/safety settings section
- In-app app/version/legal section and shareable privacy policy
- Local diagnostics report export/share, focused last-issue export/share, latest-crash capture, and connected coverage for last-issue disabled/enabled/delete states
- Support request share path with privacy-preserving technical summary
- Store/privacy docs and release signing helper scripts
- Ready-to-host static privacy policy page
- Static privacy-policy hosting bundle generator with exact-match `privacy-policy.html`, `index.html`, README instructions, and checksum evidence
- Hosted privacy-policy gate with shared validation helpers that reject localhost/private/example/test URLs, follows redirects, verifies public HTTPS reachability, checks expected Seemops privacy-policy commitments, and requires exact hosted HTML parity with `docs/privacy-policy.html`
- Dedicated privacy-policy URL checker for validating local and hosted policy content before the final gate
- Play Store screenshot capture script and generated phone screenshots, including settings legal/privacy screen and automated clean-status-bar verification
- Play Store feature graphic, store icon, and matching launcher icon source/render scripts
- Play Store upload package script with checksums and captured release/preflight status
- Release-owner brief generator that writes root `OWNER_BRIEF.md` with manifest status, blocker next actions, and signing/privacy/manual-QA handoff paths
- Play Store package verifier-tool snapshot inside each handoff package for later audit
- Play Store package verifier for generated handoff folders, checksums, required artifacts, artifact freshness evidence, local-ready blocker rows, status files, privacy copy, manual-QA guide, store assets, screenshots, and upload-ready manifest gates
- Upload owner signoff checker for the generated package's top-level upload/no-upload decision across `MANIFEST.md`, `OWNER_BRIEF.md`, `status/release-blockers.json`, and package self-check status
- Final Play Store release wrapper that runs signing checks, optional screenshot capture, build/test tasks, final preflight, and package generation
- Compact release blocker report in Markdown and JSON for final handoff evidence, including APK/AAB artifact freshness
- Structured release-blocker JSON parsing for manifest generation and package verification
- Release signing config self-check that validates visible signing inputs, local signing-file permissions, private-key alias presence, temporary signing with the configured key password, and Android debug signing-material rejection before the final bundle build
- Non-secret release-signing handoff generator with current signing-check output, environment/properties templates, checksum evidence, and rebuild/verify steps for the trusted signing machine
- Manual QA report template and final-gate confirmation flag
- Manual QA prep helper that installs the current debug build, clears app data, launches the app, and records device/app metadata without marking checks passed
- Manual QA fixture generator and package fixtures for card-pack import, full-backup import, and transfer-package receive/import checks
- Manual QA evidence packet generator and checker with exact 34-row/16-high-risk counts, a row-by-row tester run sheet, Notes prompts, screenshot/file naming guidance, privacy-safe capture rules, summary validation, and checksum evidence
- Manual QA last-issue helper that seeds a fake local crash summary into the debug QA install after the no-crash disabled state has been checked
- Manual QA evidence gate that validates filled device/tester fields, the required 34 named checklist rows, and concrete Notes evidence for 16 high-risk Android handoff/accessibility rows when QA is confirmed
- Final release runbook for signing, hosted privacy-policy verification, manual QA, packaging, and upload
- Play Console submission handoff for app access, ads, data safety, content rating, declarations, store assets, and upload checks
- Play Console handoff checker that verifies app identity/version, store assets, data-safety claims, privacy wording, dependencies, permissions, and automatic-backup settings stay aligned
- Current app status snapshot for the plain-language capability/blocker/next-priority handoff
- ASAP feature and release backlog for upload blockers and next-priority product work

## Release Artifacts

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK(s): `app/build/outputs/apk/release/*.apk` (signed or unsigned depending on release signing config)
- Unsigned release AAB: `app/build/outputs/bundle/release/app-release.aab`
- Android test APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`
- Store feature graphic: `docs/store-assets/feature-graphic.png`
- Store icon candidate: `docs/store-assets/store-icon.png`
- Launcher icons: `app/src/main/res/mipmap-*/ic_launcher.webp` and adaptive vector drawables
- Play Store package folder: `build/play-store-upload/`

## Required Before Public Release

- Configure real signing credentials using environment variables, Gradle properties, or `signing/release-signing.properties`.
- Run `scripts/prepare-release-signing-handoff.sh` when handing signing work to the credential owner.
- Run `scripts/check-release-signing-config.sh` and confirm it reports `READY`.
- Set `SEEMOPS_PRIVACY_POLICY_URL` to the hosted public HTTPS privacy-policy URL and confirm the hosted page is reachable with the expected Seemops privacy-policy commitments and exactly matches `docs/privacy-policy.html`.
- Run `scripts/prepare-privacy-policy-hosting.sh` before hosting so the public URL can be deployed from the exact generated static bundle.
- Run `scripts/check-privacy-policy-url.sh` and confirm the privacy-policy URL check passes.
- Complete `docs/MANUAL_QA_REPORT.md` with filled fields, `Result: Passed`, and every checklist row marked `Passed`, then set `SEEMOPS_MANUAL_QA_CONFIRMED=1`.
- Use `scripts/prepare-manual-qa.sh --update-report --tester "Your Name"` on the QA device to prepare a clean install and collect metadata before the hands-on checklist.
- Run `scripts/verify-release-ready.sh` and confirm the signed AAB passes verification.
- Do final manual QA on at least one phone-sized device, replacing every high-risk `TODO evidence:` note with concrete device evidence before marking that row `Passed`.
- Host/publish `docs/privacy-policy.html` at the store listing privacy-policy URL.
- Keep `docs/ASAP_FEATURES.md` current as release blockers and near-term feature priorities change.
- Follow `docs/FINAL_RELEASE_RUNBOOK.md` for the final upload sequence.
- Use `docs/PLAY_CONSOLE_SUBMISSION.md` to fill and cross-check Play Console app access, ads, data safety, content rating, declarations, and store listing inputs.
- Run `scripts/check-play-console-handoff.sh` before submission, or rely on `scripts/verify-release-ready.sh`/`scripts/finalize-play-store-release.sh` to run it.
- Run `scripts/finalize-play-store-release.sh --capture-screenshots` and confirm it exits upload-ready.
- Run `scripts/release-blockers.sh --no-fail` to review the compact blocker summary.

## External Blockers

- Release signing store password, key alias, and key password are not known to this workspace.
- Hosted privacy-policy URL is not known to this workspace.
- Real-device manual QA confirmation is not known to this workspace.
