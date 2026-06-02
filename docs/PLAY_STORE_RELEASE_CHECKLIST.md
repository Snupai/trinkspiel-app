# Play Store Release Checklist

## Build

- Create a real release keystore outside version control. You can run `scripts/create-release-keystore.sh`; it requires `keytool`, refuses to overwrite an existing keystore, and creates the keystore with private local permissions.
- Run `scripts/prepare-release-signing-handoff.sh` when handing signing work to the trusted machine that owns the real credentials; it writes safe templates and current signing-check output without including secrets.
- Configure signing in one of three ways:
  - Gradle properties, for example `-PSEEMOPS_RELEASE_STORE_FILE=...`
  - Environment variables: `SEEMOPS_RELEASE_STORE_FILE`, `SEEMOPS_RELEASE_STORE_PASSWORD`, `SEEMOPS_RELEASE_KEY_ALIAS`, `SEEMOPS_RELEASE_KEY_PASSWORD`
  - Ignored local file: run `scripts/write-signing-properties.sh` or copy `signing/release-signing.properties.example` to `signing/release-signing.properties`
- Run `scripts/check-release-signing-config.sh` and confirm the visible signing inputs are complete before building the final bundle.
- Confirm the signing config check reports that the configured key password can sign a temporary verification JAR.
- Confirm local keystore/properties permissions are private (`chmod 600 ...`); the signing checker blocks group/world-readable signing material.
- Confirm the generated package includes `release-signing-handoff/README.md`, `release-signing-handoff/check-release-signing-config.txt`, and `release-signing-handoff/checksums.sha256`.
- Confirm the generated package includes root `OWNER_BRIEF.md` and use it as the first release-owner handoff page.
- Run `scripts/release-blockers.sh --no-fail` for the compact remaining-blockers summary.
- Confirm the `release_artifacts` row is `ready`.
- Run `scripts/verify-release-ready.sh`.
- Confirm `scripts/verify-release-ready.sh` reports the APK/AAB artifacts are current with app build inputs.
- Run `scripts/finalize-play-store-release.sh --capture-screenshots` for the final all-in-one signing, build/test, preflight, screenshot, and package gate.
- Run `scripts/prepare-play-store-upload.sh` only when you need to regenerate the handoff folder without the full final wrapper.
- Upload the signed AAB from `app/build/outputs/bundle/release/app-release.aab`.

The default keystore path is `signing/seemops-release.keystore`, relative to the project root. Real keystores and `signing/release-signing.properties` are ignored by `.gitignore`.

## Store Listing

- Use `docs/PLAY_STORE_METADATA.md` for app name, short description, full description, tags, release notes, and screenshot checklist.
- Use `docs/PLAY_CONSOLE_SUBMISSION.md` as the one-page handoff for Play Console app access, ads, data safety, content rating, declarations, and upload checks.
- Run `scripts/check-play-console-handoff.sh` after changing app identity, versioning, store metadata, data-safety wording, privacy copy, dependencies, or permissions.
- Use `docs/CURRENT_APP_STATUS.md` for the short current-state answer before handoff: what works, what is blocked, and what should happen next.
- Use `docs/ASAP_FEATURES.md` to track required upload blockers and next-priority product work.
- Use `docs/FINAL_RELEASE_RUNBOOK.md` for the final signing, hosted privacy-policy, QA, and upload sequence.
- Category: Game / Casual or Entertainment.
- Age audience: adults only / legal drinking age.
- Generate the store graphics with `scripts/generate-store-assets.sh`; outputs live in `docs/store-assets/`.
- Generate matching Android launcher icons with `scripts/generate-launcher-icons.sh`.
- Review built-in card content with `docs/CONTENT_REVIEW.md`.
- Include screenshots for game, entry manager, settings, backup/privacy, support, and legal/privacy-policy sections.
- Generate current phone screenshots with `scripts/capture-store-screenshots.sh` after the final UI changes; outputs live in `docs/store-assets/screenshots/phone/`. The final release gate fails if screenshots are older than the UI source files or do not have clean demo-mode status bars.

## Privacy And Data Safety

- Run `scripts/prepare-privacy-policy-hosting.sh` and publish the generated exact-match `privacy-policy.html` or `index.html` from `build/privacy-policy-hosting/`, or publish `docs/privacy-policy.html` directly.
- Run `scripts/check-privacy-policy-url.sh` after setting `SEEMOPS_PRIVACY_POLICY_URL`; it validates the local HTML, rejects localhost/private/example/test URLs, follows redirects, and checks the hosted HTTPS page.
- Set `SEEMOPS_PRIVACY_POLICY_URL` to the hosted privacy-policy URL before running the final `scripts/verify-release-ready.sh` gate. The gate fetches the URL and checks that the hosted page contains the expected Seemops privacy-policy commitments and exactly matches `docs/privacy-policy.html`.
- Use `docs/DATA_SAFETY.md` while filling out Play Console's data-safety form.
- Cross-check Play Console form answers against `docs/PLAY_CONSOLE_SUBMISSION.md` before submitting the release.
- Declare no ads, no analytics, no account, and no server-side data collection.
- Declare local storage for user-generated content and app activity.
- Declare optional user-initiated file sharing/export for card packs, backups, transfer packages, diagnostics, and last-issue files.

## Manual QA

- Run `scripts/release-status.sh` for a local readiness snapshot.
- Use `SEEMOPS_RELEASE_STATUS_CHECK_ADB=1 scripts/release-status.sh` only when you want the snapshot to actively probe attached Android devices.
- Confirm the `Play Console handoff` section is `READY` before using `docs/PLAY_CONSOLE_SUBMISSION.md` in Play Console.
- Run `scripts/finalize-play-store-release.sh --capture-screenshots` for the final upload gate.
- Run `scripts/prepare-play-store-upload.sh --skip-build` after the final build to create the Play Console handoff folder.
- Confirm the generated `MANIFEST.md` says `Release artifacts status: ready`, `Package integrity status: pass`, and `Upload-ready status: ready`.
- Confirm root `OWNER_BRIEF.md` mirrors the manifest blocker count, links to signing/privacy/QA handoff folders, and says not to upload until all final gates are ready.
- Confirm the generated package includes `docs/manual-qa-guide.md` for the real-device tester handoff.
- Confirm the generated package includes `manual-qa-evidence/README.md`, `manual-qa-evidence/tester-run-sheet.md`, `manual-qa-evidence/evidence-notes-template.md`, and `manual-qa-evidence/checksums.sha256` for high-risk Notes evidence handoff.
- Confirm `status/release-blockers.json` includes `release_artifacts` with status `ready`.
- Confirm `status/release-blockers.json` keeps `play_console_handoff`, `connected_android_tests`, and `store_screenshots` at `ready`.
- Run `scripts/check-play-store-package.sh --require-upload-ready <generated-package-folder>` on the final handoff folder before upload.
- Run `scripts/check-upload-owner-signoff.sh --require-upload-ready <generated-package-folder>` before upload for the owner-facing manifest/brief/blocker signoff.
- Confirm the generated package includes `status/check-upload-owner-signoff.txt`, and that `MANIFEST.md` says `Upload owner signoff check exit code: 0`.
- From inside the generated handoff folder, run `bash tools/check-play-store-package.sh .` to confirm the copied verifier-tool snapshot can audit the package locally.
- From inside the generated handoff folder, run `bash tools/check-upload-owner-signoff.sh .` to confirm the copied owner-signoff checker agrees with the package status.
- Follow `docs/FINAL_RELEASE_RUNBOOK.md` for the final upload sequence.
- Host the generated package's root `privacy-policy.html` file, or host `docs/privacy-policy.html` directly.
- Confirm the generated package includes `privacy-hosting/privacy-policy.html`, `privacy-hosting/index.html`, and `privacy-hosting/checksums.sha256` for the static privacy-policy hosting handoff.
- Run `scripts/generate-store-assets.sh`.
- Run `scripts/generate-launcher-icons.sh`.
- Run built-in content checks through `./gradlew :app:testDebugUnitTest --no-daemon`.
- Run `scripts/run-connected-ui-tests.sh` with a phone or emulator attached after the latest app/androidTest source changes.
- Run `scripts/capture-store-screenshots.sh` with a phone or emulator attached.
- Run `scripts/prepare-manual-qa.sh --guide-only --tester "Your Name"` if you want a tester-facing `build/manual-qa/manual-qa-guide.md`, `build/manual-qa/fixtures/`, and `build/manual-qa/evidence/` before the phone is attached.
- Run `scripts/prepare-manual-qa.sh --update-report --tester "Your Name"` on the phone-sized QA device to install the build, clear app data, launch the app, fill only the manual-QA metadata fields, reset stale evidence notes, and write the current guide, fixtures, and evidence packet.
- Use `build/manual-qa/fixtures/`, or `manual-qa-fixtures/` from a generated package root, for card-pack import, full-backup import, and transfer-package receive/import checks.
- Use `build/manual-qa/evidence/tester-run-sheet.md` plus the rest of `build/manual-qa/evidence/`, or `manual-qa-evidence/tester-run-sheet.md` from a generated package root, for row-by-row actions, high-risk Notes prompts, and privacy-safe screenshot/file naming guidance.
- Run `scripts/check-manual-qa-evidence-packet.sh` to verify the evidence packet has 34 checklist rows, a 34-row tester run sheet, 16 high-risk prompts, exact summary counts, and valid checksums.
- After confirming the last-issue buttons are disabled with no crash summary, run `scripts/seed-manual-qa-last-issue.sh` on the debug QA install, or `bash tools/seed-manual-qa-last-issue.sh` from a generated package root, then verify last-issue share/export/delete.
- Complete `docs/MANUAL_QA_REPORT.md` on at least one phone-sized device with every checklist row marked exactly `Passed`, replace every high-risk `TODO evidence:` note with concrete device evidence, set `Result: Passed`, then set `SEEMOPS_MANUAL_QA_CONFIRMED=1` for the final release gate.
- Run `scripts/check-manual-qa-report.sh --require-confirmation` before the final gate.
- Fresh install opens the 18+ first-run setup screen.
- Gameplay is blocked until age and safety confirmations are accepted.
- First-run setup can start with the Classic Starter pack, another selected built-in pack, or an empty deck.
- Standard packs load without duplicates, including the Spieleabend Pack and Feierabend Pack.
- Card draw, skip, complete, and undo work.
- Local session recap updates after drawing cards and stays readable on the game screen.
- Pack templates create paused draft cards that can be edited and activated.
- Entry add/edit/delete/pause/export/import work, and import preview appears before applying a file.
- Full backup export/import restores cards and settings after preview confirmation.
- Device-transfer package can be shared as a JSON attachment, received on another device, opened/shared directly to Seemops, and imported through the same backup preview flow.
- Diagnostics report and last-issue shortcut can be shared/exported from settings and do not include card text or player names.
- Support request can be prepared from settings and does not include card text, player names, or crash stack traces.
- Privacy policy can be viewed/shared from settings.
- Built-in card content stays inside `docs/CONTENT_REVIEW.md` safety bounds.
- Settings reset actions work.
- Dark/light/system themes render legibly.
- Large font scale does not clip primary setup/game actions, and icon-only controls have clear TalkBack labels.

## Known External Requirement

Release signing cannot be completed until a real keystore password and key password are available. The final release gate also requires `SEEMOPS_PRIVACY_POLICY_URL` to point at a reachable public HTTPS hosted privacy policy with the expected Seemops commitments, and `SEEMOPS_MANUAL_QA_CONFIRMED=1` after a filled, passed real-device QA report. `scripts/verify-release-ready.sh` exits with a clear failure while any external item is missing.
