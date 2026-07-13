// common-src/com/evand/musicplayer/media/ThumbnailManager.java  (MC 1.21.11 / Yarn API)
package com.evand.musicplayer.media;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.nio.file.*;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicReference;

public class ThumbnailManager {
    public static final ThumbnailManager INSTANCE = new ThumbnailManager();

    // Two texture slots — we ping-pong between them so old texture stays alive during fade.
    private static final Identifier ID_A = Identifier.of("musicplayer", "thumb_a");
    private static final Identifier ID_B = Identifier.of("musicplayer", "thumb_b");
    private static final Path DEBUG_LOG  = Path.of(
        System.getProperty("java.io.tmpdir"), "musicplayer", "thumb_java_debug.log");

    public static final float FADE_DURATION_MS = 500f;

    private boolean currIsA   = true;
    private boolean aReg      = false;
    private boolean bReg      = false;
    private NativeImageBackedTexture texA = null;
    private NativeImageBackedTexture texB = null;

    private float fadeProgress = 1f;  // 0 = fully prev, 1 = fully current
    private long  lastTickMs   = System.currentTimeMillis();

    private final AtomicReference<String> pendingPath = new AtomicReference<>(null);

    private ThumbnailManager() {}

    // ── Called every frame on the render thread ───────────────────────────────

    public void tick() {
        long  now   = System.currentTimeMillis();
        float delta = (float)(now - lastTickMs);
        lastTickMs  = now;

        // Advance crossfade
        if (fadeProgress < 1f) {
            fadeProgress = Math.min(1f, fadeProgress + delta / FADE_DURATION_MS);
            if (fadeProgress >= 1f) destroyPrevSlot();
        }

        String path = pendingPath.getAndSet(null);
        if (path == null) return;
        // Empty path means song changed but new thumbnail not fetched yet.
        // Keep showing current texture — it will crossfade when the real path arrives.
        if (path.isEmpty()) return;

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

            // Load new image into the OPPOSITE slot (keeps current alive for crossfade).
            boolean nextIsA = !currIsA;
            Identifier nextId = nextIsA ? ID_A : ID_B;

            // Destroy whatever is already in that slot (old prev from last transition).
            if (nextIsA ? aReg : bReg) {
                MinecraftClient.getInstance().getTextureManager().destroyTexture(nextId);
                if (nextIsA) { aReg = false; texA = null; }
                else         { bReg = false; texB = null; }
            }

            // Register new texture.
            var newTex = new NativeImageBackedTexture(
                () -> "musicplayer:thumb_" + (nextIsA ? "a" : "b"), image);
            MinecraftClient.getInstance().getTextureManager().registerTexture(nextId, newTex);
            if (nextIsA) { texA = newTex; aReg = true; }
            else         { texB = newTex; bReg = true; }
            log("registered OK " + w + "x" + h + " slot=" + (nextIsA ? "A" : "B"));

            // Does the current slot (about to become prev) have a texture?
            // Check BEFORE the swap so we read the old currIsA value.
            boolean hasPrev = currIsA ? aReg : bReg;

            // Swap: next slot is now current.
            currIsA = nextIsA;

            // Start crossfade if there is something to fade from.
            fadeProgress = hasPrev ? 0f : 1f;

        } catch (Exception e) {
            log("FAILED: " + e);
            System.err.println("[MusicPlayer] Thumbnail load failed: " + e);
            pendingPath.compareAndSet(null, path);  // retry next frame
        }
    }

    // ── Called from the poll thread ───────────────────────────────────────────

    public void queuePath(String path) {
        pendingPath.set(path == null ? "" : path);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** Identifier of the current (new) texture. Null if nothing in current slot. */
    public Identifier getTexture() {
        boolean reg = currIsA ? aReg : bReg;
        return reg ? (currIsA ? ID_A : ID_B) : null;
    }

    /** Identifier of the previous texture during a crossfade. Null if not fading. */
    public Identifier getPrevTexture() {
        if (fadeProgress >= 1f) return null;
        boolean prevIsA = !currIsA;
        boolean reg = prevIsA ? aReg : bReg;
        return reg ? (prevIsA ? ID_A : ID_B) : null;
    }

    /** True when either slot has a registered texture (use to decide whether to draw). */
    public boolean hasAnyTexture() { return aReg || bReg; }

    /** Progress 0→1: 0 = fully showing prev, 1 = fully showing current. */
    public float getFadeProgress() { return fadeProgress; }

    /** Current texture object (for logging / GPU-view checks). */
    public NativeImageBackedTexture getTextureObj() {
        if (currIsA) return aReg ? texA : null;
        return bReg ? texB : null;
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void destroyPrevSlot() {
        boolean prevIsA = !currIsA;
        Identifier prevId = prevIsA ? ID_A : ID_B;
        if (prevIsA ? aReg : bReg) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(prevId);
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
