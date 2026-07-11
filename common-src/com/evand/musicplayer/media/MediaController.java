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
