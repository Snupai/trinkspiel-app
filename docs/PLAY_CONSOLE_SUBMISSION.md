# Play Console Submission Handoff

Last updated: 2026-06-02

Use this as the app-specific source while filling Play Console. It summarizes the current Seemops Trinkspiel behavior and points to the supporting files in this repository. Re-check the live Play Console wording during submission, because Google can rename or reorder fields.

## App Identity

| Field | Value |
| --- | --- |
| App name | Seemops Trinkspiel |
| Package name | `com.snupai.trinkspiel` |
| Version name | `3.0` |
| Version code | `3` |
| Category | Game / Casual or Entertainment |
| Target audience | Adults only / legal drinking age |
| Login or account required | No |
| Internet/server dependency | No gameplay server dependency; local play works offline. INTERNET is used only for optional user-configured manual Backend-Sync and Backend-Invite actions |

## Store Listing Inputs

| Play Console area | Repository source |
| --- | --- |
| Short description | `docs/PLAY_STORE_METADATA.md` |
| Full description | `docs/PLAY_STORE_METADATA.md` |
| Release notes / What's new | `docs/PLAY_STORE_METADATA.md` and `docs/RELEASE_NOTES.md` |
| Feature graphic | `docs/store-assets/feature-graphic.png` |
| Store icon | `docs/store-assets/store-icon.png` |
| Phone screenshots | `docs/store-assets/screenshots/phone/*.png` |
| Content review evidence | `docs/CONTENT_REVIEW.md` |
| Manual QA evidence | `docs/MANUAL_QA_REPORT.md`, with generated `manual-qa-evidence/` packet in the handoff package |
| Privacy policy | `https://snupai.github.io/trinkspiel-app/privacy-policy.html` |

## App Access

- No login is required.
- No reviewer account is needed.
- The first launch shows the 18+ safety setup before gameplay.
- The app can be reviewed offline after install.

## Ads

- Ads: No.
- Ads SDKs: None.
- Ad ID usage: No known app usage.

## Data Safety Answers

| Question | Current app answer |
| --- | --- |
| Data collected by developer | No |
| Data shared with third parties by developer | No |
| Analytics or tracking SDKs | No |
| Account creation | No |
| Server-side gameplay storage | No |
| Data encrypted in transit | Not applicable for local gameplay data. Optional manual Backend-Sync should use a user-configured HTTPS endpoint; there is no built-in developer gameplay server |
| Data deletion | Local data can be deleted in-app where applicable, reset by app data clearing/uninstall, and exported files can be deleted wherever the user saved them |

Local-only data stored on the device:

- User-created card text, categories, packs, enabled/paused state, and drink values.
- Player names, teams, scores, drinks, round state, and recent draw history.
- App settings including theme, mode, intensity, custom category choices, first-run status, and age/safety confirmations.
- Latest local crash summary if a crash occurs.

User-initiated sharing/export:

- Card-pack JSON export/share.
- Full backup JSON export/import.
- Offline device-transfer JSON package.
- Backend-Invite JSON package for joining a shared card library.
- Optional manual Backend-Sync to a user-configured Backend URL/token.
- Diagnostics JSON export/share.
- Focused last-issue JSON export/share when a local crash summary exists.
- Support request text prepared through Android's share sheet.

Important wording for forms: the developer does not collect or upload this data. Users can choose Android file-picker or share-sheet targets themselves, and optional Backend-Sync only contacts the URL/token the user enters. Those transfers are user-initiated outside app-controlled server collection.

Supporting source: `docs/DATA_SAFETY.md`.

## Privacy Policy

- GitHub Pages hosts `docs/privacy-policy.html` at `https://snupai.github.io/trinkspiel-app/privacy-policy.html`.
- Set `SEEMOPS_PRIVACY_POLICY_URL=https://snupai.github.io/trinkspiel-app/privacy-policy.html`.
- Run `scripts/check-privacy-policy-url.sh`.
- The final gate rejects localhost/private/example/test URLs, follows redirects, and requires the hosted page to contain the expected Seemops privacy-policy commitments and exactly match `docs/privacy-policy.html`.

## Content Rating And Declarations

Declare:

- Alcohol/drinking-game theme.
- Adult/legal-drinking-age audience.
- No real-money gambling.
- No simulated gambling economy.
- No public user-generated-content marketplace.
- No user account, public profile, social feed, chat, or matchmaking.
- No location collection.
- No health, medical, financial, government, or news functionality.
- No ads, analytics, or third-party tracking.

The app contains party prompts and drinking-game mechanics. Built-in cards are reviewed in `docs/CONTENT_REVIEW.md`, and automated guardrails run in the unit test suite.

## Final Upload Checks

Run these before upload:

```sh
scripts/release-blockers.sh --no-fail
scripts/finalize-play-store-release.sh --capture-screenshots
```

The final package is upload-ready only when release signing is configured, the hosted privacy-policy URL validates, manual QA is confirmed, screenshots are current, and `scripts/finalize-play-store-release.sh` exits successfully.
