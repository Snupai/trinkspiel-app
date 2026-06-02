# Privacy Policy

Last updated: 2026-06-02

Seemops Trinkspiel is an offline-first party game. The app does not require an account for local play, does not show ads, and does not use analytics. The app does not send gameplay data to a server.

## Data Stored On Device

The app stores game cards, imported packs, local card-library owner and contributor profile names/ids, player names, scores, teams, game settings, first-run setup status, age/safety confirmation status, round state including recent local draw history, and the latest local crash summary if a crash occurs.

## Backups And Imports

The app can export, import, open, or share JSON files when the user explicitly chooses a file or Android share target. These files may contain custom cards, local card-library owner and contributor profile names/ids, player names, scores, teams, and settings. The device-transfer package is generated as a temporary local JSON attachment and uses the same full-backup format. If a JSON backup is opened or shared to Seemops, the app previews it before applying changes. The user controls where exported files are saved and with whom they are shared.

The optional Backend-Sync feature lets a user store a Backend URL and Sync Token locally. When the user manually starts Backend-Sync, the app sends and receives card snapshots for the current card library, including card text, level, drink count, category, pack, review status, owner, contributor, and update metadata. Backend URL and Sync Token are not written into regular backups or device-transfer packages. If the user rejects a remote-backed review card with an admin Backend token, the app can send the selected remote card id to the user-configured Backend URL so the rejected card does not return on the next sync. If the user explicitly shares a Backend Invite, that invite contains the Backend URL, Sync Token, library owner metadata, contributor metadata, and token role so another person can join the shared library. If the user creates a contributor invite, checks Backend memberships, or revokes a generated invite, the app contacts the user-configured Backend URL with the configured token and exchanges only invite or membership metadata such as contributor name, contributor id, membership id, role, source, and creation time.

The settings screen can also create a diagnostics JSON file when the user explicitly chooses to share or export it, or a focused last-issue JSON file when a local crash summary exists. These files include technical app state such as app version, Android version, device model, gameplay counts, selected settings, and the latest crash stack trace if present. They do not include card text or player names.

The settings screen can prepare a support request when the user explicitly chooses to share it. The support request includes a short technical summary such as app version, Android version, device model, gameplay counts, selected settings, and the latest crash class/message if present. It does not include card text, player names, or the crash stack trace.

Automatic Android cloud backup is disabled in the app manifest. Manual JSON export is the supported backup path.

## Data Sharing

The app does not sell, rent, transmit, or share personal data. If a user shares an exported card pack, backup file, transfer package, Backend Invite, diagnostics report, last-issue file, or support request, that sharing happens through the Android apps the user selects.

## Safety

The app is intended only for adults of legal drinking age. A first-run setup screen asks users to confirm they are at least 18 before gameplay. Players should drink responsibly, drink water, take breaks, and stop playing whenever they want.

## Contact

For privacy questions, use the support contact listed on the app store page.
