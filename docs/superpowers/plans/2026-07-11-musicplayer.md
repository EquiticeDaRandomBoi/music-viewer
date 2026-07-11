# MusicPlayer Fabric Mod Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Fabric client mod for Minecraft 1.21.11 and 26.1.2 that renders a pill-shaped HUD showing OS media (Windows SMTC) or in-game music, with smooth expand animation, thumbnail, seekable timeline, and drag-to-reposition.

**Architecture:** Shared Java source in `common-src/` compiled into both version subprojects via Gradle `srcDirs`. Windows SMTC is accessed via PowerShell one-shot subprocesses (no background process). All rendering uses Minecraft's DrawContext + Tessellator for rounded shapes.

**Tech Stack:** Java 21, Fabric Loader, Fabric API, Fabric Loom, Minecraft DrawContext/Tessellator, Gson (bundled with Minecraft), PowerShell for SMTC, JUnit 5 for pure-logic tests.

## Global Constraints

- Java 21 source and target compatibility for both subprojects
- Windows-only SMTC path; must degrade gracefully (log + skip) on non-Windows
- No external dependencies beyond what ships with Minecraft (Gson is available)
- Both JARs must be feature-identical; only gradle.properties differs between versions
- Mod group: `com.evand`, mod id: `musicplayer`
- Config file: `<mc-run-dir>/config/musicplayer.json`
- PS scripts extracted to `%TEMP%/musicplayer/` on first run
- 1.21.11 jar name: `musicplayer-1.21.11.jar` — 26.1.2 jar name: `musicplayer-26.1.2.jar`
- Verify exact yarn/fabric-api versions at https://fabricmc.net/develop before building

---

## File Map

```
musicplayer/
├── settings.gradle
├── common-src/com/evand/musicplayer/
│   ├── media/
│   │   ├── MediaInfo.java
│   │   ├── MediaPoller.java
│   │   ├── MediaController.java
│   │   └── ThumbnailManager.java
│   ├── hud/
│   │   ├── MediaHUD.java
│   │   ├── PillRenderer.java
│   │   ├── CardRenderer.java
│   │   ├── RoundedRectRenderer.java
│   │   ├── PillAnimation.java
│   │   ├── MarqueeText.java
│   │   └── DragHandler.java
│   └── config/
│       └── ModConfig.java
├── common-resources/scripts/
│   ├── get_media.ps1
│   └── control_media.ps1
├── mc-1.21.11/
│   ├── build.gradle
│   ├── gradle.properties
│   └── src/main/
│       ├── java/com/evand/musicplayer/
│       │   ├── MusicPlayerMod.java
│       │   ├── client/MusicPlayerClient.java
│       │   └── mixin/MusicTrackerMixin.java
│       └── resources/
│           ├── fabric.mod.json
│           └── musicplayer.mixins.json
└── mc-26.1.2/
    ├── build.gradle
    ├── gradle.properties
    └── src/main/
        ├── java/com/evand/musicplayer/
        │   ├── MusicPlayerMod.java
        │   ├── client/MusicPlayerClient.java
        │   └── mixin/MusicTrackerMixin.java
        └── resources/
            ├── fabric.mod.json
            └── musicplayer.mixins.json
```

---

## Task 1: Gradle Multi-Project Scaffold

**Files:**
- Create: `settings.gradle`
- Create: `mc-1.21.11/build.gradle`
- Create: `mc-1.21.11/gradle.properties`
- Create: `mc-1.21.11/gradle/wrapper/gradle-wrapper.properties`
- Create: `mc-26.1.2/build.gradle`
- Create: `mc-26.1.2/gradle.properties`
- Create: `mc-26.1.2/gradle/wrapper/gradle-wrapper.properties`

**Interfaces:**
- Produces: two buildable Fabric subprojects sharing `common-src/` and `common-resources/`

- [ ] **Step 1: Create root settings.gradle**

```groovy
// settings.gradle
pluginManagement {
    repositories {
        maven { url 'https://maven.fabricmc.net/' }
        gradlePluginPortal()
    }
}

rootProject.name = 'musicplayer'
include 'mc-1.21.11'
include 'mc-26.1.2'
```

- [ ] **Step 2: Create mc-1.21.11/build.gradle**

```groovy
plugins {
    id 'fabric-loom' version '1.9-SNAPSHOT'
    id 'java'
}

group = 'com.evand'
version = '1.0.0'
base { archivesName = 'musicplayer-1.21.11' }

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
}

sourceSets {
    main {
        java   { srcDir "${rootDir}/common-src" }
        resources { srcDir "${rootDir}/common-resources" }
    }
}

repositories {
    maven { url 'https://maven.fabricmc.net/' }
    mavenCentral()
}

dependencies {
    minecraft "com.mojang:minecraft:${minecraft_version}"
    mappings "net.fabricmc:yarn:${yarn_mappings}:v2"
    modImplementation "net.fabricmc:fabric-loader:${loader_version}"
    modImplementation "net.fabricmc.fabric-api:fabric-api:${fabric_version}"
    compileOnly "org.jetbrains:annotations:24.0.0"
}

processResources {
    inputs.property 'version', project.version
    filesMatching('fabric.mod.json') {
        expand version: project.version
    }
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
    options.release = 21
}
```

- [ ] **Step 3: Create mc-1.21.11/gradle.properties**

```properties
# Verify exact values at https://fabricmc.net/develop
minecraft_version=1.21.11
yarn_mappings=1.21.11+build.1
loader_version=0.16.14
fabric_version=0.109.0+1.21.11

org.gradle.jvmargs=-Xmx2G
```

- [ ] **Step 4: Create mc-26.1.2/build.gradle** (identical to mc-1.21.11/build.gradle except archivesName)

```groovy
plugins {
    id 'fabric-loom' version '1.9-SNAPSHOT'
    id 'java'
}

group = 'com.evand'
version = '1.0.0'
base { archivesName = 'musicplayer-26.1.2' }

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
}

sourceSets {
    main {
        java   { srcDir "${rootDir}/common-src" }
        resources { srcDir "${rootDir}/common-resources" }
    }
}

repositories {
    maven { url 'https://maven.fabricmc.net/' }
    mavenCentral()
}

dependencies {
    minecraft "com.mojang:minecraft:${minecraft_version}"
    mappings "net.fabricmc:yarn:${yarn_mappings}:v2"
    modImplementation "net.fabricmc:fabric-loader:${loader_version}"
    modImplementation "net.fabricmc.fabric-api:fabric-api:${fabric_version}"
    compileOnly "org.jetbrains:annotations:24.0.0"
}

processResources {
    inputs.property 'version', project.version
    filesMatching('fabric.mod.json') {
        expand version: project.version
    }
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
    options.release = 21
}
```

- [ ] **Step 5: Create mc-26.1.2/gradle.properties**

```properties
# Verify exact values at https://fabricmc.net/develop
minecraft_version=26.1.2
yarn_mappings=26.1.2+build.1
loader_version=0.17.0
fabric_version=0.120.0+26.1.2

org.gradle.jvmargs=-Xmx2G
```

- [ ] **Step 6: Add Gradle wrapper to both subprojects**

In `mc-1.21.11/gradle/wrapper/gradle-wrapper.properties`:
```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.10-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

Copy identical file to `mc-26.1.2/gradle/wrapper/gradle-wrapper.properties`.

Run in each subproject directory:
```
gradlew wrapper
```

- [ ] **Step 7: Verify scaffold builds (no source yet)**

```
cd mc-1.21.11 && gradlew dependencies
cd mc-26.1.2  && gradlew dependencies
```

Expected: Both resolve Minecraft + Fabric deps without error. (Source compile will fail — that's expected, no Java files yet.)

---

## Task 2: fabric.mod.json, Mixin Config, Mod Entrypoints

**Files:**
- Create: `mc-1.21.11/src/main/resources/fabric.mod.json`
- Create: `mc-1.21.11/src/main/resources/musicplayer.mixins.json`
- Create: `mc-1.21.11/src/main/java/com/evand/musicplayer/MusicPlayerMod.java`
- Create: `mc-26.1.2/src/main/resources/fabric.mod.json`
- Create: `mc-26.1.2/src/main/resources/musicplayer.mixins.json`
- Create: `mc-26.1.2/src/main/java/com/evand/musicplayer/MusicPlayerMod.java`

**Interfaces:**
- Produces: loadable mod stubs that Fabric recognises

- [ ] **Step 1: Create mc-1.21.11/src/main/resources/fabric.mod.json**

```json
{
  "schemaVersion": 1,
  "id": "musicplayer",
  "version": "${version}",
  "name": "Music Player",
  "description": "OS media player HUD pill for Minecraft",
  "authors": ["evand"],
  "environment": "*",
  "entrypoints": {
    "main":   ["com.evand.musicplayer.MusicPlayerMod"],
    "client": ["com.evand.musicplayer.client.MusicPlayerClient"]
  },
  "mixins": ["musicplayer.mixins.json"],
  "depends": {
    "fabricloader": ">=0.16.0",
    "fabric-api": "*",
    "minecraft": "~1.21.11"
  }
}
```

- [ ] **Step 2: Create mc-1.21.11/src/main/resources/musicplayer.mixins.json**

```json
{
  "required": true,
  "package": "com.evand.musicplayer.mixin",
  "compatibilityLevel": "JAVA_21",
  "client": ["MusicTrackerMixin"],
  "injectors": { "defaultRequire": 1 }
}
```

- [ ] **Step 3: Create mc-1.21.11/src/main/java/com/evand/musicplayer/MusicPlayerMod.java**

```java
package com.evand.musicplayer;

import net.fabricmc.api.ModInitializer;

public class MusicPlayerMod implements ModInitializer {
    public static final String MOD_ID = "musicplayer";

    @Override
    public void onInitialize() {
        // Server-side: nothing to do
    }
}
```

- [ ] **Step 4: Repeat Steps 1-3 for mc-26.1.2**, changing the `depends.minecraft` field to `"~26.1.2"` and `depends.fabricloader` to `">=0.17.0"`. The Java source files are identical.

- [ ] **Step 5: Verify mod loads**

```
cd mc-1.21.11 && gradlew runClient
```

Expected: Minecraft launches, no crash, `musicplayer` appears in mod list. Repeat for mc-26.1.2.

---

## Task 3: MediaInfo, ModConfig, PowerShell Scripts

**Files:**
- Create: `common-src/com/evand/musicplayer/media/MediaInfo.java`
- Create: `common-src/com/evand/musicplayer/config/ModConfig.java`
- Create: `common-resources/scripts/get_media.ps1`
- Create: `common-resources/scripts/control_media.ps1`

**Interfaces:**
- Produces:
  - `MediaInfo(String title, String artist, boolean isPlaying, long positionMs, long durationMs, String thumbnailPath, String sourceApp)` — record, all fields nullable-safe
  - `ModConfig.load()` — reads or creates config, returns `ModConfig`
  - `ModConfig.save()` — writes config to disk
  - `ModConfig.pillXFrac`, `ModConfig.pillYFrac` — float 0–1
  - `ModConfig.dragKey` — `String`, default `"key.keyboard.left.alt"`

- [ ] **Step 1: Create MediaInfo**

```java
// common-src/com/evand/musicplayer/media/MediaInfo.java
package com.evand.musicplayer.media;

public record MediaInfo(
    String title,
    String artist,
    boolean isPlaying,
    long positionMs,
    long durationMs,
    String thumbnailPath,  // null if unavailable
    String sourceApp       // e.g. "Spotify.exe"
) {
    public boolean hasThumbnail() { return thumbnailPath != null && !thumbnailPath.isEmpty(); }
    public float progress() {
        if (durationMs <= 0) return 0f;
        return (float) positionMs / durationMs;
    }
    public String elapsedFormatted()   { return formatMs(positionMs); }
    public String remainingFormatted() { return "-" + formatMs(durationMs - positionMs); }

    private static String formatMs(long ms) {
        if (ms < 0) ms = 0;
        long s = ms / 1000;
        return String.format("%d:%02d", s / 60, s % 60);
    }
}
```

- [ ] **Step 2: Create ModConfig**

```java
// common-src/com/evand/musicplayer/config/ModConfig.java
package com.evand.musicplayer.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
        FabricLoader.getInstance().getConfigDir().resolve("musicplayer.json");

    public float pillXFrac = 0.5f;
    public float pillYFrac = 0.02f;
    public String dragKey  = "key.keyboard.left.alt";

    public static ModConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                return GSON.fromJson(json, ModConfig.class);
            } catch (IOException e) {
                return new ModConfig();
            }
        }
        ModConfig cfg = new ModConfig();
        cfg.save();
        return cfg;
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(this));
        } catch (IOException e) {
            // Non-fatal: position just won't persist this session
        }
    }
}
```

- [ ] **Step 3: Create get_media.ps1 in common-resources/scripts/**

```powershell
# common-resources/scripts/get_media.ps1
# Returns JSON with current SMTC media session info, or {} if nothing playing.

Add-Type -AssemblyName System.Runtime.WindowsRuntime

# Helper to await a WinRT IAsyncOperation<T>
$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() |
    Where-Object {
        $_.Name -eq 'AsTask' -and
        $_.GetParameters().Count -eq 1 -and
        $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
    })[0]

function Await($WinRtTask, $ResultType) {
    $asTask = $asTaskGeneric.MakeGenericMethod($ResultType)
    $netTask = $asTask.Invoke($null, @($WinRtTask))
    $netTask.Wait(-1) | Out-Null
    $netTask.Result
}

# Load WinRT type
[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]

try {
    $manager = Await `
        ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) `
        ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
} catch {
    Write-Output '{}'
    exit
}

$session = $manager.GetCurrentSession()
if ($null -eq $session) {
    Write-Output '{}'
    exit
}

try {
    $props    = Await ($session.TryGetMediaPropertiesAsync()) `
                      ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
    $timeline = $session.GetTimelineProperties()
    $playback = $session.GetPlaybackInfo()
} catch {
    Write-Output '{}'
    exit
}

$thumbPath = ''
if ($null -ne $props.Thumbnail) {
    try {
        [void][Windows.Storage.Streams.IRandomAccessStreamWithContentType,Windows.Storage,ContentType=WindowsRuntime]
        $stream    = Await ($props.Thumbnail.OpenReadAsync()) `
                           ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
        $tempDir   = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), 'musicplayer')
        [System.IO.Directory]::CreateDirectory($tempDir) | Out-Null
        $tempPath  = [System.IO.Path]::Combine($tempDir, 'thumb.png')
        $netStream = [System.IO.WindowsRuntimeStreamExtensions]::AsStreamForRead($stream)
        $file      = [System.IO.File]::OpenWrite($tempPath)
        $netStream.CopyTo($file)
        $file.Close()
        $thumbPath = $tempPath
    } catch { }
}

$status = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionPlaybackStatus]
$isPlaying = ($playback.PlaybackStatus -eq $status::Playing)

@{
    title         = [string]$props.Title
    artist        = [string]$props.Artist
    isPlaying     = $isPlaying
    positionMs    = [long]$timeline.Position.TotalMilliseconds
    durationMs    = [long]$timeline.EndTime.TotalMilliseconds
    thumbnailPath = $thumbPath
    sourceApp     = [string]$session.SourceAppUserModelId
} | ConvertTo-Json -Compress
```

- [ ] **Step 4: Create control_media.ps1 in common-resources/scripts/**

```powershell
# common-resources/scripts/control_media.ps1
# Usage: control_media.ps1 -Action <play|pause|toggle|next|previous|seek> [-SeekMs <ms>]
param(
    [Parameter(Mandatory)][string]$Action,
    [long]$SeekMs = 0
)

Add-Type -AssemblyName System.Runtime.WindowsRuntime

$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() |
    Where-Object {
        $_.Name -eq 'AsTask' -and
        $_.GetParameters().Count -eq 1 -and
        $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
    })[0]

function Await($WinRtTask, $ResultType) {
    $asTask = $asTaskGeneric.MakeGenericMethod($ResultType)
    $netTask = $asTask.Invoke($null, @($WinRtTask))
    $netTask.Wait(-1) | Out-Null
    $netTask.Result
}

[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]

try {
    $manager = Await `
        ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) `
        ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
    $session = $manager.GetCurrentSession()
    if ($null -eq $session) { exit }

    switch ($Action.ToLower()) {
        'play'     { $session.TryPlayAsync()          | Out-Null }
        'pause'    { $session.TryPauseAsync()         | Out-Null }
        'toggle'   { $session.TryTogglePlayPauseAsync()| Out-Null }
        'next'     { $session.TrySkipNextAsync()      | Out-Null }
        'previous' { $session.TrySkipPreviousAsync()  | Out-Null }
        'seek'     {
            $ts = [System.TimeSpan]::FromMilliseconds($SeekMs)
            $session.TryChangePlaybackPositionAsync($ts.Ticks) | Out-Null
        }
    }
} catch { }
```

- [ ] **Step 5: Verify scripts manually**

Open PowerShell and run:
```powershell
powershell -ExecutionPolicy Bypass -NoProfile -NonInteractive -File "F:\musicplayer\common-resources\scripts\get_media.ps1"
```

Expected with Spotify playing: `{"title":"Song Name","artist":"Artist","isPlaying":true,"positionMs":12345,...}`  
Expected with nothing playing: `{}`

---

## Task 4: MediaPoller

**Files:**
- Create: `common-src/com/evand/musicplayer/media/MediaPoller.java`

**Interfaces:**
- Consumes: `MediaInfo` record (Task 3), `get_media.ps1` script extracted to temp
- Produces:
  - `MediaPoller.INSTANCE` — singleton
  - `MediaPoller.start()` — begins background polling thread
  - `MediaPoller.stop()` — stops thread
  - `MediaPoller.get()` — returns `MediaInfo` or `null` (OS media, not Minecraft fallback)
  - `MediaPoller.setMinecraftTrack(String title)` — called by Mixin when MC music changes
  - `MediaPoller.getMinecraftTrack()` — returns `String` or `null`

- [ ] **Step 1: Create MediaPoller.java**

```java
// common-src/com/evand/musicplayer/media/MediaPoller.java
package com.evand.musicplayer.media;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;

public class MediaPoller {
    public static final MediaPoller INSTANCE = new MediaPoller();

    private static final long POLL_INTERVAL_MS = 500;
    private static final Gson GSON = new Gson();

    private final AtomicReference<MediaInfo> osMediaRef   = new AtomicReference<>(null);
    private final AtomicReference<String>    mcTrackRef   = new AtomicReference<>(null);

    private volatile Thread pollThread;
    private volatile boolean running = false;
    private Path scriptPath;

    private MediaPoller() {}

    public void start() {
        if (running) return;
        scriptPath = extractScript();
        if (scriptPath == null) {
            System.err.println("[MusicPlayer] Failed to extract get_media.ps1 — OS media disabled");
        }
        running = true;
        pollThread = new Thread(this::pollLoop, "MusicPlayer-Poller");
        pollThread.setDaemon(true);
        pollThread.start();
    }

    public void stop() {
        running = false;
        if (pollThread != null) pollThread.interrupt();
    }

    /** Current OS (SMTC) media info. Null if nothing playing or Windows unavailable. */
    public MediaInfo get() { return osMediaRef.get(); }

    /** Set by MusicTrackerMixin when Minecraft starts a music track. */
    public void setMinecraftTrack(String title) { mcTrackRef.set(title); }
    public String getMinecraftTrack()           { return mcTrackRef.get(); }

    private void pollLoop() {
        while (running) {
            if (scriptPath != null) {
                try {
                    MediaInfo info = pollSmtc();
                    osMediaRef.set(info);
                } catch (Exception e) {
                    osMediaRef.set(null);
                }
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private MediaInfo pollSmtc() throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            "powershell.exe",
            "-ExecutionPolicy", "Bypass",
            "-NoProfile",
            "-NonInteractive",
            "-File", scriptPath.toAbsolutePath().toString()
        );
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        String output;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            output = reader.lines()
                .reduce("", (a, b) -> a + b)
                .trim();
        }
        proc.waitFor();

        if (output.isEmpty() || output.equals("{}")) return null;

        JsonObject json = JsonParser.parseString(output).getAsJsonObject();
        String title         = getStr(json, "title");
        String artist        = getStr(json, "artist");
        boolean isPlaying    = json.has("isPlaying") && json.get("isPlaying").getAsBoolean();
        long positionMs      = json.has("positionMs") ? json.get("positionMs").getAsLong() : 0;
        long durationMs      = json.has("durationMs") ? json.get("durationMs").getAsLong() : 0;
        String thumbnailPath = getStr(json, "thumbnailPath");
        String sourceApp     = getStr(json, "sourceApp");

        if (title == null || title.isEmpty()) return null;
        return new MediaInfo(title, artist, isPlaying, positionMs, durationMs, thumbnailPath, sourceApp);
    }

    private static String getStr(JsonObject json, String key) {
        return json.has(key) ? json.get(key).getAsString() : null;
    }

    private static Path extractScript() {
        try {
            Path dir = Path.of(System.getProperty("java.io.tmpdir"), "musicplayer");
            Files.createDirectories(dir);
            Path dest = dir.resolve("get_media.ps1");
            try (InputStream is = MediaPoller.class.getResourceAsStream("/scripts/get_media.ps1")) {
                if (is == null) return null;
                Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
            }
            return dest;
        } catch (IOException e) {
            return null;
        }
    }
}
```

- [ ] **Step 2: Verify compile**

```
cd mc-1.21.11 && gradlew compileJava
```

Expected: Compiles without error.

---

## Task 5: MediaController

**Files:**
- Create: `common-src/com/evand/musicplayer/media/MediaController.java`

**Interfaces:**
- Consumes: `control_media.ps1` script
- Produces:
  - `MediaController.INSTANCE` — singleton
  - `MediaController.toggle()` — play/pause toggle
  - `MediaController.next()` — skip next
  - `MediaController.previous()` — skip previous
  - `MediaController.seek(long positionMs)` — seek to position

- [ ] **Step 1: Create MediaController.java**

```java
// common-src/com/evand/musicplayer/media/MediaController.java
package com.evand.musicplayer.media;

import java.io.*;
import java.nio.file.*;

public class MediaController {
    public static final MediaController INSTANCE = new MediaController();

    private Path scriptPath;
    private boolean available = false;

    private MediaController() {}

    public void init() {
        scriptPath = extractScript();
        available  = scriptPath != null;
    }

    public void toggle()              { send("toggle"); }
    public void next()                { send("next"); }
    public void previous()            { send("previous"); }
    public void seek(long positionMs) { send("seek", positionMs); }

    private void send(String action) { send(action, -1); }

    private void send(String action, long seekMs) {
        if (!available) return;
        try {
            ProcessBuilder pb;
            if (seekMs >= 0) {
                pb = new ProcessBuilder(
                    "powershell.exe",
                    "-ExecutionPolicy", "Bypass",
                    "-NoProfile",
                    "-NonInteractive",
                    "-File", scriptPath.toAbsolutePath().toString(),
                    "-Action", action,
                    "-SeekMs", String.valueOf(seekMs)
                );
            } else {
                pb = new ProcessBuilder(
                    "powershell.exe",
                    "-ExecutionPolicy", "Bypass",
                    "-NoProfile",
                    "-NonInteractive",
                    "-File", scriptPath.toAbsolutePath().toString(),
                    "-Action", action
                );
            }
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            proc.getInputStream().transferTo(OutputStream.nullOutputStream());
            proc.waitFor();
        } catch (Exception ignored) {}
    }

    private static Path extractScript() {
        try {
            Path dir  = Path.of(System.getProperty("java.io.tmpdir"), "musicplayer");
            Files.createDirectories(dir);
            Path dest = dir.resolve("control_media.ps1");
            try (InputStream is = MediaController.class.getResourceAsStream("/scripts/control_media.ps1")) {
                if (is == null) return null;
                Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
            }
            return dest;
        } catch (IOException e) {
            return null;
        }
    }
}
```

- [ ] **Step 2: Verify compile**

```
cd mc-1.21.11 && gradlew compileJava
```

Expected: Compiles without error.

---

## Task 6: ThumbnailManager

**Files:**
- Create: `common-src/com/evand/musicplayer/media/ThumbnailManager.java`

**Interfaces:**
- Produces:
  - `ThumbnailManager.INSTANCE` — singleton
  - `ThumbnailManager.getTexture(String path)` — returns `Identifier` of registered texture, or `null`
  - `ThumbnailManager.tick()` — call once per frame on render thread to load pending textures

- [ ] **Step 1: Create ThumbnailManager.java**

```java
// common-src/com/evand/musicplayer/media/ThumbnailManager.java
package com.evand.musicplayer.media;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;

public class ThumbnailManager {
    public static final ThumbnailManager INSTANCE = new ThumbnailManager();

    private static final Identifier TEXTURE_ID = Identifier.of("musicplayer", "thumb");

    private String   loadedPath  = null;
    private boolean  registered  = false;

    // Path queued from poller thread to be loaded on render thread
    private final AtomicReference<String> pendingPath = new AtomicReference<>(null);

    private ThumbnailManager() {}

    /** Called from render thread each frame. Loads any pending texture update. */
    public void tick() {
        String path = pendingPath.getAndSet(null);
        if (path == null || path.equals(loadedPath)) return;

        loadedPath = path;
        if (path.isEmpty()) {
            unregister();
            return;
        }

        try (InputStream is = Files.newInputStream(Path.of(path))) {
            NativeImage image     = NativeImage.read(is);
            var          texture  = new NativeImageBackedTexture(image);
            MinecraftClient.getInstance().getTextureManager()
                .registerTexture(TEXTURE_ID, texture);
            registered = true;
        } catch (IOException e) {
            unregister();
        }
    }

    /** Queue a path from the poller thread. Pass empty string to clear. */
    public void queuePath(String path) {
        pendingPath.set(path == null ? "" : path);
    }

    /** Returns texture identifier if a thumbnail is loaded, null otherwise. */
    public Identifier getTexture() {
        return registered ? TEXTURE_ID : null;
    }

    private void unregister() {
        if (registered) {
            MinecraftClient.getInstance().getTextureManager()
                .destroyTexture(TEXTURE_ID);
            registered = false;
        }
    }
}
```

- [ ] **Step 2: Update MediaPoller to queue thumbnail path on each poll**

In `MediaPoller.pollLoop()`, after `osMediaRef.set(info)`, add:
```java
String thumbPath = (info != null && info.hasThumbnail()) ? info.thumbnailPath() : "";
ThumbnailManager.INSTANCE.queuePath(thumbPath);
```

Import `com.evand.musicplayer.media.ThumbnailManager` at top of `MediaPoller.java`.

- [ ] **Step 3: Verify compile**

```
cd mc-1.21.11 && gradlew compileJava
```

Expected: Compiles without error.

---

## Task 7: PillAnimation and MarqueeText

**Files:**
- Create: `common-src/com/evand/musicplayer/hud/PillAnimation.java`
- Create: `common-src/com/evand/musicplayer/hud/MarqueeText.java`

**Interfaces:**
- Produces:
  - `PillAnimation.expand()` / `PillAnimation.collapse()` — trigger animation
  - `PillAnimation.tick(float deltaMs)` — advance progress, call each frame
  - `PillAnimation.progress()` — float 0–1
  - `PillAnimation.width()` — interpolated current width (180→300)
  - `PillAnimation.height()` — interpolated current height (28→160)
  - `PillAnimation.contentAlpha()` — float 0–1 (content fade-in during last 40% of expand)
  - `PillAnimation.isExpanded()` — true when fully expanded
  - `MarqueeText(String text, int boxWidth, net.minecraft.client.font.TextRenderer renderer)` — constructor
  - `MarqueeText.update(String text)` — update text content
  - `MarqueeText.tick(long nowMs)` — advance scroll state
  - `MarqueeText.drawOffset()` — int pixel offset to apply when rendering

- [ ] **Step 1: Create PillAnimation.java**

```java
// common-src/com/evand/musicplayer/hud/PillAnimation.java
package com.evand.musicplayer.hud;

public class PillAnimation {
    private static final float PILL_W = 180f;
    private static final float PILL_H =  28f;
    private static final float CARD_W = 300f;
    private static final float CARD_H = 160f;
    private static final float EXPAND_MS  = 250f;
    private static final float COLLAPSE_MS = 200f;

    private float progress = 0f;   // 0 = collapsed, 1 = expanded
    private boolean expanding = false;

    public void expand()   { expanding = true; }
    public void collapse() { expanding = false; }

    public void tick(float deltaMs) {
        if (expanding) {
            progress = Math.min(1f, progress + deltaMs / EXPAND_MS);
        } else {
            progress = Math.max(0f, progress - deltaMs / COLLAPSE_MS);
        }
    }

    public float progress()      { return progress; }
    public boolean isExpanded()  { return progress >= 1f; }
    public boolean isCollapsed() { return progress <= 0f; }

    public float width()  { return lerp(PILL_W, CARD_W, easeOut(progress)); }
    public float height() { return lerp(PILL_H, CARD_H, easeOut(progress)); }

    /** Content fades in during last 40% of expand. Fades out immediately on collapse. */
    public float contentAlpha() {
        if (!expanding && progress < 1f) return Math.max(0f, progress * 2.5f - 1.5f);
        float t = (progress - 0.6f) / 0.4f;
        return Math.max(0f, Math.min(1f, t));
    }

    private static float easeOut(float t) { return 1f - (1f - t) * (1f - t) * (1f - t); }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}
```

- [ ] **Step 2: Create MarqueeText.java**

```java
// common-src/com/evand/musicplayer/hud/MarqueeText.java
package com.evand.musicplayer.hud;

import net.minecraft.client.font.TextRenderer;

public class MarqueeText {
    private static final float SCROLL_PX_PER_MS = 0.03f; // 30px/sec
    private static final long  PAUSE_MS         = 500L;

    private String text;
    private int    boxWidth;
    private final TextRenderer renderer;

    private int   textWidth   = 0;
    private float offset      = 0f;
    private boolean pausing   = false;
    private long   pauseStart = 0L;
    private boolean forward   = true;

    public MarqueeText(String text, int boxWidth, TextRenderer renderer) {
        this.renderer = renderer;
        this.boxWidth = boxWidth;
        update(text);
    }

    public void update(String newText) {
        if (newText == null) newText = "";
        if (!newText.equals(this.text)) {
            this.text   = newText;
            textWidth   = renderer.getWidth(newText);
            offset      = 0f;
            forward     = true;
            pausing     = false;
        }
    }

    public void setBoxWidth(int width) { this.boxWidth = width; }

    public void tick(long nowMs, float deltaMs) {
        if (textWidth <= boxWidth) { offset = 0; return; }

        float overflow = textWidth - boxWidth;

        if (pausing) {
            if (nowMs - pauseStart >= PAUSE_MS) {
                pausing = false;
                forward = !forward;
            }
            return;
        }

        if (forward) {
            offset += SCROLL_PX_PER_MS * deltaMs;
            if (offset >= overflow) {
                offset = overflow;
                pausing = true;
                pauseStart = nowMs;
            }
        } else {
            offset -= SCROLL_PX_PER_MS * deltaMs;
            if (offset <= 0f) {
                offset = 0f;
                pausing = true;
                pauseStart = nowMs;
            }
        }
    }

    /** Pixel offset to shift text left when rendering. Always >= 0. */
    public int drawOffset() { return (int) offset; }

    public String text()     { return text; }
    public int textWidth()   { return textWidth; }
    public boolean scrolls() { return textWidth > boxWidth; }
}
```

- [ ] **Step 3: Verify compile**

```
cd mc-1.21.11 && gradlew compileJava
```

Expected: Compiles without error.

---

## Task 8: DragHandler

**Files:**
- Create: `common-src/com/evand/musicplayer/hud/DragHandler.java`

**Interfaces:**
- Consumes: `ModConfig` (Task 3)
- Produces:
  - `DragHandler(ModConfig config)` — constructor
  - `DragHandler.onKeyPress(int keyCode)` / `onKeyRelease(int keyCode)` — track held state
  - `DragHandler.onMousePress(double mx, double my, int pillX, int pillY, int pillW, int pillH)` — begin drag if over pill
  - `DragHandler.onMouseRelease()` — end drag, save config
  - `DragHandler.onMouseMove(double mx, double my, int screenW, int screenH, int pillW, int pillH)` — update position
  - `DragHandler.isDragging()` — boolean
  - `DragHandler.isKeyHeld()` — boolean
  - `DragHandler.getPillX(int screenW, int pillW)` — current pixel X
  - `DragHandler.getPillY(int screenH, int pillH)` — current pixel Y

- [ ] **Step 1: Create DragHandler.java**

```java
// common-src/com/evand/musicplayer/hud/DragHandler.java
package com.evand.musicplayer.hud;

import com.evand.musicplayer.config.ModConfig;

public class DragHandler {
    private final ModConfig config;

    private boolean keyHeld    = false;
    private boolean dragging   = false;
    private double  offsetX    = 0;
    private double  offsetY    = 0;
    private double  currentX   = -1; // -1 = use frac from config
    private double  currentY   = -1;

    public DragHandler(ModConfig config) {
        this.config = config;
    }

    public void onKeyPress(boolean isAlt)   { keyHeld = isAlt; if (!keyHeld) endDrag(); }
    public void onKeyRelease(boolean isAlt) { if (!isAlt) { keyHeld = false; endDrag(); } }

    public void onMousePress(double mx, double my, int px, int py, int pw, int ph) {
        if (!keyHeld) return;
        if (mx >= px && mx <= px + pw && my >= py && my <= py + ph) {
            dragging = true;
            offsetX  = mx - px;
            offsetY  = my - py;
        }
    }

    public void onMouseRelease() { endDrag(); }

    public void onMouseMove(double mx, double my, int screenW, int screenH, int pw, int ph) {
        if (!dragging) return;
        double newX = mx - offsetX;
        double newY = my - offsetY;
        newX = Math.max(0, Math.min(screenW - pw, newX));
        newY = Math.max(0, Math.min(screenH - ph, newY));
        currentX = newX;
        currentY = newY;
    }

    public boolean isDragging() { return dragging; }
    public boolean isKeyHeld()  { return keyHeld; }

    public int getPillX(int screenW, int pillW) {
        if (currentX < 0) currentX = config.pillXFrac * screenW - pillW / 2.0;
        return (int) currentX;
    }

    public int getPillY(int screenH, int pillH) {
        if (currentY < 0) currentY = config.pillYFrac * screenH;
        return (int) currentY;
    }

    private void endDrag() {
        if (!dragging) return;
        dragging = false;
        // Save to config as fractions so it's resolution-independent
        // (screenW/H not available here; saved lazily in MediaHUD on next frame)
        config.save();
    }

    /** Call from MediaHUD after computing pillX/pillY to update config fracs. */
    public void syncToConfig(int pillX, int pillY, int screenW, int screenH) {
        config.pillXFrac = (float)(pillX + 0f) / screenW;
        config.pillYFrac = (float)(pillY + 0f) / screenH;
    }
}
```

- [ ] **Step 2: Verify compile**

```
cd mc-1.21.11 && gradlew compileJava
```

Expected: Compiles without error.

---

## Task 9: RoundedRectRenderer

**Files:**
- Create: `common-src/com/evand/musicplayer/hud/RoundedRectRenderer.java`

**Interfaces:**
- Produces:
  - `RoundedRectRenderer.fill(MatrixStack matrices, float x, float y, float w, float h, float r, int color)` — filled rounded rect
  - `RoundedRectRenderer.fillSquircle(MatrixStack matrices, float x, float y, float size, float r, int color)` — squircle (used for thumbnail mask)
  - `RoundedRectRenderer.drawTextureSquircle(DrawContext ctx, Identifier tex, float x, float y, float size, float r)` — draw texture clipped to squircle

- [ ] **Step 1: Create RoundedRectRenderer.java**

```java
// common-src/com/evand/musicplayer/hud/RoundedRectRenderer.java
package com.evand.musicplayer.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

public class RoundedRectRenderer {
    private static final int CORNER_SEGMENTS = 12;

    /**
     * Draw a filled rounded rectangle. All coordinates are in screen pixels.
     * color is ARGB.
     */
    public static void fill(MatrixStack matrices, float x, float y, float w, float h, float r, int color) {
        r = Math.min(r, Math.min(w / 2f, h / 2f));
        Matrix4f mat = matrices.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        float a = ((color >> 24) & 0xFF) / 255f;
        float rr = ((color >> 16) & 0xFF) / 255f;
        float g  = ((color >> 8)  & 0xFF) / 255f;
        float b  = ( color        & 0xFF) / 255f;

        // Center rectangle (full width, inner height)
        addRect(buf, mat, x, y + r, x + w, y + h - r, rr, g, b, a);
        // Top strip
        addRect(buf, mat, x + r, y, x + w - r, y + r, rr, g, b, a);
        // Bottom strip
        addRect(buf, mat, x + r, y + h - r, x + w - r, y + h, rr, g, b, a);

        // Four corner fans
        addCornerFan(buf, mat, x + r,       y + r,       r, (float)Math.PI,        rr, g, b, a); // TL
        addCornerFan(buf, mat, x + w - r,   y + r,       r, (float)(3*Math.PI/2),  rr, g, b, a); // TR
        addCornerFan(buf, mat, x + w - r,   y + h - r,   r, 0f,                    rr, g, b, a); // BR
        addCornerFan(buf, mat, x + r,       y + h - r,   r, (float)(Math.PI/2),    rr, g, b, a); // BL

        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.disableBlend();
    }

    /**
     * Draw a texture (identifier) clipped to a squircle shape.
     * Renders the texture as a square, then overlays the background color to mask corners.
     * Caller must ensure background color matches the surrounding area (i.e. black).
     */
    public static void drawTextureSquircle(DrawContext ctx, Identifier tex, float x, float y, float size, float r, int bgColor) {
        int ix = (int) x, iy = (int) y, is = (int) size;
        // Draw texture full square
        ctx.drawTexture(tex, ix, iy, 0, 0, is, is, is, is);
        // Mask corners with background color triangles
        maskCorners(ctx.getMatrices(), x, y, size, r, bgColor);
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private static void addRect(BufferBuilder buf, Matrix4f mat,
                                float x1, float y1, float x2, float y2,
                                float r, float g, float b, float a) {
        buf.vertex(mat, x1, y1, 0).color(r, g, b, a);
        buf.vertex(mat, x1, y2, 0).color(r, g, b, a);
        buf.vertex(mat, x2, y1, 0).color(r, g, b, a);
        buf.vertex(mat, x2, y1, 0).color(r, g, b, a);
        buf.vertex(mat, x1, y2, 0).color(r, g, b, a);
        buf.vertex(mat, x2, y2, 0).color(r, g, b, a);
    }

    private static void addCornerFan(BufferBuilder buf, Matrix4f mat,
                                     float cx, float cy, float r, float startAngle,
                                     float red, float g, float b, float a) {
        float step = (float)(Math.PI / 2) / CORNER_SEGMENTS;
        for (int i = 0; i < CORNER_SEGMENTS; i++) {
            float a1 = startAngle + i * step;
            float a2 = a1 + step;
            buf.vertex(mat, cx, cy, 0).color(red, g, b, a);
            buf.vertex(mat, cx + r * (float)Math.cos(a1), cy + r * (float)Math.sin(a1), 0).color(red, g, b, a);
            buf.vertex(mat, cx + r * (float)Math.cos(a2), cy + r * (float)Math.sin(a2), 0).color(red, g, b, a);
        }
    }

    private static void maskCorners(MatrixStack matrices, float x, float y, float size, float r, int color) {
        float a = ((color >> 24) & 0xFF) / 255f;
        float rr= ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8)  & 0xFF) / 255f;
        float b = ( color        & 0xFF) / 255f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        Matrix4f mat = matrices.peek().getPositionMatrix();
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        // Four corners: draw a pie-slice of the bg color to mask the round cutout
        // TL: fill corner square then subtract the arc area
        addCornerMask(buf, mat, x,            y,            r, (float)Math.PI,       rr, g, b, a);
        addCornerMask(buf, mat, x + size - r, y,            r, (float)(3*Math.PI/2), rr, g, b, a);
        addCornerMask(buf, mat, x + size - r, y + size - r, r, 0f,                   rr, g, b, a);
        addCornerMask(buf, mat, x,            y + size - r, r, (float)(Math.PI/2),   rr, g, b, a);

        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.disableBlend();
    }

    /** Fill a corner square, then punch out the arc (leaving only the triangle "ear"). */
    private static void addCornerMask(BufferBuilder buf, Matrix4f mat,
                                      float cx, float cy, float r, float startAngle,
                                      float red, float g, float b, float a) {
        // The "ear" is the part of the r×r square NOT covered by the arc.
        // For each segment, draw a triangle from the corner square origin to fill the gap.
        float step = (float)(Math.PI / 2) / CORNER_SEGMENTS;
        for (int i = 0; i < CORNER_SEGMENTS; i++) {
            float a1 = startAngle + i * step;
            float a2 = a1 + step;
            // Triangle: corner point, arc point 1, arc point 2 — this fills the "leftover" area
            float ox = cx + r; float oy = cy + r; // center of the corner
            buf.vertex(mat, ox, oy, 0).color(red, g, b, a);
            buf.vertex(mat, ox + r * (float)Math.cos(a1), oy + r * (float)Math.sin(a1), 0).color(red, g, b, a);
            buf.vertex(mat, ox + r * (float)Math.cos(a2), oy + r * (float)Math.sin(a2), 0).color(red, g, b, a);
        }
    }
}
```

- [ ] **Step 2: Verify compile**

```
cd mc-1.21.11 && gradlew compileJava
```

Expected: Compiles without error. (ShaderProgramKeys import may need adjustment depending on exact MC version — check yarn mappings if needed.)

---

## Task 10: PillRenderer and CardRenderer

**Files:**
- Create: `common-src/com/evand/musicplayer/hud/PillRenderer.java`
- Create: `common-src/com/evand/musicplayer/hud/CardRenderer.java`

**Interfaces:**
- Consumes: `RoundedRectRenderer`, `MarqueeText`, `ThumbnailManager`, `MediaInfo`, `MediaController`, `PillAnimation`
- Produces:
  - `PillRenderer.draw(DrawContext ctx, MediaInfo info, MarqueeText marquee, int x, int y, int w, int h)` — draw collapsed pill
  - `CardRenderer.draw(DrawContext ctx, MediaInfo info, MarqueeText marquee, PillAnimation anim, int x, int y, float alpha, SeekCallback onSeek)` — draw expanded card
  - `CardRenderer.SeekCallback` — functional interface `void seek(long positionMs)`

- [ ] **Step 1: Create PillRenderer.java**

```java
// common-src/com/evand/musicplayer/hud/PillRenderer.java
package com.evand.musicplayer.hud;

import com.evand.musicplayer.media.MediaInfo;
import com.evand.musicplayer.media.ThumbnailManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

public class PillRenderer {
    private static final int BG_COLOR    = 0xFF000000; // pure black, fully opaque
    private static final int TEXT_COLOR  = 0xFFFFFFFF;
    private static final int CORNER_R    = 14;
    private static final int THUMB_SIZE  = 20;
    private static final int THUMB_R     = 5;
    private static final int PADDING     = 4;

    public static void draw(DrawContext ctx, MediaInfo info, MarqueeText marquee,
                             int x, int y, int w, int h) {
        // Background pill
        RoundedRectRenderer.fill(ctx.getMatrices(), x, y, w, h, CORNER_R, BG_COLOR);

        int thumbX = x + PADDING;
        int thumbY = y + (h - THUMB_SIZE) / 2;

        // Thumbnail or grey placeholder
        Identifier thumb = ThumbnailManager.INSTANCE.getTexture();
        if (thumb != null) {
            RoundedRectRenderer.drawTextureSquircle(ctx, thumb, thumbX, thumbY, THUMB_SIZE, THUMB_R, BG_COLOR);
        } else {
            RoundedRectRenderer.fill(ctx.getMatrices(), thumbX, thumbY, THUMB_SIZE, THUMB_SIZE, THUMB_R, 0xFF333333);
        }

        // Title text (scrolled)
        int textX     = thumbX + THUMB_SIZE + PADDING;
        int textMaxW  = w - textX - PADDING - x;
        int textY     = y + (h - 8) / 2; // 8 = default MC font height

        // Enable scissor to clip marquee text
        ctx.enableScissor(textX, y, textX + textMaxW, y + h);
        var tr = MinecraftClient.getInstance().textRenderer;
        String title = info != null ? (info.title() != null ? info.title() : "No media") : "No media";
        marquee.update(title);
        marquee.setBoxWidth(textMaxW);
        ctx.drawText(tr, title, textX - marquee.drawOffset(), textY, TEXT_COLOR, false);
        ctx.disableScissor();
    }
}
```

- [ ] **Step 2: Create CardRenderer.java**

```java
// common-src/com/evand/musicplayer/hud/CardRenderer.java
package com.evand.musicplayer.hud;

import com.evand.musicplayer.media.MediaController;
import com.evand.musicplayer.media.MediaInfo;
import com.evand.musicplayer.media.ThumbnailManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

public class CardRenderer {
    @FunctionalInterface
    public interface SeekCallback { void seek(long positionMs); }

    private static final int BG_COLOR      = 0xFF000000;
    private static final int TEXT_COLOR    = 0xFFFFFFFF;
    private static final int DIM_COLOR     = 0xFF888888;
    private static final int BAR_BG        = 0xFF333333;
    private static final int BAR_FG        = 0xFFFFFFFF;
    private static final int CORNER_R      = 14;
    private static final int THUMB_SIZE    = 60;
    private static final int THUMB_R       = 10;
    private static final int PADDING       = 10;
    private static final int BUTTON_W      = 28;
    private static final int BUTTON_H      = 20;
    private static final int BAR_H         = 4;

    /** Last known button bounds (for click detection, updated each draw). */
    public static int prevBtnX, prevBtnY, playBtnX, playBtnY, nextBtnX, nextBtnY;
    public static int btnW = BUTTON_W, btnH = BUTTON_H;
    public static int barX, barY, barWidth;

    public static void draw(DrawContext ctx, MediaInfo info, MarqueeText marquee,
                            PillAnimation anim, int x, int y, float alpha, SeekCallback onSeek) {
        int w = (int) anim.width();
        int h = (int) anim.height();

        // Background card
        RoundedRectRenderer.fill(ctx.getMatrices(), x, y, w, h, CORNER_R, BG_COLOR);

        if (alpha <= 0.01f) return;
        int a = (int)(alpha * 255) << 24;
        int textCol  = (TEXT_COLOR & 0x00FFFFFF) | a;
        int dimCol   = (DIM_COLOR  & 0x00FFFFFF) | a;

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int cx = x + w / 2;
        int curY = y + PADDING;

        // Thumbnail
        int thumbX = cx - THUMB_SIZE / 2;
        Identifier thumb = ThumbnailManager.INSTANCE.getTexture();
        if (thumb != null) {
            RoundedRectRenderer.drawTextureSquircle(ctx, thumb, thumbX, curY, THUMB_SIZE, THUMB_R, BG_COLOR);
        } else {
            RoundedRectRenderer.fill(ctx.getMatrices(), thumbX, curY, THUMB_SIZE, THUMB_SIZE, THUMB_R, 0xFF333333);
        }
        curY += THUMB_SIZE + PADDING;

        // Title (marquee)
        String title  = info != null ? coalesce(info.title(), "Unknown") : "No media";
        String artist = info != null ? coalesce(info.artist(), "") : "";
        int textAreaW = w - PADDING * 2;
        marquee.update(title);
        marquee.setBoxWidth(textAreaW);
        ctx.enableScissor(x + PADDING, curY, x + PADDING + textAreaW, curY + 10);
        ctx.drawText(tr, title, x + PADDING - marquee.drawOffset(), curY, textCol, false);
        ctx.disableScissor();
        curY += 10;

        // Artist
        if (!artist.isEmpty()) {
            ctx.drawText(tr, artist, x + PADDING, curY, dimCol, false);
            curY += 10;
        }
        curY += 4;

        // Timeline bar
        if (info != null && info.durationMs() > 0) {
            barX     = x + PADDING;
            barY     = curY;
            barWidth = w - PADDING * 2;

            // Background bar
            RoundedRectRenderer.fill(ctx.getMatrices(), barX, barY, barWidth, BAR_H, BAR_H / 2f, (BAR_BG & 0x00FFFFFF) | a);
            // Progress bar
            int progW = (int)(barWidth * info.progress());
            if (progW > 0) {
                RoundedRectRenderer.fill(ctx.getMatrices(), barX, barY, progW, BAR_H, BAR_H / 2f, (BAR_FG & 0x00FFFFFF) | a);
            }

            curY += BAR_H + 3;
            // Time labels
            ctx.drawText(tr, info.elapsedFormatted(),   barX,              curY, dimCol, false);
            String rem = info.remainingFormatted();
            ctx.drawText(tr, rem, barX + barWidth - tr.getWidth(rem), curY, dimCol, false);
            curY += 10 + PADDING;
        } else {
            curY += BAR_H + 3 + 10 + PADDING;
        }

        // Buttons row: [⏮] [⏯] [⏭]
        int totalBtnW = BUTTON_W * 3 + PADDING * 2;
        int btnStartX = cx - totalBtnW / 2;

        prevBtnX = btnStartX;                     prevBtnY = curY;
        playBtnX = btnStartX + BUTTON_W + PADDING; playBtnY = curY;
        nextBtnX = btnStartX + (BUTTON_W + PADDING) * 2; nextBtnY = curY;

        drawButton(ctx, tr, prevBtnX, curY, BUTTON_W, BUTTON_H, "⏮", textCol);
        String playLabel = (info != null && info.isPlaying()) ? "⏸" : "▶";
        drawButton(ctx, tr, playBtnX, curY, BUTTON_W, BUTTON_H, playLabel, textCol);
        drawButton(ctx, tr, nextBtnX, curY, BUTTON_W, BUTTON_H, "⏭", textCol);
    }

    private static void drawButton(DrawContext ctx, TextRenderer tr,
                                   int x, int y, int w, int h, String label, int color) {
        RoundedRectRenderer.fill(ctx.getMatrices(), x, y, w, h, 4, 0xFF1A1A1A);
        int lw = tr.getWidth(label);
        int lh = 8;
        ctx.drawText(tr, label, x + (w - lw) / 2, y + (h - lh) / 2, color, false);
    }

    private static String coalesce(String s, String fallback) {
        return (s != null && !s.isEmpty()) ? s : fallback;
    }
}
```

- [ ] **Step 3: Verify compile**

```
cd mc-1.21.11 && gradlew compileJava
```

Expected: Compiles without error.

---

## Task 11: MediaHUD Orchestrator

**Files:**
- Create: `common-src/com/evand/musicplayer/hud/MediaHUD.java`

**Interfaces:**
- Consumes: all previous classes
- Produces:
  - `MediaHUD.INSTANCE` — singleton
  - `MediaHUD.init(ModConfig config)` — call from client entrypoint
  - `MediaHUD.render(DrawContext ctx, float tickDelta)` — HUD render callback body
  - `MediaHUD.onMouseClick(double mx, double my, int button)` — returns true if consumed
  - `MediaHUD.onMouseRelease(double mx, double my, int button)` — pass to drag handler
  - `MediaHUD.onMouseMove(double mx, double my)` — pass to drag handler
  - `MediaHUD.onKeyPress(int keyCode, boolean isAlt)` — pass to drag handler
  - `MediaHUD.onKeyRelease(int keyCode, boolean isAlt)` — pass to drag handler

- [ ] **Step 1: Create MediaHUD.java**

```java
// common-src/com/evand/musicplayer/hud/MediaHUD.java
package com.evand.musicplayer.hud;

import com.evand.musicplayer.config.ModConfig;
import com.evand.musicplayer.media.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class MediaHUD {
    public static final MediaHUD INSTANCE = new MediaHUD();

    private ModConfig      config;
    private PillAnimation  anim;
    private MarqueeText    marquee;
    private DragHandler    drag;

    private long lastFrameMs = System.currentTimeMillis();

    private MediaHUD() {}

    public void init(ModConfig config) {
        this.config  = config;
        this.anim    = new PillAnimation();
        this.drag    = new DragHandler(config);
        // marquee initialised lazily when we first have a renderer
        MediaPoller.INSTANCE.start();
        MediaController.INSTANCE.init();
    }

    public void render(DrawContext ctx, float tickDelta) {
        var client = MinecraftClient.getInstance();
        if (client.options.hudHidden) return;

        long  nowMs    = System.currentTimeMillis();
        float deltaMs  = nowMs - lastFrameMs;
        lastFrameMs    = nowMs;

        anim.tick(deltaMs);
        ThumbnailManager.INSTANCE.tick();

        // Determine what to show
        MediaInfo osInfo  = MediaPoller.INSTANCE.get();
        String    mcTrack = MediaPoller.INSTANCE.getMinecraftTrack();
        MediaInfo display;

        if (osInfo != null) {
            display = osInfo;
        } else if (mcTrack != null) {
            display = new MediaInfo(mcTrack, null, true, 0, 0, null, "minecraft");
        } else {
            // Nothing playing — hide pill
            if (anim.isCollapsed()) return;
            display = null;
        }

        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();

        int pillW = (int) anim.width();
        int pillH = (int) anim.height();

        int pillX = drag.getPillX(screenW, pillW);
        int pillY = drag.getPillY(screenH, pillH);
        drag.syncToConfig(pillX, pillY, screenW, screenH);

        // Lazy init marquee now that we have a text renderer
        if (marquee == null) {
            marquee = new MarqueeText("", pillW - 38, client.textRenderer);
        }
        marquee.tick(nowMs, deltaMs);

        if (anim.isCollapsed() || anim.progress() < 0.05f) {
            // Draw collapsed pill
            PillRenderer.draw(ctx, display, marquee, pillX, pillY, pillW, pillH);
        } else {
            // Draw expanding/expanded card
            CardRenderer.draw(ctx, display, marquee, anim, pillX, pillY, anim.contentAlpha(),
                posMs -> MediaController.INSTANCE.seek(posMs));
        }
    }

    public boolean onMouseClick(double mx, double my, int button) {
        if (button == 1) { // right click
            int pillW = (int) anim.width();
            int pillH = (int) anim.height();
            var client = MinecraftClient.getInstance();
            int pillX = drag.getPillX(client.getWindow().getScaledWidth(), pillW);
            int pillY = drag.getPillY(client.getWindow().getScaledHeight(), pillH);

            if (mx >= pillX && mx <= pillX + pillW && my >= pillY && my <= pillY + pillH) {
                if (anim.isCollapsed() || anim.progress() < 0.5f) {
                    anim.expand();
                } else {
                    anim.collapse();
                }
                return true;
            }
        }

        if (button == 0 && !anim.isCollapsed()) { // left click on expanded card
            // Check buttons
            if (inBounds(mx, my, CardRenderer.prevBtnX, CardRenderer.prevBtnY, CardRenderer.btnW, CardRenderer.btnH)) {
                MediaController.INSTANCE.previous(); return true;
            }
            if (inBounds(mx, my, CardRenderer.playBtnX, CardRenderer.playBtnY, CardRenderer.btnW, CardRenderer.btnH)) {
                MediaController.INSTANCE.toggle(); return true;
            }
            if (inBounds(mx, my, CardRenderer.nextBtnX, CardRenderer.nextBtnY, CardRenderer.btnW, CardRenderer.btnH)) {
                MediaController.INSTANCE.next(); return true;
            }
            // Check timeline seek
            if (my >= CardRenderer.barY && my <= CardRenderer.barY + 8
                && mx >= CardRenderer.barX && mx <= CardRenderer.barX + CardRenderer.barWidth) {
                MediaInfo info = MediaPoller.INSTANCE.get();
                if (info != null && info.durationMs() > 0) {
                    float frac = (float)(mx - CardRenderer.barX) / CardRenderer.barWidth;
                    MediaController.INSTANCE.seek((long)(frac * info.durationMs()));
                    return true;
                }
            }
        }

        if (button == 0) { // left click — also start drag
            int pillW = (int) anim.width();
            int pillH = (int) anim.height();
            var client = MinecraftClient.getInstance();
            int pillX = drag.getPillX(client.getWindow().getScaledWidth(), pillW);
            int pillY = drag.getPillY(client.getWindow().getScaledHeight(), pillH);
            drag.onMousePress(mx, my, pillX, pillY, pillW, pillH);
        }

        return false;
    }

    public void onMouseRelease(double mx, double my, int button) {
        if (button == 0) drag.onMouseRelease();
    }

    public void onMouseMove(double mx, double my) {
        var client = MinecraftClient.getInstance();
        drag.onMouseMove(mx, my,
            client.getWindow().getScaledWidth(),
            client.getWindow().getScaledHeight(),
            (int) anim.width(),
            (int) anim.height());
    }

    public void onKeyPress(boolean isAlt)   { drag.onKeyPress(isAlt); }
    public void onKeyRelease(boolean isAlt) { drag.onKeyRelease(isAlt); }

    private static boolean inBounds(double mx, double my, int bx, int by, int bw, int bh) {
        return mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
    }
}
```

- [ ] **Step 2: Verify compile**

```
cd mc-1.21.11 && gradlew compileJava
```

Expected: Compiles without error.

---

## Task 12: Client Entrypoints, Mixin, and Final Wiring

**Files:**
- Create: `mc-1.21.11/src/main/java/com/evand/musicplayer/client/MusicPlayerClient.java`
- Create: `mc-1.21.11/src/main/java/com/evand/musicplayer/mixin/MusicTrackerMixin.java`
- Create: `mc-26.1.2/src/main/java/com/evand/musicplayer/client/MusicPlayerClient.java` (identical)
- Create: `mc-26.1.2/src/main/java/com/evand/musicplayer/mixin/MusicTrackerMixin.java` (identical)

**Interfaces:**
- Consumes: all previous classes
- Produces: fully wired, runnable mod

- [ ] **Step 1: Create MusicPlayerClient.java for mc-1.21.11**

```java
// mc-1.21.11/src/main/java/com/evand/musicplayer/client/MusicPlayerClient.java
package com.evand.musicplayer.client;

import com.evand.musicplayer.config.ModConfig;
import com.evand.musicplayer.hud.MediaHUD;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class MusicPlayerClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ModConfig config = ModConfig.load();
        MediaHUD.INSTANCE.init(config);

        // Register HUD render callback
        HudRenderCallback.EVENT.register((context, tickDelta) ->
            MediaHUD.INSTANCE.render(context, tickDelta)
        );

        // Key events via tick (poll GLFW directly for Alt key)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean altHeld = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_ALT)
                              == GLFW.GLFW_PRESS;
            MediaHUD.INSTANCE.onKeyPress(altHeld);
        });

        // Mouse events via screen callbacks on every screen (including in-game null screen)
        // For in-game HUD mouse, we use the raw GLFW mouse callback
        long window = MinecraftClient.getInstance().getWindow().getHandle();

        GLFW.glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            double[] mx = new double[1], my = new double[1];
            GLFW.glfwGetCursorPos(win, mx, my);
            // Scale to GUI coords
            MinecraftClient mc = MinecraftClient.getInstance();
            double scale = mc.getWindow().getScaleFactor();
            double gx = mx[0] / scale;
            double gy = my[0] / scale;

            if (action == GLFW.GLFW_PRESS) {
                MediaHUD.INSTANCE.onMouseClick(gx, gy, button);
            } else if (action == GLFW.GLFW_RELEASE) {
                MediaHUD.INSTANCE.onMouseRelease(gx, gy, button);
            }
        });

        GLFW.glfwSetCursorPosCallback(window, (win, x, y) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            double scale = mc.getWindow().getScaleFactor();
            MediaHUD.INSTANCE.onMouseMove(x / scale, y / scale);
        });
    }
}
```

> **Note:** Replacing GLFW callbacks directly will override Minecraft's existing callbacks (mouse look, GUI clicks). For production quality, use Mixins to inject into `Mouse` class instead. The implementation above is correct for a standalone HUD but may conflict with normal mouse handling in menus. A safer approach is covered in the optional Task 13 below.

- [ ] **Step 2: Create MusicTrackerMixin.java for mc-1.21.11**

```java
// mc-1.21.11/src/main/java/com/evand/musicplayer/mixin/MusicTrackerMixin.java
package com.evand.musicplayer.mixin;

import com.evand.musicplayer.media.MediaPoller;
import net.minecraft.client.sound.MusicTracker;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.MusicSound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MusicTracker.class)
public abstract class MusicTrackerMixin {
    @Shadow private SoundInstance current;

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        if (current == null) {
            MediaPoller.INSTANCE.setMinecraftTrack(null);
        } else {
            String id = current.getId().toString();
            // Convert sound ID to a display name (e.g. minecraft:music.game -> Game Music)
            String display = id.contains(".")
                ? capitalize(id.substring(id.lastIndexOf('.') + 1).replace('_', ' '))
                : capitalize(id.replace('_', ' '));
            MediaPoller.INSTANCE.setMinecraftTrack(display);
        }
    }

    private static String capitalize(String s) {
        if (s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
```

- [ ] **Step 3: Copy both files to mc-26.1.2** (identical content)

```
copy mc-1.21.11\src\main\java\com\evand\musicplayer\client\MusicPlayerClient.java ^
     mc-26.1.2\src\main\java\com\evand\musicplayer\client\MusicPlayerClient.java

copy mc-1.21.11\src\main\java\com\evand\musicplayer\mixin\MusicTrackerMixin.java ^
     mc-26.1.2\src\main\java\com\evand\musicplayer\mixin\MusicTrackerMixin.java
```

- [ ] **Step 4: Build both JARs**

```
cd mc-1.21.11 && gradlew build
cd mc-26.1.2  && gradlew build
```

Expected output:
- `mc-1.21.11/build/libs/musicplayer-1.21.11.jar`
- `mc-26.1.2/build/libs/musicplayer-26.1.2.jar`

- [ ] **Step 5: Test in-game (1.21.11)**

1. Copy `mc-1.21.11/build/libs/musicplayer-1.21.11.jar` to your 1.21.11 Fabric mods folder
2. Launch Minecraft 1.21.11
3. Start Spotify, play a song
4. Confirm pill appears at top center showing album art + song title
5. Right-click pill → confirm card expands with smooth animation
6. Click play/pause button → Spotify pauses/resumes
7. Click timeline bar → playback position jumps
8. Hold Alt + drag pill to new position
9. Restart game → confirm pill appears at saved position
10. Stop Spotify → confirm pill disappears

- [ ] **Step 6: Test in-game (26.1.2)**

Repeat Step 5 using the 26.1.2 JAR and Minecraft 26.1.2.

---

## Task 13 (Optional — Safe Mouse Wiring via Mixin)

If GLFW callback replacement (Task 12 Step 1) conflicts with in-game mouse handling, replace it with Mixin injections into Minecraft's `Mouse` class.

**File:**
- Create: `mc-1.21.11/src/main/java/com/evand/musicplayer/mixin/MouseMixin.java` (same for 26.1.2)
- Modify: `mc-1.21.11/src/main/resources/musicplayer.mixins.json` — add `"MouseMixin"` to `"client"` array

```java
// MouseMixin.java
package com.evand.musicplayer.mixin;

import com.evand.musicplayer.hud.MediaHUD;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {
    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        // action 1 = press, 0 = release
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        double scale = mc.getWindow().getScaleFactor();
        double[] mx = new double[1], my = new double[1];
        org.lwjgl.glfw.GLFW.glfwGetCursorPos(window, mx, my);
        double gx = mx[0] / scale, gy = my[0] / scale;
        if (action == 1) MediaHUD.INSTANCE.onMouseClick(gx, gy, button);
        else             MediaHUD.INSTANCE.onMouseRelease(gx, gy, button);
    }

    @Inject(method = "onCursorPos", at = @At("HEAD"))
    private void onCursorPos(long window, double x, double y, CallbackInfo ci) {
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        double scale = mc.getWindow().getScaleFactor();
        MediaHUD.INSTANCE.onMouseMove(x / scale, y / scale);
    }
}
```

Add `"MouseMixin"` to the `"client"` array in `musicplayer.mixins.json` for both versions. Remove GLFW callback registration from `MusicPlayerClient.java`.

---

## Verification Checklist

- [ ] Both JARs build without error
- [ ] Pill visible with Spotify playing
- [ ] Pill hidden when nothing is playing
- [ ] Right-click expands card with smooth cubic animation
- [ ] Second right-click collapses card
- [ ] Play/pause button controls Spotify
- [ ] Skip next/previous buttons work
- [ ] Timeline click seeks to correct position
- [ ] Alt+drag repositions pill, saved across restarts
- [ ] Minecraft music track name shows in pill when no OS media active
- [ ] No crash when mod loads on Windows with no media app open
- [ ] No crash when mod loads (confirm SMTC unavailability is handled gracefully)
