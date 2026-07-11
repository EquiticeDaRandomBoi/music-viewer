# MusicPlayer Fabric Mod — Design Spec
**Date:** 2026-07-11  
**Versions:** Minecraft 1.21.11 + 26.1.2 (Fabric)  
**Platform:** Windows only

---

## Overview

A Fabric client-side mod that renders a pill-shaped HUD at the top of the screen showing the currently playing media from the OS (via Windows SMTC) or falling back to in-game Minecraft music. The pill expands into a full control card on right-click. Position is draggable and persists across sessions.

---

## Architecture

### Project Structure

```
musicplayer/
├── common/
│   └── src/main/java/com/evand/musicplayer/
│       ├── media/
│       │   ├── MediaInfo.java          # Data class: title, artist, isPlaying, positionMs, durationMs, thumbnailPath
│       │   ├── MediaPoller.java        # Background thread, polls SMTC every 500ms via JNA
│       │   └── MediaController.java   # Sends play/pause/skip/seek to SMTC via JNA
│       ├── hud/
│       │   ├── MediaHUD.java          # HudRenderCallback, owns state, delegates render
│       │   ├── PillRenderer.java      # Draws collapsed pill (squircle thumb + marquee title)
│       │   ├── CardRenderer.java      # Draws expanded card (thumb, title, timeline, buttons)
│       │   ├── PillAnimation.java     # Cubic ease-out/in interpolation, 0.0→1.0 progress
│       │   ├── MarqueeText.java       # Smooth right-cycling overflow text
│       │   └── DragHandler.java       # Alt+drag repositioning, screen-bounds clamp
│       └── config/
│           └── ModConfig.java         # JSON config: pillX, pillY, dragKey
├── 1.21.11/
│   ├── build.gradle
│   ├── gradle.properties
│   └── src/main/java/com/evand/musicplayer/
│       ├── MusicPlayerMod.java        # Fabric mod init
│       └── client/
│           └── MusicPlayerClient.java # Registers HUD, keybinds, render hooks (1.21.11 API)
└── 26.1.2/
    ├── build.gradle
    ├── gradle.properties
    └── src/main/java/com/evand/musicplayer/
        ├── MusicPlayerMod.java
        └── client/
            └── MusicPlayerClient.java # Same, 26.1.2 API
```

Both versions compile identical feature sets. Version-specific files contain only Fabric/Minecraft API glue (render callbacks, keybind registration). All logic lives in `common/`.

---

## Media Detection

### Primary: Windows SMTC via JNA

`MediaPoller` runs a daemon thread polling every 500ms. Uses JNA to call `Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager` via WinRT COM interfaces.

Reads per poll:
- `title`, `artist` — from media properties
- `playbackStatus` — Playing / Paused / Stopped
- `position` (ms), `duration` (ms) — from timeline properties
- `thumbnail` — RandomAccessStreamReference, saved to `%TEMP%/musicplayer_thumb.png`, reloaded as NativeImageBackedTexture when changed

Session change detection: compare session source app ID each poll. On change, invalidate thumbnail texture and re-fetch all fields.

### Fallback: Minecraft SoundManager

When no SMTC session is active, `MediaPoller` signals fallback mode. `MusicPlayerClient` hooks into `SoundManager` to detect the current playing `SoundInstance`. Provides title only (track ID → display name mapping). No thumbnail, no position (Minecraft doesn't expose playback position). No seek/skip controls rendered in fallback mode.

### Shared State

`MediaPoller` writes to an `AtomicReference<MediaInfo>`. `MediaHUD` reads it once per frame render. Thread-safe, no locks on render path.

---

## HUD Rendering

### Collapsed Pill

- Dimensions: 180×28px (scales with screen if needed)
- Style: pure black (`#FF000000`), corner radius = 14px (full pill ends)
- Left: squircle thumbnail, 20×20px, corner radius ~5px, inset 4px from left edge
- Right of thumb: scrolling marquee title (see Marquee Text below)
- No buttons in collapsed state

### Expanded Card

Triggered by right-click on the pill. Pill morphs into card:
- Dimensions: 300×160px
- Same black fill, corner radius = 14px
- Layout (top to bottom, 8px padding):
  1. Squircle thumbnail — 60×60px, centered horizontally, corner radius ~10px
  2. Scrolling title — full width, marquee if overflow
  3. Artist name — smaller font, static (truncated with ellipsis if overflow)
  4. Timeline bar — full width, clickable, shows elapsed / remaining as text on each end
  5. Button row — [⏮ Prev] [⏯ Play/Pause] [⏭ Skip], evenly spaced

Prev/Next/Play/Pause buttons send commands to `MediaController`. Timeline click calculates `clickX / barWidth * durationMs` and calls `MediaController.seek(positionMs)`.

In Minecraft fallback mode: thumbnail area blank, no timeline, no buttons rendered.

### Animation

`PillAnimation` tracks a float `progress` (0.0 = collapsed, 1.0 = expanded).

**Expand (right-click):** progress animates 0→1 over 250ms, cubic ease-out: `f(t) = 1 - (1-t)³`  
**Collapse (right-click again or outside click):** progress animates 1→0 over 200ms, cubic ease-in: `f(t) = t³`

Each frame, interpolate:
- `width` = lerp(180, 300, progress)
- `height` = lerp(28, 160, progress)
- `cornerRadius` = 14px (constant, keeps pill shape throughout)

Card content opacity = `clamp((progress - 0.6) / 0.4, 0, 1)` — fades in during last 40% of expand.

### Marquee Text

`MarqueeText` measures text width each frame. If text fits: render static. If overflow:
- Offset cycles from 0 → `(textWidth - boxWidth)` → 0, looping
- Speed: 30px/sec smooth scroll right, 500ms pause at each end
- Implemented as a time-based offset using `System.currentTimeMillis()`

---

## Drag & Repositioning

`DragHandler` activates when the configured key (default: `Left Alt`) is held.

While key held:
- On mouse press over pill bounds: begin drag, record `mouseX - pillX`, `mouseY - pillY` as offset
- On mouse drag: `pillX = mouseX - offsetX`, `pillY = mouseY - offsetY`
- Clamp: `pillX ∈ [0, screenWidth - pillWidth]`, `pillY ∈ [0, screenHeight - pillHeight]`
- On mouse release or key release: finalize position, call `ModConfig.save()`

Position drops exactly where released — no snapping.

---

## Config

`ModConfig` reads/writes `config/musicplayer.json` in the Minecraft run directory.

```json
{
  "pillX": 0.5,
  "pillY": 0.02,
  "dragKey": "left.alt"
}
```

`pillX` and `pillY` stored as fractions (0.0–1.0) of screen dimensions so position scales correctly across resolution changes. Converted to absolute px at render time.

Saved on drag release. Loaded on client init.

---

## Error Handling

- **No SMTC on machine:** JNA load fails gracefully, mod logs warning, falls back to Minecraft music mode permanently.
- **No media playing:** Pill hides itself (render skipped) when `MediaInfo` is null and no Minecraft track active.
- **Thumbnail load failure:** Render squircle area as dark grey placeholder, no crash.
- **SMTC session ends mid-play:** Poller detects null session next tick, clears `MediaInfo`, pill hides.

---

## Dual Version Notes

| Item | 1.21.11 | 26.1.2 |
|------|---------|--------|
| Fabric Loader | 0.16.x | latest |
| Fabric API | 0.107.x | latest for 26.1.2 |
| Render callback | `HudRenderCallback` | `HudRenderCallback` (verify API name) |
| DrawContext | `net.minecraft.client.gui.DrawContext` | same |
| Key binding | `KeyBinding` / `FabricKeyBinding` | same pattern |
| Gradle mappings | `1.21.11+build.X` | `26.1.2+build.X` |

Both jars named distinctly: `musicplayer-1.21.11.jar`, `musicplayer-26.1.2.jar`.

---

## Success Criteria

- Pill appears at top-center on game launch
- Plays Spotify / YouTube / any SMTC app → pill shows title, thumbnail, scrolls if long
- Right-click → smooth expand animation, controls functional
- Timeline click seeks playback in the source app
- Alt+drag repositions pill, survives game restart
- Switching media source (Spotify → browser) → pill updates within 1 second
- No crashes if SMTC unavailable or no media playing
