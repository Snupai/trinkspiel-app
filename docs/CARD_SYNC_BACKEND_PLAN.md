# Card Sync Backend Plan

Last updated: 2026-06-02

This app is still offline-first. It now has an optional manual REST backend connector for shared card libraries, plus the local JSON sync package for file-based exchange.

## Current Code Contract

- Local cards live in three Room tables by level: `drink_level_1_entries`, `drink_level_2_entries`, and `drink_level_3_entries`.
- Gameplay still reads the combined DAO view, so the game can mix all levels after filtering.
- Known card users live in the local `card_users` table. Settings can save/switch the current library owner and active contributor from those profiles, including backend invite account ids.
- New manually added cards use the active owner/contributor ids from settings, not only the display names. The add/edit card dialogs offer known owner/contributor profile chips so users can pick a stored backend/invite identity without retyping it. Review defaults compare normalized ids so equal display names with different backend accounts still go to review.
- Every card has:
  - `ownerUserId` / `ownerName`: the user library the card belongs to.
  - `contributorUserId` / `contributorName`: the user who added the card.
  - `remoteId`: backend card id once known.
  - `updatedAtMillis`: last local or remote card update timestamp.
  - `syncStatus`: `local`, `dirty`, or `synced`.
- `CardSyncEngine.plan(...)` compares local cards with remote snapshots and returns:
  - `createRemote`: local cards with no `remoteId`.
  - `updateRemote`: dirty local cards that should be pushed.
  - `insertLocal`: remote cards missing locally.
  - `updateLocal`: newer remote cards that can safely replace clean local copies.
  - `markLocalSynced`: local dirty cards that already match remote content.
  - `conflicts`: cards changed both locally and remotely.
  - `skippedRemoteCards`: invalid or wrong-owner remote snapshots.
- `CardSyncPackage` can export/import local JSON snapshots using the same `RemoteCardSnapshot` shape. This remains file-based and user-initiated.
- `HttpRemoteCardSyncApi` can fetch a library from a configured REST endpoint and push `createRemote` / `updateRemote` work as a batch.
- `HttpRemoteCardSyncApi` can ask an admin backend token to create a contributor Backend-Invite token, inspect admin-only library membership summaries, and revoke generated invite memberships.
- `scripts/card-sync-server.mjs` is a dependency-free local reference backend for the same REST contract. It stores library JSON under `build/card-sync-server` by default, persists generated invite tokens in `generated-tokens.json`, and enforces bearer-token membership rules.

## Suggested Backend Shape

A backend can mirror the local level split or store all cards in one table plus a `questionLevel` column. If it mirrors the local split, expose a combined read endpoint for play/import and route writes by level.

Minimum remote card fields:

```text
remoteId
text
drinks
category
packName
isEnabled
isPendingReview
questionLevel
ownerUserId
ownerName
contributorUserId
contributorName
updatedAtMillis
```

Recommended rules:

- Users can read cards where `ownerUserId` matches a library they are allowed to play/edit.
- Users can create cards for a library they are allowed to contribute to.
- Contributor-scoped writes should become `pendingReview = true` and `enabled = false` server-side, even if the client tries to upload them as playable. Admin-level membership is the moderation path for approving cards.
- Contributor-scoped write access should only update cards originally contributed by that same user; owner/moderation workflows should use an admin-level membership.
- Contributors should be stored separately from owners so "added by other people" remains visible.
- Server writes should return `remoteId` and the final `updatedAtMillis`.
- Conflict resolution should not silently overwrite local dirty cards when the server copy is newer.

## REST Contract Used By The App

Configure the app with a backend base URL, for example `https://example.com/api`. The current library owner is sent in the URL path. If a Sync-Token is configured, the app sends `Authorization: Bearer <token>`.

Fetch cards:

```text
GET /libraries/{ownerUserId}/cards
```

The response can be either a raw card array or an object with a `cards` array. Each card must include a non-empty `remoteId`.

Push local changes:

```text
POST /libraries/{ownerUserId}/cards:batchUpsert
Content-Type: application/json
```

Request shape:

```json
{
  "cards": [
    {
      "clientLocalId": 123,
      "operation": "create",
      "card": {
        "text": "Example",
        "drinks": 2,
        "category": "challenge",
        "packName": "Eigene Karten",
        "enabled": true,
        "pendingReview": false,
        "questionLevel": 2,
        "ownerUserId": "local_user_...",
        "ownerName": "WG Bibliothek",
        "contributorUserId": "local_user_...",
        "contributorName": "Mika",
        "updatedAtMillis": 1760000000000
      }
    }
  ]
}
```

Response shape:

```json
{
  "cards": [
    {
      "clientLocalId": 123,
      "card": {
        "remoteId": "server-card-id",
        "text": "Example",
        "drinks": 2,
        "category": "challenge",
        "packName": "Eigene Karten",
        "enabled": true,
        "pendingReview": false,
        "questionLevel": 2,
        "ownerUserId": "local_user_...",
        "ownerName": "WG Bibliothek",
        "contributorUserId": "local_user_...",
        "contributorName": "Mika",
        "updatedAtMillis": 1760000001000
      }
    }
  ]
}
```

The app uses `clientLocalId` only to replace the correct local row with the returned `remoteId`, final timestamp, and `syncStatus = synced`. Manual Backend-Sync first fetches the remote library and shows the same sync preview used by local card-sync packages. The user confirms remote inserts/updates, selected conflict replacements, and pending local uploads before the app applies local changes and sends create/update requests. The app stores the configured token role locally for UI clarity; `read` roles do not offer local uploads in the preview.

Delete remote cards:

```text
POST /libraries/{ownerUserId}/cards:batchDelete
Content-Type: application/json
Authorization: Bearer <admin-token>
```

Request shape:

```json
{
  "remoteIds": ["server-card-id"]
}
```

Response shape:

```json
{
  "deletedRemoteIds": ["server-card-id"],
  "skippedRemoteIds": ["missing-card-id"]
}
```

The Android app uses this when an admin rejects remote-backed review cards. Local-only review cards are still deleted locally. A non-admin local reject can remove the local copy, but the remote card can return on the next sync because server moderation requires admin access.

Create a contributor invite:

```text
POST /libraries/{ownerUserId}/invites
Content-Type: application/json
Authorization: Bearer <admin-token>
```

Request shape:

```json
{
  "libraryOwnerName": "WG Bibliothek",
  "contributorName": "Mika",
  "role": "write"
}
```

Response shape:

```json
{
  "invite": {
    "type": "seemops.backend_sync_invite",
    "version": 1,
    "endpointUrl": "https://example.com/api",
    "accessToken": "invite_...",
    "libraryOwnerUserId": "local_user_...",
    "libraryOwnerName": "WG Bibliothek",
    "contributorUserId": "account_mika_...",
    "contributorName": "Mika",
    "role": "write"
  }
}
```

The current Android settings UI can call this endpoint when configured with an admin token, then share the returned invite through the same Backend-Invite JSON package flow.

Inspect Backend memberships:

```text
GET /libraries/{ownerUserId}/memberships
Authorization: Bearer <admin-token>
```

Response shape:

```json
{
  "libraryOwnerUserId": "local_user_...",
  "memberships": [
    {
      "tokenId": "sha256-prefix",
      "libraryOwnerUserId": "local_user_...",
      "role": "write",
      "contributorUserId": "account_mika_...",
      "contributorName": "Mika",
      "source": "generated",
      "createdAtMillis": 1760000001000
    }
  ]
}
```

The reference backend never returns the secret Sync Token in membership summaries. The Android settings UI shows contributor, role, source, and contributor id so an owner/admin can check who can currently read or add cards.

Revoke a generated invite membership:

```text
DELETE /libraries/{ownerUserId}/memberships/{tokenId}
Authorization: Bearer <admin-token>
```

Response shape:

```json
{
  "revoked": {
    "tokenId": "sha256-prefix",
    "libraryOwnerUserId": "local_user_...",
    "role": "write",
    "contributorUserId": "account_mika_...",
    "contributorName": "Mika",
    "source": "generated"
  }
}
```

The local reference backend only revokes generated invite memberships stored in `generated-tokens.json`; configured tokens remain server configuration. Revoking an invite stops that token from reading or adding future cards but does not delete cards already synchronized.

## Backend Invite Package

The settings screen can share a `seemops.backend_sync_invite` JSON package for joining a shared card library. Opening or sharing that JSON to Seemops shows a confirmation dialog before the app stores anything.

Invite fields:

```text
type = seemops.backend_sync_invite
version
endpointUrl
accessToken
libraryOwnerUserId
libraryOwnerName
contributorUserId
contributorName
role
```

The invite contains the Sync-Token, so it should only be shared with people who are allowed to read or contribute to that library. Applying an invite stores the Backend-URL/token/role locally, switches the card library profile to the invite's owner/contributor metadata, and persists those owner/contributor profiles as known local card users for later profile switching. Creating an admin-generated contributor invite also stores the returned contributor identity locally so the owner can select it before any card from that contributor exists.

## Local Reference Backend

Start a local backend:

```sh
node scripts/card-sync-server.mjs \
  --port 8080 \
  --tokens "admin:local:admin:account_owner:Owner,writer:local:write:account_mika:Mika%20Server,reader:local:read"
```

Then configure the app's Backend-Sync section:

```text
Backend-URL: http://10.0.2.2:8080
Sync-Token: writer
```

Use `10.0.2.2` from the Android emulator to reach the host machine. For a physical device, use the host machine's LAN IP and make sure the server binds to that interface via `--host 0.0.0.0`.

Token spec format:

```text
token:libraryOwnerUserId:role[:contributorUserId[:contributorName]]
```

Roles:

- `read`: fetch cards only.
- `write`: fetch cards, create new review-pending cards, and update cards originally contributed by the same token identity. The reference backend forces `pendingReview = true` and `enabled = false` for these writes.
- `admin`: fetch cards, batch-upsert any card in the library, delete/reject cards, create Backend-Invite tokens for contributors, inspect membership summaries, and revoke generated invite memberships.

Use `*` as `libraryOwnerUserId` for all libraries. If `contributorUserId` is present, the local reference backend overwrites incoming contributor metadata on new cards with the token identity, so clients cannot spoof who added a card. Existing cards keep their original contributor metadata; identity-bound `write` tokens cannot overwrite cards from another contributor, and their own creates/edits return to review until an `admin` token approves them. Admin-created invite tokens are persisted under the server data directory, so they survive a reference-server restart. URL-encode `contributorName` if it contains spaces, commas, or colons. If no token spec is provided, the local dev server uses `dev-token:*:admin`. Use `--public-base-url` when the externally reachable backend URL differs from the request host that the local server sees.

Run the reference backend test:

```sh
node scripts/test-card-sync-server.mjs
```

Run the Android HTTP client against the same reference backend contract:

```sh
./gradlew :app:testDebugUnitTest --tests 'com.snupai.trinkspiel.sync.HttpRemoteCardSyncReferenceBackendTest'
```

## Next Implementation Steps

1. Pick the backend provider and account model.
2. Replace the local reference backend with the chosen production provider.
3. Add account/library membership checks using real user identity, not only static bearer tokens.
4. Add connected/instrumentation coverage against the local reference backend.
5. Add a production provider and real account/library membership model.

## Local Sync Package

The settings screen now includes a "Karten-Sync-Paket" section. It exports card snapshots for the current library owner and imports the same package through `CardSyncEngine`.

- Local-only cards receive a stable package remote id based on owner id and local card id.
- Packages can be saved through Android's file picker or shared through Android's share sheet.
- Shared/opened card-sync package JSON is detected by `type = seemops.card_sync` and routed to the card-sync preview instead of the full-backup restore path.
- Import previews show local inserts, local updates, synced markers, conflicts, skipped wrong-owner/invalid cards, and backend-only pending work.
- Conflicts are not overwritten automatically; the user can explicitly choose individual conflict cards whose local copy should be replaced with the remote version during package import.
- This package does not replace a backend because there is no shared auth, server timestamp, permission check, or central conflict resolution.
