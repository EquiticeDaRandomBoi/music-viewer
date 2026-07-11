// mc-1.21.11/src/main/java/com/evand/musicplayer/client/MusicPlayerClient.java
package com.evand.musicplayer.client;

import com.evand.musicplayer.config.ModConfig;
import com.evand.musicplayer.hud.MediaHUD;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class MusicPlayerClient implements ClientModInitializer {

    private static final Identifier HUD_ID = Identifier.of("musicplayer", "media_hud");

    @Override
    public void onInitializeClient() {
        ModConfig config = ModConfig.load();
        MediaHUD.INSTANCE.init(config);

        // Register HUD element via the non-deprecated HudElementRegistry API.
        // HudElement.render(DrawContext, RenderTickCounter) — tickCounter.getTickProgress(false)
        // gives the float partial tick that MediaHUD.render expects.
        HudElementRegistry.addLast(HUD_ID, (context, tickCounter) ->
            MediaHUD.INSTANCE.render(context, tickCounter.getTickProgress(false))
        );

        // Poll Alt key state each tick via GLFW (avoids replacing key callbacks)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean altHeld = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_ALT)
                              == GLFW.GLFW_PRESS;
            MediaHUD.INSTANCE.onKeyPress(altHeld);
        });
    }
}
