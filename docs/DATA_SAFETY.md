# Play Console Data Safety Draft

This draft matches the current app behavior as of 2026-06-02.

## Collection And Sharing

- Data collected by developer: No
- Data shared with third parties by developer: No
- App includes ads: No
- Analytics/SDK tracking: No
- Account required: No
- Data encrypted in transit: Not applicable for local play. The optional manual Backend-Sync sends card-library snapshots only to the user-configured Backend URL; production use should configure an HTTPS endpoint.
- User can request data deletion: Data is stored locally; users can delete entries, reset settings, uninstall the app, or delete exported files wherever they saved them.

## Local-Only Data

The app stores the following on the device only:

- User-generated card text and pack names
- Local card-library owner and contributor profile names/ids
- Player names
- Scores, teams, drinks, round state, and recent local draw history
- App settings such as theme, mode, intensity, custom category choices, first-run setup status, and age/safety notice acceptance
- Latest local crash summary, if a crash occurs

## User-Initiated File Export/Import

The user can explicitly export, import, open, or share JSON files through Android's system file picker or share sheet. These files can contain card text, pack names, local card-library owner and contributor profile names/ids, player names, scores, teams, and settings. The app does not upload these files; sharing depends on the Android target the user selects. The device-transfer package is generated as a temporary local JSON attachment and uses the same full-backup JSON format. If a JSON backup is opened or shared to Seemops, the app previews it before applying changes.

The user can explicitly share, create, revoke, or open a Backend-Invite JSON file for a shared card library. This invite contains the Backend URL, Sync Token, library owner metadata, contributor metadata, and token role. Creating a new contributor invite sends the requested contributor name and invite role to the user-configured Backend URL using the configured admin token. Checking Backend memberships requests membership metadata from the user-configured Backend URL, such as contributor name, contributor id, role, token source, and creation time. Revoking a generated invite sends the selected membership id to the user-configured Backend URL using the configured admin token. Invites and membership actions are used only when the user chooses to create, share, open, inspect, or revoke them.

The optional manual Backend-Sync sends and receives card-library snapshots through the user-configured Backend URL/token. These snapshots can include card text, pack names, level, drink count, category, review status, owner, contributor, and update metadata. Rejecting a remote-backed review card with an admin Backend token can send the selected remote card id to the user-configured Backend URL so the rejected card does not return on the next sync. The app does not send player names, scores, teams, round state, diagnostics, or gameplay history through Backend-Sync.

The user can also explicitly share or export a diagnostics JSON file from settings, or a focused last-issue JSON file when a local crash summary exists. These files include app version, Android version, device model, gameplay counts, selected settings, and the latest crash stack trace if present. They do not include card text or player names.

The support action in settings prepares a user-initiated plain-text request with app version, Android version, device model, gameplay counts, selected settings, and the latest crash class/message if present. It does not include card text, player names, or the crash stack trace.

## Play Console Form Notes

- If Play Console asks whether the app collects user-generated content, answer based on "collected by the developer." Current answer: no, because the content remains local unless the user manually exports or shares a file outside the app.
- If Play Console asks whether users can share data from the app, note optional user-initiated JSON sharing/export, including transfer packages for moving data to another device and Backend-Invite packages for joining a shared card library.
- If Play Console asks about age/content, disclose alcohol/drinking-game theme and target adults/legal drinking age only.
- Use `docs/CONTENT_REVIEW.md` as the built-in card content review record.
