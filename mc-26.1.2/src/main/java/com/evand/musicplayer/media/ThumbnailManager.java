package com.evand.musicplayer.media;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.nio.file.*;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicReference;

public class ThumbnailManager {
    public static final ThumbnailManager INSTANCE = new ThumbnailManager();

    // Two texture slots — ping-pong so old texture stays alive during crossfade.
    private static final Identifier ID_A = Identifier.fromNamespaceAndPath("musicplayer", "thumb_a");
    private static final Identifier ID_B = Identifier.fromNamespaceAndPath("musicplayer", "thumb_b");
    private static final Path DEBUG_LOG  = Path.of(
        System.getProperty("java.io.tmpdir"), "musicplayer", "thumb_java_debug.log");

    public static final float FADE_DURATION_MS = 500f;

    private boolean currIsA = true;
    private boolean aReg    = false;
    private boolean bReg    = false;
    private DynamicTexture texA = null;
    private DynamicTexture texB = null;

    private float fadeProgress = 1f;
    private long  lastTickMs   = System.currentTimeMillis();

    private final AtomicReference<String> pendingPath = new AtomicReference<>(null);

    private ThumbnailManager() {}

    public void tick() {
        long  now   = System.currentTimeMillis();
        float delta = (float)(now - lastTickMs);
        lastTickMs  = now;

        if (fadeProgress < 1f) {
            fadeProgress = Math.min(1f, fadeProgress + delta / FADE_DURATION_MS);
            if (fadeProgress >= 1f) destroyPrevSlot();
        }

        String path = pendingPath.getAndSet(null);
        if (path == null) return;
        if (path.isEmpty()) return; // keep showing current; new path arrives when ready

        Path p = Path.of(path);
        if (!Files.exists(p)) { pendingPath.compareAndSet(null, path); return; }

        byte[] bytes = null;
        try {
            bytes = Files.readAllBytes(p);
            log("reading " + bytes.length + " bytes from " + path);
            NativeImage image = NativeImage.read(bytes);
            int w = image.getWidth(), h = image.getHeight();
            log("decoded " + w + "x" + h);
            if (w <= 0 || h <= 0) { image.close(); log("bad dims"); return; }

            boolean nextIsA = !currIsA;
            Identifier nextId = nextIsA ? ID_A : ID_B;

            if (nextIsA ? aReg : bReg) {
                Minecraft.getInstance().getTextureManager().release(nextId);
                if (nextIsA) { aReg = false; texA = null; }
                else         { bReg = false; texB = null; }
            }

            DynamicTexture newTex = new DynamicTexture(
                () -> "musicplayer:thumb_" + (nextIsA ? "a" : "b"), image);
            Minecraft.getInstance().getTextureManager().register(nextId, newTex);
            if (nextIsA) { texA = newTex; aReg = true; }
            else         { texB = newTex; bReg = true; }
            log("registered OK " + w + "x" + h + " slot=" + (nextIsA ? "A" : "B"));

            boolean hasPrev = currIsA ? aReg : bReg;
            currIsA = nextIsA;
            fadeProgress = hasPrev ? 0f : 1f;

        } catch (Exception e) {
            log("FAILED: " + e);
            System.err.println("[MusicPlayer] Thumbnail load failed: " + e);
            pendingPath.compareAndSet(null, path);  // retry next frame
        }
    }

    public void queuePath(String path) {
        pendingPath.set(path == null ? "" : path);
    }

    public Identifier getTexture() {
        boolean reg = currIsA ? aReg : bReg;
        return reg ? (currIsA ? ID_A : ID_B) : null;
    }

    public Identifier getPrevTexture() {
        if (fadeProgress >= 1f) return null;
        boolean prevIsA = !currIsA;
        boolean reg = prevIsA ? aReg : bReg;
        return reg ? (prevIsA ? ID_A : ID_B) : null;
    }

    public boolean hasAnyTexture() { return aReg || bReg; }

    public float getFadeProgress() { return fadeProgress; }

    public DynamicTexture getTextureObj() {
        if (currIsA) return aReg ? texA : null;
        return bReg ? texB : null;
    }

    public DynamicTexture getPrevTextureObj() {
        if (fadeProgress >= 1f) return null;
        boolean prevIsA = !currIsA;
        if (prevIsA) return aReg ? texA : null;
        return bReg ? texB : null;
    }

    private void destroyPrevSlot() {
        boolean prevIsA = !currIsA;
        Identifier prevId = prevIsA ? ID_A : ID_B;
        if (prevIsA ? aReg : bReg) {
            Minecraft.getInstance().getTextureManager().release(prevId);
            if (prevIsA) { aReg = false; texA = null; }
            else         { bReg = false; texB = null; }
        }
    }

    private static void log(String msg) {
        try {
            String line = java.time.LocalTime.now() + " [thumb] " + msg + "\n";
            Files.writeString(DEBUG_LOG, line,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {}
    }
}
