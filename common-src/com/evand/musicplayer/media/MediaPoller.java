// common-src/com/evand/musicplayer/media/MediaPoller.java
package com.evand.musicplayer.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class MediaPoller {
    public static final MediaPoller INSTANCE = new MediaPoller();

    private static final long    POLL_INTERVAL_MS = 200;
    private static final int     MAX_FAIL_COUNT   = 5;
    private static final boolean IS_WINDOWS       =
        System.getProperty("os.name", "").toLowerCase().startsWith("win");

    // Must use PS 5.1 (powershell.exe) — the media script uses WinRT reflection that
    // fails under PS 7 / .NET Core (GetParameters() throws 0x80131539 on WinRT MethodInfo).
    // PS 5.1 is built into Windows and cannot be removed, so this is always safe.
    private static final String PS_EXE = "powershell.exe";

    private final AtomicReference<MediaInfo> osMediaRef = new AtomicReference<>(null);
    private final AtomicReference<String>    mcTrackRef = new AtomicReference<>(null);

    private volatile Thread  pollThread;
    private volatile boolean running      = false;
    private volatile long    lastPollTime = 0L;
    private Path scriptPath;

    private Process        pollerProcess = null;
    private PrintWriter    pollerStdin   = null;
    private BufferedReader pollerStdout  = null;
    private int            failCount     = 0;

    private String lastQueuedThumb = null;
    private String lastTitle       = null;
    private String lastSourceApp   = null;

    private MediaPoller() {}

    public void start() {
        if (running) return;
        if (IS_WINDOWS) {
            scriptPath = extractScript("get_media_server.ps1");
            if (scriptPath == null) {
                System.err.println("[MusicPlayer] Failed to extract get_media_server.ps1");
            }
        } else {
            System.out.println("[MusicPlayer] Non-Windows OS — SMTC polling disabled");
        }
        running = true;
        pollThread = new Thread(this::pollLoop, "MusicPlayer-Poller");
        pollThread.setDaemon(true);
        pollThread.start();
    }

    public void stop() {
        running = false;
        if (pollThread != null) pollThread.interrupt();
        stopPoller();
    }

    public MediaInfo get()                        { return osMediaRef.get(); }
    public long      getLastPollTimeMs()          { return lastPollTime; }
    public void      setMinecraftTrack(String t)  { mcTrackRef.set(t); }
    public String    getMinecraftTrack()          { return mcTrackRef.get(); }

    private void pollLoop() {
        while (running) {
            if (scriptPath != null) {
                try {
                    MediaInfo info = pollSmtc();
                    failCount    = 0;
                    lastPollTime = System.currentTimeMillis();
                    osMediaRef.set(info);

                    if (info != null && info.hasThumbnail()) {
                        String thumb  = info.thumbnailPath();
                        String title  = info.title();
                        String source = info.sourceApp();
                        boolean changed = !thumb.equals(lastQueuedThumb)
                                || !Objects.equals(title,  lastTitle)
                                || !Objects.equals(source, lastSourceApp);
                        if (changed) {
                            ThumbnailManager.INSTANCE.queuePath(thumb);
                            lastQueuedThumb = thumb;
                            lastTitle       = title;
                            lastSourceApp   = source;
                        }
                    } else if (lastQueuedThumb != null && !lastQueuedThumb.isEmpty()) {
                        ThumbnailManager.INSTANCE.queuePath("");
                        lastQueuedThumb = "";
                    }

                } catch (Exception e) {
                    stopPoller();
                    failCount++;
                    osMediaRef.set(null);
                    if (lastQueuedThumb != null && !lastQueuedThumb.isEmpty()) {
                        ThumbnailManager.INSTANCE.queuePath("");
                        lastQueuedThumb = "";
                    }
                    if (failCount >= MAX_FAIL_COUNT) {
                        try { Thread.sleep(10_000); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); return; }
                        failCount = 0;
                    }
                }
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        stopPoller();
    }

    private MediaInfo pollSmtc() throws Exception {
        ensurePollerRunning();
        pollerStdin.println("poll");
        String line = pollerStdout.readLine();
        if (line == null) {
            stopPoller();
            throw new IOException("Poller stdout closed");
        }
        // Strip UTF-8 BOM (U+FEFF) that PS 5.1 can emit at stream start.
        if (!line.isEmpty() && line.charAt(0) == '﻿') line = line.substring(1);
        line = line.trim();
        if (line.isEmpty() || line.equals("{}")) return null;

        JsonObject json    = JsonParser.parseString(line).getAsJsonObject();
        String  title         = getStr(json, "title");
        String  artist        = getStr(json, "artist");
        boolean isPlaying     = json.has("isPlaying") && json.get("isPlaying").getAsBoolean();
        long    positionMs    = json.has("positionMs") ? json.get("positionMs").getAsLong() : 0;
        long    durationMs    = json.has("durationMs") ? json.get("durationMs").getAsLong() : 0;
        String  thumbnailPath = getStr(json, "thumbnailPath");
        String  sourceApp     = getStr(json, "sourceApp");

        if (title == null || title.isEmpty()) return null;
        return new MediaInfo(title, artist, isPlaying, positionMs, durationMs, thumbnailPath, sourceApp);
    }

    private synchronized void ensurePollerRunning() throws Exception {
        if (pollerProcess != null && pollerProcess.isAlive()) return;
        stopPoller();

        ProcessBuilder pb = new ProcessBuilder(
            PS_EXE, "-ExecutionPolicy", "Bypass",
            "-NoProfile", "-NonInteractive",
            "-File", scriptPath.toAbsolutePath().toString()
        );
        pollerProcess = pb.start();
        pollerStdin   = new PrintWriter(
            new OutputStreamWriter(pollerProcess.getOutputStream(), StandardCharsets.UTF_8), true);
        pollerStdout  = new BufferedReader(
            new InputStreamReader(pollerProcess.getInputStream(), StandardCharsets.UTF_8));

        Process p = pollerProcess;
        Thread stderr = new Thread(() -> {
            try { p.getErrorStream().transferTo(OutputStream.nullOutputStream()); }
            catch (Exception ignored) {}
        }, "MusicPlayer-Poller-Err");
        stderr.setDaemon(true);
        stderr.start();
    }

    private void stopPoller() {
        try { if (pollerProcess != null) pollerProcess.destroy(); } catch (Exception ignored) {}
        pollerProcess = null;
        pollerStdin   = null;
        pollerStdout  = null;
    }

    private static String getStr(JsonObject json, String key) {
        return json.has(key) ? json.get(key).getAsString() : null;
    }

    private static Path extractScript(String name) {
        try {
            Path dir  = Path.of(System.getProperty("java.io.tmpdir"), "musicplayer");
            Files.createDirectories(dir);
            Path dest = dir.resolve(name);
            try (InputStream is = MediaPoller.class.getResourceAsStream("/scripts/" + name)) {
                if (is == null) return null;
                Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
            }
            return dest;
        } catch (IOException e) {
            return null;
        }
    }
}
