# Music Player HUD

A Fabric mod that displays a live music HUD inside Minecraft showing whatever is playing on your PC — title, artist, album art, progress bar, and a play/pause button.

**Windows only.** Uses the Windows System Media Transport Controls (SMTC) API, which any media app that registers with it (Spotify, YouTube in browsers, VLC, Apple Music, etc.) automatically feeds into.

---

## Features

- **Pill widget** — compact bar at the top of the screen with album art, title, and artist
- **Expanded card** — right-click the pill to open a full card with seek bar and play/pause button
- **Album art** — thumbnail fetched from SMTC, letterboxed to square with rounded corners
- **Crossfade** — smooth dip-to-black animation when the song changes
- **Live seek bar** — interpolated locally so the timer advances every frame, not just every poll
- **Seek control** — click and drag the bar to jump position
- **Smart session selection** — prefers actively playing sessions; if Spotify and a browser are both open, picks whichever is playing
- **Marquee scroll** — long titles scroll automatically
- **Draggable** — hold Alt and drag the pill to any position on screen

---

## Supported Minecraft Versions

| Subproject | Minecraft | Mappings | Java |
|---|---|---|---|
| `mc-1.21.11` | 1.21 – 1.21.11 | Yarn | 21 |
| `mc-26.1.2` | 26.1.x | Mojang (identity) | 25 |
| `mc-26.2` *(template)* | 26.2.x | Mojang (identity) | 25 |

Requires **Fabric Loader** and **Fabric API**.

---

## Requirements

- Windows 10 or 11
- Fabric Loader installed for your MC version
- Fabric API installed
- Java 21+ (Java 25 required for 26.x builds)
- A media app that registers with SMTC (Spotify, any Chromium-based browser, VLC, etc.)

---

## Installation

1. Download the JAR for your MC version from the [Releases](../../releases) page.
2. Drop the JAR into your `.minecraft/mods/` folder alongside Fabric API.
3. Launch Minecraft. The HUD appears automatically when any supported media is playing.

---

## Usage

| Action | Result |
|---|---|
| Right-click pill | Expand / collapse card |
| Alt + drag pill | Move pill to any screen position |
| Click + drag seek bar | Seek to that position |
| Click play/pause button | Toggle playback |

The HUD hides when nothing is playing.

---

## Building from Source

Requires Gradle (wrapper included in each subproject).

```bat
rem MC 1.21.11
cd mc-1.21.11
gradlew.bat build

rem MC 26.1.2
cd mc-26.1.2
gradlew.bat build
```

Output JARs are in `mc-<version>/build/libs/`.

The root `gradle.properties` forces the Gradle daemon onto Java 25 so both subprojects can be configured simultaneously:

```bat
rem Build all active subprojects from root (using mc-1.21.11's wrapper)
cd mc-1.21.11
gradlew.bat :mc-1.21.11:build :mc-26.1.2:build
```

### Adding a new MC version

1. Copy `mc-26.2/` as a template.
2. Update `gradle.properties` with the correct `minecraft_version`, `loader_version`, and `fabric_version`.
3. Uncomment `include 'mc-26.2'` in `settings.gradle`.
4. If the MC version introduces API changes in the HUD/texture layer, add override files in `mc-<new>/src/main/java/` and mirror the excludes in `build.gradle`.

---

## How It Works

A persistent PowerShell server (`get_media_server.ps1`) runs as a subprocess. Java writes `poll` to its stdin every 200 ms; the script responds with a JSON line containing title, artist, position, duration, thumbnail path, and source app. A separate server (`control_media_server.ps1`) handles playback commands (play/pause, seek, skip).

Thumbnails are fetched via WinRT's `ThumbnailReference`, decoded through WPF's `BitmapDecoder`, converted to RGB24, letterboxed to a square with black bars, then saved as PNG to a temp file. Java loads the PNG into a `NativeImage` and registers it as a Minecraft texture. Two texture slots (A and B) alternate on each track change so the old thumbnail stays alive during the 500 ms dip-to-black crossfade.

---

## License

MIT
