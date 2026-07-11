// mc-26.1.2/src/main/java/com/evand/musicplayer/mixin/MusicTrackerMixin.java
// mc-26.1.2 uses MusicManager (Mojang name) and SoundInstance.getIdentifier()
package com.evand.musicplayer.mixin;

import com.evand.musicplayer.media.MediaPoller;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.client.resources.sounds.SoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Watches MusicManager (mc-26.1.2 equivalent of MusicTracker) so that MediaPoller
 * knows when Minecraft itself is playing music.
 *
 * Differences from mc-1.21.11:
 *   class  : MusicManager          (was MusicTracker)
 *   field  : currentMusic          (was current)
 *   id method: getIdentifier()     (was getId())
 */
@Mixin(MusicManager.class)
public abstract class MusicTrackerMixin {

    @Shadow private SoundInstance currentMusic;

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        if (currentMusic == null) {
            MediaPoller.INSTANCE.setMinecraftTrack(null);
        } else {
            String id = currentMusic.getIdentifier().toString();
            // e.g. "minecraft:music.game" → "Game"
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
