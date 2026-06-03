# Seemops Trinkspiel

Offline-first Android party drinking game for adults. The app lets groups play from built-in packs, create their own cards, sort cards into escalation levels, manage player scores, export/import backups, and optionally sync a shared card library through a manually configured backend.

Website: [snupai.github.io/trinkspiel-app](https://snupai.github.io/trinkspiel-app/)  
Privacy policy: [snupai.github.io/trinkspiel-app/privacy-policy.html](https://snupai.github.io/trinkspiel-app/privacy-policy.html)

## Current Status

Version: `3.0 (3)`  
Package: `com.snupai.trinkspiel`

Local release evidence is mostly ready:

- Debug/release builds work.
- Release AAB is signed locally.
- Unit tests, lint, connected Android tests, Play Console handoff, and store screenshots are current.
- GitHub Pages hosts the exact privacy-policy HTML.
- Remaining public-release blocker: real-device manual QA must be completed and confirmed.

See [docs/CURRENT_APP_STATUS.md](docs/CURRENT_APP_STATUS.md) for the full status snapshot.

## App Features

- 128 built-in cards across 8 packs
- Three question levels: `1 Einfach`, `2 Saufen`, `3 Fast ausziehen`
- Custom cards with owner/contributor attribution
- Separate review flow for external contributor cards
- Pack templates for paused draft cards
- Player rotation, teams, scores, skip, undo, reset, and local recap
- Full local backup/restore and transfer-package import/export
- Optional manual Backend-Sync and Backend-Invite JSON flow
- No ads, no analytics, no account required for local play

The app is intended only for adults of legal drinking age. Drink responsibly, take breaks, and stop whenever needed.

## Build

Requirements:

- JDK 17
- Android SDK with API 36
- Android SDK build tools/platform tools

Useful commands:

```sh
./gradlew :app:assembleDebug --no-daemon
./gradlew :app:assembleRelease :app:bundleRelease --no-daemon
```

Generated local artifacts:

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release.apk`
- Release AAB: `app/build/outputs/bundle/release/app-release.aab`

Build artifacts are ignored by git.

## Test And Release Checks

Run the local checks:

```sh
./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug --no-daemon
```

With an attached Android device or emulator:

```sh
scripts/run-connected-ui-tests.sh
scripts/capture-store-screenshots.sh
```

Release gate:

```sh
export SEEMOPS_PRIVACY_POLICY_URL="https://snupai.github.io/trinkspiel-app/privacy-policy.html"
scripts/verify-release-ready.sh --skip-build
scripts/release-blockers.sh --no-fail
```

At the moment the final gate still fails until manual QA is filled and confirmed with `SEEMOPS_MANUAL_QA_CONFIRMED=1`.

## GitHub Pages

GitHub Pages is served from `main` / `docs`.

- Landing page: `docs/index.html`
- Privacy policy: `docs/privacy-policy.html`
- Store assets: `docs/store-assets/`

After editing the privacy page, validate it with:

```sh
SEEMOPS_PRIVACY_POLICY_URL="https://snupai.github.io/trinkspiel-app/privacy-policy.html" scripts/check-privacy-policy-url.sh
```

## Signing

Real release signing files stay local and must not be committed.

Ignored local files include:

- `signing/*.keystore`
- `signing/*.jks`
- `signing/release-signing.properties`

Check local signing config:

```sh
scripts/check-release-signing-config.sh
```

See [signing/README.md](signing/README.md) for details.

## Important Docs

- [docs/CURRENT_APP_STATUS.md](docs/CURRENT_APP_STATUS.md)
- [docs/ASAP_FEATURES.md](docs/ASAP_FEATURES.md)
- [docs/FINAL_RELEASE_RUNBOOK.md](docs/FINAL_RELEASE_RUNBOOK.md)
- [docs/PLAY_CONSOLE_SUBMISSION.md](docs/PLAY_CONSOLE_SUBMISSION.md)
- [docs/RELEASE_AUDIT.md](docs/RELEASE_AUDIT.md)
- [docs/PRIVACY_POLICY.md](docs/PRIVACY_POLICY.md)

