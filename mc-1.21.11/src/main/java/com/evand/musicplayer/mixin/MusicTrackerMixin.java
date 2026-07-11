// mc-1.21.11/src/main/java/com/evand/musicplayer/mixin/MusicTrackerMixin.java
package com.evand.musicplayer.mixin;

import com.evand.musicplayer.media.MediaPoller;
import net.minecraft.client.sound.MusicTracker;
import net.minecraft.client.sound.SoundInstance;
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
            // Convert sound ID to a display name e.g. "minecraft:music.game" -> "Game"
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
