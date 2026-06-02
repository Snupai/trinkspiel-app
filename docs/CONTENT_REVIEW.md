# Built-In Content Review

Last updated: 2026-06-01

This file documents the review boundary for the built-in Seemops Trinkspiel card packs. It does not cover user-created or imported cards, which remain local user-generated content.

## Current Built-In Scope

- 8 built-in packs
- 128 built-in cards
- 3 local pack templates with paused draft cards for user editing
- Maximum built-in card value: 4 drinks
- Built-in safety pacing prompt: included through a water/pause card
- Target audience: adults of legal drinking age only

## Automated Guardrails

`BuiltInPacksTest` and `PackTemplatesTest` verify:

- Pack IDs, pack names, and card texts are unique.
- Each built-in pack has launch-ready volume.
- Each built-in card has valid metadata.
- Pack templates start paused and provide editable draft cards.
- Pack templates include balanced category mixes and low-impact drink values.
- Built-in cards stay at or below 4 drinks.
- Built-in cards stay short enough for quick in-game reading.
- Built-in cards avoid high-risk terms around chugging, blackout, drugs, driving after drinking, and explicit sexual pressure.
- Built-in content includes at least one water/pause pacing prompt.

Run:

```sh
./gradlew :app:testDebugUnitTest --no-daemon
```

## Human Review Checklist

- Cards are playful, social, and easy to skip.
- Cards do not require illegal activity, dangerous stunts, harassment, humiliation, or non-consensual contact.
- Cards do not ask players to drive, travel, or make safety-critical decisions after drinking.
- Cards do not encourage chugging, blackout drinking, or finishing bottles/glasses.
- Spicy/couples prompts remain mild and non-explicit.
- Prompts are suitable for a Play Store listing that discloses an alcohol/drinking-game theme and adult audience.
- Safety messaging remains visible in first-run setup, settings, store metadata, and privacy/safety docs.

## Store Disclosure Notes

- Content rating should disclose alcohol/drinking-game references.
- Store listing should target adults/legal drinking age only.
- The app should continue to state that gameplay is optional, local, offline, and user-controlled.
