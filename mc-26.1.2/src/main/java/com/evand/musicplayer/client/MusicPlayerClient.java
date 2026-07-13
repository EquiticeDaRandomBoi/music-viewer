package com.evand.musicplayer.client;

import com.evand.musicplayer.config.ModConfig;
import com.evand.musicplayer.hud.MediaHUD;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class MusicPlayerClient implements ClientModInitializer {

    private static final Identifier HUD_ID =
        Identifier.fromNamespaceAndPath("musicplayer", "media_hud");

    @Override
    public void onInitializeClient() {
        ModConfig config = ModConfig.load();
        MediaHUD.INSTANCE.init(config);

        // HudElement.extractRenderState(GuiGraphicsExtractor, DeltaTracker) in mc-26.1.2
        HudElementRegistry.addLast(HUD_ID, (extractor, deltaTracker) ->
            MediaHUD.INSTANCE.render(extractor, 0f)
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean altHeld = GLFW.glfwGetKey(
                client.getWindow().handle(), GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS;
            MediaHUD.INSTANCE.onKeyPress(altHeld);
        });
    }
}
