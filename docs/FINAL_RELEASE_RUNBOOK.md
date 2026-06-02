# Final Release Runbook

Last updated: 2026-06-02

Use this runbook for the final Play Store upload pass. The app is locally release-ready only after every command below passes and `scripts/verify-release-ready.sh` reports success.

## 1. Confirm Local Build Evidence

Run:

```sh
scripts/release-status.sh
scripts/release-blockers.sh --no-fail
```

`scripts/release-status.sh` does not start ADB by default. If you want it to actively query attached Android devices, run `SEEMOPS_RELEASE_STATUS_CHECK_ADB=1 scripts/release-status.sh`.

Expected before the external blockers are cleared:

- Unit tests show at least 105 clean tests.
- Built-in content guardrails show at least 5 clean tests.
- Debug APK, release APK, Android test APK, and release AAB are current with the app build inputs checked by `scripts/verify-release-ready.sh`.
- Connected Android tests show at least 26 clean tests and are current with app/androidTest source, including:
  - incoming transfer-package share preview
  - incoming transfer-package import confirmation
  - transfer-package JSON attachment URI
  - starter pack draw and session recap
  - pack template draft creation
  - skip/complete/undo gameplay
  - theme/privacy controls
  - settings release options
  - entry CRUD controls
  - player/scoreboard controls
  - last-issue controls
  - settings reset controls
  - round/score reset controls
- Store screenshots and graphics are present, and screenshots are newer than the current UI source files with clean demo-mode status bars verified by `scripts/check-store-screenshot-polish.sh`.
- Play Console handoff shows `READY`, meaning the submission answers still match the app identity, store/privacy docs, dependencies, permissions, and backup settings.
- Release AAB is unsigned until real signing is configured.

For the final all-in-one upload gate, use:

```sh
scripts/finalize-play-store-release.sh --capture-screenshots
```

It runs signing input checks, optional screenshot capture, the release build/test set, final preflight, Play Store packaging, and exits successfully only when the package is actually upload-ready.

The generated Play Store package also includes root `OWNER_BRIEF.md`, plus `status/release-blockers.md` and `status/release-blockers.json`, for the compact owner handoff. Its `release_artifacts` row must be `ready`, proving the APK/AAB files are present and current with app build inputs.
Use `docs/PLAY_CONSOLE_SUBMISSION.md` as the app-specific Play Console form handoff while filling app access, ads, data safety, content rating, declarations, and final upload checks.
The final preflight also runs `scripts/check-play-console-handoff.sh` so package/version, app name, no-ads/no-analytics/no-account claims, local-only privacy wording, dependencies, permissions, and store asset references stay aligned.

## 2. Configure Release Signing

Create or provide a real release keystore outside version control. One local helper is:

```sh
scripts/create-release-keystore.sh
```

The helper requires `keytool`, refuses to overwrite an existing keystore, and creates the keystore with private local permissions.

For a non-secret credential-owner handoff folder, run:

```sh
scripts/prepare-release-signing-handoff.sh
```

The generated `build/release-signing-handoff/` folder contains the current signing-check output, safe environment/properties templates, and the exact rebuild/verify steps. It does not include a keystore or real signing passwords.

Configure signing with one of these options:

- Environment variables: `SEEMOPS_RELEASE_STORE_FILE`, `SEEMOPS_RELEASE_STORE_PASSWORD`, `SEEMOPS_RELEASE_KEY_ALIAS`, `SEEMOPS_RELEASE_KEY_PASSWORD`
- Gradle properties using the same key names
- Ignored local file: `signing/release-signing.properties`

If you use local files, keep the keystore and ignored properties file private:

```sh
chmod 600 signing/seemops-release.keystore
[[ ! -f signing/release-signing.properties ]] || chmod 600 signing/release-signing.properties
```

Then run:

```sh
scripts/check-release-signing-config.sh
./gradlew :app:bundleRelease --no-daemon
scripts/verify-release-ready.sh --skip-build
```

The signing config check must report `READY`, including private file permissions, a private-key alias, a successful temporary signing probe, and no Android debug signing material. The release preflight signing section must report that the release bundle is signed with non-debug signing material and verified.
The generated Play Store package also includes `release-signing-handoff/` so the credential owner can repeat the signing setup without hunting through the repo.

## 3. Host Privacy Policy

Run the static hosting helper, then host `build/privacy-policy-hosting/privacy-policy.html`, `docs/privacy-policy.html`, or the generated package's `privacy-hosting/privacy-policy.html` at the final Play Console privacy-policy URL. The hosted file must be the exact release HTML, not a rewritten page with similar text.

```sh
scripts/prepare-privacy-policy-hosting.sh
```

Set the final HTTPS URL:

```sh
export SEEMOPS_PRIVACY_POLICY_URL="https://privacy.your-real-domain.com/privacy-policy"
```

Run:

```sh
scripts/check-privacy-policy-url.sh
scripts/verify-release-ready.sh --skip-build
```

The privacy-policy URL check and the release preflight privacy-policy section must show that the URL is public HTTPS, not localhost/private/example/test, reachable after redirects, contains the expected Seemops privacy-policy commitments, and exactly matches `docs/privacy-policy.html`. The generated Play Store package also includes `privacy-hosting/` with exact-match HTML, checksum evidence, and hosting instructions.

## 4. Complete Real-Device QA

Install the current build on at least one phone-sized Android device. To prepare the device and collect the metadata fields, run:

```sh
scripts/prepare-manual-qa.sh --update-report --tester "Your Name"
```

Before the device is available, you can generate the same tester-facing checklist guide without touching ADB:

```sh
scripts/prepare-manual-qa.sh --guide-only --tester "Your Name"
```

The connected-test, screenshot, manual-QA, and release-status scripts auto-detect common Android SDK locations, including `$ANDROID_HOME`, `$ANDROID_SDK_ROOT`, `~/Library/Android/sdk`, and `~/Android/Sdk`, before checking for `adb`. Release status only probes attached devices when ADB is already running or `SEEMOPS_RELEASE_STATUS_CHECK_ADB=1` is set.

This helper installs the current debug build, clears app data, launches the app, fills the device/app/date metadata fields, and writes `build/manual-qa/manual-qa-device-info.md`. It never marks the result or checklist rows as passed.
It also writes `build/manual-qa/manual-qa-guide.md`, `build/manual-qa/fixtures/`, and `build/manual-qa/evidence/`, grouping the 34 required checks, writing a row-by-row tester run sheet, carrying the `TODO evidence:` prompts for the 16 high-risk rows, preparing card-pack import/full-backup/transfer-package fixtures, and giving the tester privacy-safe screenshot/file naming guidance. From a generated package root, use `manual-qa-fixtures/` for import checks and `manual-qa-evidence/tester-run-sheet.md` plus the rest of `manual-qa-evidence/` for high-risk Notes evidence prompts.
For the last-issue row, first confirm the no-crash buttons are disabled, then run `scripts/seed-manual-qa-last-issue.sh` from the repo, or `bash tools/seed-manual-qa-last-issue.sh` from a generated package root, to seed a fake local crash summary into the debug QA install.
When `--update-report` is used, it also resets `Result` to `Not completed` and every checklist status to `TODO`, so the report starts fresh for the current device/build.
The Play Store handoff package also includes `docs/manual-qa-guide.md` generated from the current checklist and `manual-qa-evidence/` generated from the checker-owned required row lists. `scripts/check-manual-qa-evidence-packet.sh` verifies that the evidence packet has 34 indexed rows, a 34-row tester run sheet, 16 high-risk prompts, fixture references, last-issue helper guidance, exact summary counts, and checksum evidence. The package verifier runs the same checker and also checks that the guide contains the current app version plus high-risk Android system UI/accessibility prompts.

Complete `docs/MANUAL_QA_REPORT.md`:

- Fill device model, Android version, app version, tester, and date.
- Keep app version exactly aligned with the current Gradle version, for example `3.0 (3)`, and use `YYYY-MM-DD` for the date.
- Set `Result: Passed`.
- Mark every checklist row as exactly `Passed`; any `TODO`, `Failed`, blank, or custom status keeps the release blocked.
- Replace each high-risk row's `TODO evidence:` note with concrete device evidence before marking it `Passed`; silent pass rows for Android share sheets, file pickers, transfer, diagnostics, support, privacy sharing, theme, or accessibility checks keep the release blocked.

Then set:

```sh
export SEEMOPS_MANUAL_QA_CONFIRMED=1
```

Run:

```sh
scripts/check-manual-qa-report.sh --require-confirmation
scripts/verify-release-ready.sh --skip-build
```

The manual QA checker and release preflight must confirm the filled fields, passed checklist rows, concrete high-risk evidence notes, and confirmation flag.

## 5. Build Final Package

After signing, privacy URL, manual QA, and final screenshot capture are complete, run:

```sh
scripts/finalize-play-store-release.sh --capture-screenshots
```

The final package is upload-ready only when:

- `status/verify-release-ready.txt` reports success.
- `MANIFEST.md` says `Release AAB signing status: signed`.
- `status/check-release-signing-config.txt` confirms the configured key password can sign a temporary verification JAR.
- `release-signing-handoff/check-release-signing-config.txt` captures the current non-secret signing checker output, and `release-signing-handoff/checksums.sha256` verifies.
- `MANIFEST.md` says `Release artifacts status: ready`.
- `MANIFEST.md` says `Upload-ready status: ready`.
- `MANIFEST.md` says `Package integrity status: pass`.
- `MANIFEST.md` says `Blockers remaining: 0`.
- `MANIFEST.md` includes the final hosted privacy-policy URL.
- `status/check-privacy-policy-url.txt` confirms the hosted privacy-policy content exactly matches `docs/privacy-policy.html`.
- `privacy-hosting/privacy-policy.html` and `privacy-hosting/index.html` match `docs/privacy-policy.html`, and `privacy-hosting/checksums.sha256` verifies.
- `MANIFEST.md` says `Manual QA status: confirmed`.
- `MANIFEST.md` says `Package completeness check exit code: 0`.
- `OWNER_BRIEF.md` mirrors the manifest upload-ready status, blocker count, and package integrity status, and keeps the "Do not upload" gate visible.
- `status/release-blockers.json` includes `release_artifacts` with status `ready`.
- `status/release-blockers.json` includes `play_console_handoff`, `connected_android_tests`, and `store_screenshots` with status `ready`.
- `status/release-blockers.md` agrees with `status/release-blockers.json` on blocker count and row statuses.
- `MANIFEST.md` `Blockers remaining` matches `status/release-blockers.json` `blocker_count`.
- `status/verify-release-ready.txt` reports the APK/AAB artifacts are current with app build inputs.
- `status/verify-release-ready.txt` reports the store screenshots are current with UI source files.
- `status/verify-release-ready.txt` includes `docs/CURRENT_APP_STATUS.md` in the documentation preflight.
- `docs/CURRENT_APP_STATUS.md` is included in the package docs for the plain-language app status and ASAP priorities.
- `docs/manual-qa-guide.md` is included in the package docs for the real-device tester handoff.
- `manual-qa-fixtures/` is included in the package for card-pack, full-backup, and transfer-package manual QA import checks.
- `manual-qa-evidence/` is included in the package for the tester run sheet, high-risk Notes prompts, screenshot/file naming guidance, and checksum evidence.
- `tools/seed-manual-qa-last-issue.sh` is included in the package for the last-issue manual QA row.
- `tools/check-upload-owner-signoff.sh` is included in the package for the final owner-facing upload/no-upload decision from `MANIFEST.md`, `OWNER_BRIEF.md`, and `status/release-blockers.json`.
- `tools/check-play-store-package.sh`, `tools/check-upload-owner-signoff.sh`, `tools/check-manual-qa-report.sh`, `tools/check-manual-qa-evidence-packet.sh`, `tools/prepare-manual-qa-evidence-packet.sh`, `tools/prepare-release-owner-brief.sh`, and the other helper scripts are included in the package and match the current repo verifier scripts.
- From the generated package root, `bash tools/check-play-store-package.sh .` passes for a package-local self-audit.
- From the generated package root, `bash tools/check-upload-owner-signoff.sh --require-upload-ready .` passes before upload.
- `docs/PLAY_CONSOLE_SUBMISSION.md` is included in the package docs and has been used for the Play Console form pass.
- `status/check-play-console-handoff.txt` reports success.
- `status/check-manual-qa-report.txt` reports success.
- `status/check-manual-qa-report.txt` confirms `High-risk evidence-note rows present: 16`.
- `status/check-play-store-package.txt` reports success.
- `status/check-upload-owner-signoff.txt` reports success, and `MANIFEST.md` says `Upload owner signoff check exit code: 0`.
- `scripts/finalize-play-store-release.sh` exits with `Final release package is upload-ready.`

Upload `android/app-release.aab` from the generated package folder to Play Console.
For an explicit package-only check, run `scripts/check-play-store-package.sh --require-upload-ready <generated-package-folder>`.

## Do Not Upload If

- The AAB is unsigned.
- The privacy URL is missing, unreachable, or does not serve the Seemops privacy policy.
- Store screenshots are older than the current UI source files.
- `docs/MANUAL_QA_REPORT.md` still has `TODO` or `Failed` rows.
- `scripts/verify-release-ready.sh` exits non-zero.
