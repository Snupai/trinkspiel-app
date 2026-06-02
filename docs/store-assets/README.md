# Store Assets

Generated Play Store support assets live here.

## Store Graphics

Run this to render deterministic PNG assets from SVG sources:

```sh
scripts/generate-store-assets.sh
```

Current expected graphics:

- `feature-graphic.png`: 1024x500 Play Store feature graphic
- `store-icon.png`: 512x512 Play Store icon candidate

Editable SVG sources live in `docs/store-assets/source/`.

## Launcher Icons

Run this after changing `docs/store-assets/source/store-icon.svg`:

```sh
scripts/generate-launcher-icons.sh
```

The script regenerates legacy Android launcher WebP assets in `app/src/main/res/mipmap-*`. Android 8+ uses the adaptive vector icon in `app/src/main/res/drawable/`.

## Phone Screenshots

Run this with one Android device or emulator attached:

```sh
scripts/capture-store-screenshots.sh
```

The script installs the debug APK, clears app data, enters Android System UI demo mode for a clean status bar, walks through first-run setup and core screens, then writes PNGs to `docs/store-assets/screenshots/phone/`.

Use this after capture to verify the status bar stayed clean:

```sh
scripts/check-store-screenshot-polish.sh
```

Current expected screenshots:

- `01-first-run-setup.png`
- `02-game-ready.png`
- `03-card-drawn.png`
- `04-entry-manager.png`
- `05-settings-diagnostics.png`
- `06-settings-legal.png`
