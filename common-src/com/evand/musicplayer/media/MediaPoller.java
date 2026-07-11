// common-src/com/evand/musicplayer/media/MediaPoller.java
package com.evand.musicplayer.media;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;

public class MediaPoller {
    public static final MediaPoller INSTANCE = new MediaPoller();

    private static final long POLL_INTERVAL_MS = 500;
    private static final Gson GSON = new Gson();

    private final AtomicReference<MediaInfo> osMediaRef = new AtomicReference<>(null);
    private final AtomicReference<String>    mcTrackRef = new AtomicReference<>(null);

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
