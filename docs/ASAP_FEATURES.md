# ASAP Feature And Release Backlog

Last updated: 2026-06-02

This list is the source of truth for the next highest-priority work before and immediately after the first public Play Store release.

## P0: Required Before Play Store Upload

- Sign the release AAB with real release credentials.
  - Status: ready as of 2026-06-02. A local release keystore was created at `signing/seemops-release-20260602.keystore`, the ignored `signing/release-signing.properties` contains the local signing values, `scripts/check-release-signing-config.sh` reports `READY`, and `scripts/verify-release-ready.sh --skip-build` verifies `app/build/outputs/bundle/release/app-release.aab` is signed with non-debug signing material.
  - Evidence needed: keep the keystore and ignored signing properties backed up privately; rerun `scripts/check-release-signing-config.sh`, rebuild `:app:bundleRelease`, and rerun `scripts/verify-release-ready.sh --skip-build` after signing changes.
- Host the privacy policy.
  - Status: GitHub Pages is configured for `https://snupai.github.io/trinkspiel-app/` from the repo `docs/` folder. The Play Console privacy-policy target is `https://snupai.github.io/trinkspiel-app/privacy-policy.html`.
  - Evidence needed: set `SEEMOPS_PRIVACY_POLICY_URL=https://snupai.github.io/trinkspiel-app/privacy-policy.html`, then confirm `scripts/check-privacy-policy-url.sh` reports the hosted page is reachable with the expected Seemops privacy-policy commitments and exactly matches `docs/privacy-policy.html`.
- Complete real-device manual QA.
  - Status: `docs/MANUAL_QA_REPORT.md` is structured for device/tester details plus 34 per-check `Passed` rows, including specific Android chooser/file-picker, transfer, diagnostics, reset, and accessibility checks. High-risk rows now carry `TODO evidence:` note prompts, and the checker rejects a final `Passed` state until those prompts are replaced with concrete device evidence. `scripts/prepare-manual-qa.sh` can write a tester guide, import fixtures, and evidence packet without ADB, or install/launch the QA build, fill metadata, write the guide/fixtures/evidence packet, and reset result/checklist statuses plus evidence placeholders for a fresh pass. The evidence packet now includes `tester-run-sheet.md` with row-by-row actions, expected proof, and report-update guidance. `scripts/check-manual-qa-evidence-packet.sh` verifies the packet's exact 34-row/16-high-risk structure. The Play Store handoff package includes a generated `docs/manual-qa-guide.md`, verified `manual-qa-fixtures/`, and verified `manual-qa-evidence/`. The real phone-sized pass is not confirmed.
  - Evidence needed: QA report fields are filled with the current Gradle app version and a `YYYY-MM-DD` date after a fresh `scripts/prepare-manual-qa.sh --update-report` run, `Result: Passed`, all 34 named checklist rows are present and `Passed`, the 16 high-risk rows have concrete Notes evidence gathered with the evidence packet, `scripts/check-manual-qa-evidence-packet.sh` passes, `scripts/check-manual-qa-report.sh --require-confirmation` passes, and `SEEMOPS_MANUAL_QA_CONFIRMED=1` passes the final release gate and finalizer.
- Fill the Play Console submission forms from the release handoff.
  - Status: `docs/PLAY_CONSOLE_SUBMISSION.md` now consolidates app access, ads, data safety, content rating, declarations, store assets, privacy-policy, optional user-configured Backend-Sync/Backend-Invite internet usage, and upload-check answers. `scripts/check-play-console-handoff.sh` passes against the current metadata, data-safety copy, privacy copy, and manifest `INTERNET` permission. The generated Play Store package now adds root `OWNER_BRIEF.md` and `tools/check-upload-owner-signoff.sh` so the release owner can see current blockers and run a final upload/no-upload signoff before Play Console.
  - Evidence needed: `scripts/check-play-console-handoff.sh` passes, root `OWNER_BRIEF.md` mirrors `MANIFEST.md`, `scripts/check-upload-owner-signoff.sh --require-upload-ready <generated-package-folder>` passes, and final Play Console answers are cross-checked against `docs/PLAY_CONSOLE_SUBMISSION.md`, `docs/DATA_SAFETY.md`, and `docs/PLAY_STORE_METADATA.md` before submission.
- Refresh connected Android UI/import test evidence after the latest UI cleanup.
  - Status: ready as of 2026-06-02. `scripts/run-connected-ui-tests.sh` passes on `Medium_Phone_API_36.1` with 26 connected Android tests, including transfer-package preview/confirmation, starter draw/session recap, pack template drafts, skip/complete/undo gameplay, active library switching, theme/privacy controls, entry CRUD, player/scoreboard, last-issue, round/score reset, settings reset controls, repository level-table moves, and FileProvider share paths.
  - Evidence needed: keep rerunning `scripts/run-connected-ui-tests.sh` after any app or androidTest source change, then confirm `scripts/verify-release-ready.sh --skip-build` reports connected Android tests current with source files.
- Refresh Play Store phone screenshots after the final UI cleanup.
  - Status: ready as of 2026-06-02. `scripts/capture-store-screenshots.sh` regenerated all six 1080x2400 phone screenshots after the latest UI cleanup, and `scripts/check-store-screenshot-polish.sh` reports clean demo-mode status bars.
  - Evidence needed: recapture with `scripts/finalize-play-store-release.sh --capture-screenshots` or `scripts/capture-store-screenshots.sh` after any UI source change, then confirm `scripts/verify-release-ready.sh --skip-build` reports screenshots current with UI source files.

## P1: Add Soon After Release

- Crash/error export shortcut.
  - Status: settings now includes direct "last issue" share/export actions that are enabled when a local crash summary exists; connected UI coverage verifies the no-crash disabled state plus stored-crash enable/delete behavior.
  - The shortcut writes a focused `seemops.last_issue` JSON file with technical state and the latest crash stack trace, without card text or player names.
  - A manual QA helper, `scripts/seed-manual-qa-last-issue.sh`, can seed a fake local crash summary into the debug QA install after the disabled state is checked.
  - Next: confirm the share/export handoff with Android's chooser during real-device QA.
- Safer first-game defaults.
  - Status: first-run setup now starts on `Locker` and explains that groups can increase intensity later.
  - Next: confirm this on real-device QA with the rest of `docs/MANUAL_QA_REPORT.md`.
- Core gameplay flow.
  - Status: connected UI coverage now verifies card draw, skip, complete, player score update, undo, active card-library switching, and loading a starter pack into an otherwise empty active library through the live game screen. Gameplay now also has explicit question-level toggles, so Level 1/2/3 tables can be combined or excluded independently of mode/intensity, and the live deck plus pack toggles are scoped to the active card-user library. The level copy now mirrors the intended escalation: `1 Einfach`, `2 Saufen`, and `3 Fast ausziehen`, with descriptions shown in card editing, settings, and gameplay.
  - Next: confirm the same flow by hand during real-device QA, especially library switching, question-level toggles, touch feel, and scroll position.
- Better onboarding pack choice.
  - Status: first-run setup now includes a compact starter-pack picker for all built-in packs, plus the empty-start path.
  - Next: confirm chosen-pack setup on real-device QA.
- In-app import validation preview.
  - Status: card-pack imports and full backup restores now show a confirmation preview before applying changes.
  - The preview summarizes new cards, skipped duplicate/invalid cards, pack names, and backup settings.
  - Connected coverage now verifies an incoming transfer-package preview can be confirmed and the imported card appears in the entry manager.
  - Manual QA fixtures are generated by `scripts/generate-manual-qa-fixtures.sh` and included in the Play Store handoff package for Android file-picker/share-target checks.
  - Next: confirm Android file-picker backup/card imports during real-device QA.
- Entry management controls.
  - Status: connected UI coverage now verifies adding a custom card, pausing/reactivating it, editing it, and deleting it from the entry manager. The entry manager now shows counts, filters, packs, visible cards, and bulk actions for the active card-user library. Its summary stats wrap on narrow screens, filter chips allow longer names, add/edit level/category chips wrap instead of horizontal-scrolling, card rows show level/category/drinks/pack/user/review/sync as scannable metadata pills, and pending review cards use a clear approval icon. Import/edit duplicate detection, edit guards, pack toggles, and pack deletion are also scoped by card-user library, so different libraries can keep the same card text and pack names independently while each library still avoids duplicates and owner-local pack actions. Single-card, bulk, and review actions now resolve requested ids through the current stored active-library entries before applying changes, which protects against stale or spoofed entry objects. Generic activate/pause bulk actions skip pending review cards, so external contributions require explicit review approval before entering gameplay. Card edits update active owner/contributor settings when those profiles change, while pure text edits keep the current active contributor untouched. Normal imports from external contributors now land paused in review by default before they can enter gameplay, and the import preview distinguishes those external review cards from ordinary owner-authored imports. Pending review cards are summarized by contributor and Level 1/2/3 counts so an owner can jump straight to a person's submitted cards. Repository instrumentation now verifies that manual edits and imported replacements move cards cleanly between the separate Level 1/2/3 tables without duplicating them in the combined deck.
  - Next: confirm Android file-picker import/export handoff and active-library entry-manager filters plus owner-scoped pack toggles/deletion during real-device QA.
- Accessibility polish pass.
  - Status: primary game/setup actions now use scalable minimum heights, long settings action labels can wrap cleanly, player chips wrap instead of hiding off-screen, the main card uses non-clickable info pills for metadata, header/player/scoreboard rows protect long names and labels from overlapping, entry-manager row actions now expose item-specific TalkBack labels, and system bars now use explicit theme-aware contrast.
  - Next: validate touch targets, font scaling, TalkBack labels, and contrast on a real device.
- Theme and legal controls.
  - Status: connected UI coverage now verifies dark, light, and system theme controls keep settings usable and that the in-app privacy policy dialog opens with the expected local privacy copy.
  - Next: confirm final color contrast and Android share-sheet privacy handoff during real-device QA.
- Settings reset controls.
  - Status: settings now has connected UI coverage proving `Runde zurücksetzen` clears the active card/recap state, `Scores zurücksetzen` clears player score totals, `Spieler löschen` clears the player panel, and `Alters-/Sicherheitshinweis zurücksetzen` returns the app to the first-run gate with start disabled until confirmations are accepted.
  - Next: confirm the reset controls visually during real-device QA.

## P2: Product Expansion

- Optional cloud/device-to-device transfer.
  - Status: implemented as an offline "Gerätewechsel" settings section using user-initiated transfer-package sharing/import, plus an optional manual Backend-Sync section for shared card libraries.
  - The transfer package is a file-backed JSON attachment using the full backup format, including known local card-user profiles and active gameplay question-level selection, and does not upload data to a server.
  - Received JSON backups can be opened or shared directly to Seemops and still go through the same preview-confirm import flow.
  - Connected coverage verifies direct Seemops receive/open handling through preview confirmation and local card import.
  - Sync-ready card metadata, local card-user profile switching, profile chips in add/edit card dialogs, active owner/contributor id attribution for cards, invite profile persistence, `CardSyncEngine`, an Android REST client, a preview/confirm Backend-Sync settings flow with visible token role, Backend-Invite JSON packages, admin-created contributor invites, admin-only Backend membership summaries, generated invite revocation, remote review rejection, and `scripts/card-sync-server.mjs` now cover create/update/pull/conflict/delete/invite/membership/revoke work for a shared card-library backend. The reference backend also supports token-bound contributor identity for write requests, persists generated invite tokens, hides secret tokens in membership summaries, revokes generated invite tokens, forces contributor creates/edits into review, lets admins delete rejected remote cards, and blocks identity-bound contributor tokens from overwriting cards added by someone else. `HttpRemoteCardSyncReferenceBackendTest` now verifies the Android HTTP client against the local reference backend. See `docs/CARD_SYNC_BACKEND_PLAN.md`.
  - Next: confirm the local card-user profile picker, Android share-sheet handoff on two real devices or a device plus emulator, run the preview/confirm Backend-Sync, remote review approve/reject, admin-created Backend-Invite, Backend membership-check, and generated invite-revoke flows manually on a device/emulator against the reference backend, then pick the production backend/account model.
- More built-in packs.
  - Status: implemented two additional standard packs, `Spieleabend Pack` and `Feierabend Pack`, bringing built-in content to 128 cards across 8 packs.
  - Content remains covered by the same safety guardrails used by `BuiltInPacksTest`.
  - Next: confirm the new packs appear in first-run selection, entry manager loading, and game pack filters during real-device QA.
- Pack templates.
  - Status: implemented in the entry manager as local "Pack-Vorlagen" that create paused draft cards.
  - The templates include mode/intensity guidance plus balanced category mixes so users can edit before activating cards.
  - Connected coverage now requires the pack-template draft creation flow before release.
  - Next: confirm template edit/activation touch flow during real-device QA.
- Session recap.
  - Status: implemented as a local "Rückblick" panel with current round totals, recent drawn cards, and top player standings.
  - Connected coverage now requires the starter draw/session recap flow before release.
  - Next: confirm the recap layout during real-device QA and keep it local-only without analytics or remote storage.

## Not Planned For The First Release

- Ads, analytics, account login, remote tracking, or server-side gameplay storage.
- Automatic background cloud sync.
- Online multiplayer.
- Public content marketplace.
- Automatic crash reporting to a third-party service.
