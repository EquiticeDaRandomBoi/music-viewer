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

    private String  loadedPath = null;
    private boolean registered = false;

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
            NativeImage image    = NativeImage.read(is);
            var         texture  = new NativeImageBackedTexture(() -> "musicplayer:thumb", image);
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
