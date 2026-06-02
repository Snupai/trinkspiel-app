# Current App Status

Last updated: 2026-06-02

This is the short, plain-language status snapshot for Seemops Trinkspiel `3.0 (3)`. For the full audit trail, see `docs/RELEASE_AUDIT.md`; for the working backlog, see `docs/ASAP_FEATURES.md`.

## Overall Status

The app is feature-complete locally for the current Play Store handoff path, but it is not upload-ready yet.

Local build, unit-test, lint, connected Android UI/import tests, Play Console handoff, package integrity evidence, and Play Store phone screenshots are current. The remaining release blockers are external: real release signing credentials, a hosted public HTTPS privacy-policy URL, and a passed real-device manual QA report.

## What The App Can Do Now

- Run as an offline-first drinking game with no account, ads, analytics, gameplay server, or public marketplace.
- Gate first launch behind the adult/safety setup before gameplay.
- Start from a selected built-in starter pack or from an empty deck.
- Use 128 reviewed built-in cards across 8 packs.
- Add, edit, pause/reactivate, delete, search, sort, filter, import, export, share, and bulk-manage custom cards in the active card-user library; duplicate checks, edit guards, pack toggles/deletion, entry-manager counts, gameplay level counts, sync-open counts, and bulk/review actions are scoped to each library. Single-card, bulk, and review actions resolve requested ids against the current stored active-library cards before changing anything, so stale or spoofed entry objects cannot affect another library. Generic activate/pause bulk actions skip pending review cards; review cards require explicit review approval. Card edits keep owner/contributor settings aligned when those profiles actually change, without changing the active contributor for a pure text edit. Normal card-pack imports reset foreign remote-sync metadata while keeping contributor attribution; imported cards from external contributors are paused into review by default and surfaced separately in the import preview. The entry manager groups pending review cards by contributor and level for quicker owner review, while backup/sync imports can explicitly preserve remote metadata and review state.
- Save local card-user profiles, switch the active library owner/contributor in settings, pick known profiles directly while adding or editing cards, keep cards attributed to the selected owner/contributor ids for review/sync, and scope the playable deck plus owner-specific pack filters to the active library.
- Create paused draft cards from pack templates, then let the user edit before activation.
- Draw cards from the active card-user library with mode, intensity, question-level, pack, category, player, team, scoreboard, skip, complete, undo, reset, and session recap support. The three playable card levels are surfaced as `1 Einfach`, `2 Saufen`, and `3 Fast ausziehen`, with short descriptions in add/edit, settings, and gameplay selection.
- Export and restore full local backups with a confirmation preview, including known local card-user profiles.
- Share/import an offline device-transfer JSON package through Android's file/share flow.
- Keep sync-ready card metadata, configure an optional manual Backend-Sync endpoint/token/role, export/share active-library Card-Sync packages, share/open Backend-Invite JSON packages for joining a shared library, persist invite owner/contributor profiles as known card users, create contributor invites from an admin token, inspect Backend membership metadata, revoke generated invite memberships, fetch/push shared card-library changes, reject remote-backed review cards, and preview remote inserts/updates/conflicts plus pending local uploads before the user confirms the sync. Read-only roles preview/pull without offering local uploads.
- Run against `scripts/card-sync-server.mjs`, a local reference backend that stores shared card libraries as JSON, enforces bearer-token library roles, can mint and revoke persisted contributor invite tokens from an admin token, exposes admin-only membership summaries without returning secret tokens, binds contributor identity to write tokens, forces contributor writes into review, lets admins approve or delete cards, and blocks contributor tokens from overwriting other contributors' existing cards.
- View/share privacy policy text, diagnostics, support request text, and a focused last-issue report when a local crash summary exists.
- Switch between system, light, dark, and dynamic color theme options.
- Produce Play Store assets: feature graphic, store icon, launcher icons, metadata, data-safety copy, privacy policy, release notes, upload package, root `OWNER_BRIEF.md` handoff summary, and six current 1080x2400 phone screenshots with clean demo-mode status bars.

## What The App Cannot Do Yet

- It cannot be uploaded to Play Console as-is, because `app-release.aab` is currently unsigned.
- It cannot pass the final release gate until `SEEMOPS_PRIVACY_POLICY_URL` points to the hosted public HTTPS privacy policy; `scripts/prepare-privacy-policy-hosting.sh` now prepares the exact static HTML bundle for that upload.
- It cannot be marked ready for public release until real-device manual QA is completed with concrete Notes evidence on the 16 high-risk rows and confirmed with `SEEMOPS_MANUAL_QA_CONFIRMED=1`; `scripts/prepare-manual-qa-evidence-packet.sh` now prepares the evidence packet for that pass.
- It does not provide online multiplayer, production cloud sync, real accounts, ads, analytics, automatic third-party crash reporting, or a public card marketplace.
- The Backend-Sync path is manual and optional. It has preview/confirm UI, Backend-Invite package sharing/import, admin-created contributor invites, generated invite revocation, admin-only membership summaries on the local reference backend, and Android HTTP client coverage, but no hosted production backend, login flow, real account model, automatic background sync, or server-side gameplay storage yet.
- It does not automatically verify Android share sheets on physical devices; those handoffs still need real-device QA confirmation.

## What Should Happen Next

1. Run `scripts/prepare-release-signing-handoff.sh`, configure real release signing credentials on the trusted signing machine, and rebuild the release bundle.
2. Run `scripts/prepare-privacy-policy-hosting.sh`, then host the exact generated privacy-policy HTML at the final public HTTPS privacy-policy URL.
3. Run `scripts/prepare-manual-qa.sh --update-report --tester "Your Name"` on a phone-sized device, then use `build/manual-qa/evidence/tester-run-sheet.md` and the rest of `build/manual-qa/evidence/` to complete every manual QA row by hand and replace high-risk `TODO evidence:` notes with concrete device evidence.
4. Run `scripts/finalize-play-store-release.sh --capture-screenshots` after signing, privacy URL, and manual QA are complete.
5. Open the generated package's `OWNER_BRIEF.md`, then confirm `MANIFEST.md` says `Upload-ready status: ready`, `Blockers remaining: 0`, and `Package integrity status: pass`.
6. Upload the signed `android/app-release.aab` from the generated Play Store package folder.

## ASAP Feature And QA Priorities

- Confirm the expanded 34-row manual QA checklist, especially Android chooser/file-picker handoffs for backup import/export, card-pack import/export, transfer-package sharing, privacy sharing, diagnostics, support request, and last-issue export on a real device, using the generated tester run sheet and evidence packet for concrete Notes evidence on the 16 high-risk rows.
- Validate accessibility on a real device: touch targets, large font scale, TalkBack labels, contrast, and long text wrapping.
- Confirm the first-game flow by hand: safety gate, starter-pack choice, question-level toggles, first draw, skip, complete, undo, score changes, reset controls, and session recap readability.
- Confirm entry-management polish by hand: active-library counts/filters, pack templates, draft editing, activation, pause/reactivate, owner-scoped pack toggles/deletion, delete, and import preview.
- Confirm two-device or device-plus-emulator transfer package behavior before relying on it as the main migration path.
- Choose and implement the production card-library backend/account model after the current local reference backend proves the sync, invite, and membership contracts.
- Keep built-in pack expansion conservative until the first release is accepted; no ads, analytics, accounts, cloud storage, online multiplayer, marketplace, or automatic crash reporting are planned for the first release.
