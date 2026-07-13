# Music Player HUD

Shows what's playing on your PC as a little pill widget in Minecraft. Album art, title, artist, seek bar, play/pause. Windows only — it hooks into Windows SMTC so anything that registers there (Spotify, browsers, VLC, etc.) just works automatically.

## What it does

Small pill at the top of the screen shows the current track. Right-click to expand a card with a seek bar and playback controls. Alt+drag to reposition it. Long titles scroll. Album art crossfades when tracks change. Seek bar interpolates every frame.

Nothing to configure — drop in the mod and play music.

## Versions

| Subproject | MC versions | Mappings | Java |
|---|---|---|---|
| `mc-1.21.11` | 1.21 – 1.21.11 | Yarn | 21 |
| `mc-26.1.2` | 26.1.x | Mojang (identity) | 25 |

Needs Fabric Loader + Fabric API. Windows 10/11 only.

## Install

Grab the JAR from [Releases](../../releases), drop it in `.minecraft/mods/` with Fabric API.

## Controls

- Right-click pill → expand/collapse card
- Alt + drag pill → reposition
- Drag seek bar → seek
- Click play/pause → toggle playback

## Building

```bat
cd mc-1.21.11 && gradlew.bat build
cd mc-26.1.2  && gradlew.bat build
```

JARs end up in `<subproject>/build/libs/`.

## How it works

A PowerShell subprocess (`get_media_server.ps1`) runs in the background. Java polls it every 200 ms by writing `poll` to stdin; the script responds with a JSON line — title, artist, position, duration, thumbnail path. A second script handles playback commands.

Thumbnails come from WinRT's `ThumbnailReference`, decoded via WPF, letterboxed to a square, saved as PNG to a temp file, then loaded as a Minecraft texture. Two slots alternate on track change so the old thumbnail stays alive during the 500 ms crossfade.

## License

MIT
