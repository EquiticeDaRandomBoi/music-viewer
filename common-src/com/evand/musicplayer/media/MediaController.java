// common-src/com/evand/musicplayer/media/MediaController.java
package com.evand.musicplayer.media;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class MediaController {
    public static final MediaController INSTANCE = new MediaController();

    private volatile Path        serverPath;
    private volatile Process     server;
    private volatile PrintWriter serverStdin;
    private volatile boolean     available;

    private MediaController() {}

    public void init() {
        serverPath = extractScript("control_media_server.ps1");
        available  = serverPath != null;
        if (available) {
            // Pre-warm: start PS now so the first button press is instant
            Thread t = new Thread(this::startServer, "MusicPlayer-ctrl-start");
            t.setDaemon(true);
            t.start();
        }
    }

    public void toggle()              { send("toggle"); }
    public void next()                { send("next"); }
    public void previous()            { send("previous"); }
    public void seek(long positionMs) { send("seek " + positionMs); }

    private void send(String cmd) {
        if (!available) return;
        if (server == null || !server.isAlive()) {
            startServer(); // restart synchronously; cmd queues in the pipe
        }
        PrintWriter w = serverStdin;
        if (w == null) return;
        try {
            w.println(cmd);
        } catch (Exception e) {
            server      = null;
            serverStdin = null;
        }
    }

    private synchronized void startServer() {
        if (server != null && server.isAlive()) return;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "powershell.exe", "-ExecutionPolicy", "Bypass",
                "-NoProfile", "-NonInteractive",
                "-File", serverPath.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            server      = pb.start();
            serverStdin = new PrintWriter(
                new OutputStreamWriter(server.getOutputStream(), StandardCharsets.UTF_8), true
            );
            Process p = server;
            Thread drainer = new Thread(() -> {
                try { p.getInputStream().transferTo(OutputStream.nullOutputStream()); }
                catch (Exception ignored) {}
            }, "MusicPlayer-ctrl-drain");
            drainer.setDaemon(true);
            drainer.start();
        } catch (Exception e) {
            server      = null;
            serverStdin = null;
        }
    }

    private static Path extractScript(String name) {
        try {
            Path dir  = Path.of(System.getProperty("java.io.tmpdir"), "musicplayer");
            Files.createDirectories(dir);
            Path dest = dir.resolve(name);
            try (InputStream is = MediaController.class.getResourceAsStream("/scripts/" + name)) {
                if (is == null) return null;
                Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
            }
            return dest;
        } catch (IOException e) {
            return null;
        }
    }
}
