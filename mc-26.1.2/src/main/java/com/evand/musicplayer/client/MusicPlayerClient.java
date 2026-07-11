// mc-26.1.2/src/main/java/com/evand/musicplayer/client/MusicPlayerClient.java
// mc-26.1.2 uses Mojang-mapped names; APIs differ from 1.21.11 Yarn mappings.
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

/**
 * Client entrypoint for mc-26.1.2.
 *
 * NOTE: In mc-26.1.2, HudRenderCallback is replaced by HudElementRegistry/HudElement.
 * The HudElement callback receives (GuiGraphicsExtractor, DeltaTracker) — not (DrawContext, float).
 * MediaHUD.render currently takes (DrawContext, float) which is the mc-1.21.11 API.
 * The common-src MediaHUD must be updated for mc-26.1.2 to render correctly;
 * until then the HUD element is registered but renders nothing.
 */
@Environment(EnvType.CLIENT)
public class MusicPlayerClient implements ClientModInitializer {

    private static final Identifier HUD_ID = Identifier.fromNamespaceAndPath("musicplayer", "media_hud");

    @Override
    public void onInitializeClient() {
        ModConfig config = ModConfig.load();
        MediaHUD.INSTANCE.init(config);

        // Register a HUD element — mc-26.1.2 HUD API (HudElementRegistry).
        // When common-src MediaHUD is ported to mc-26.1.2, replace the no-op with:
        //   MediaHUD.INSTANCE.render(graphics, deltaTracker.getGameTimeDeltaPartialTick(false));
        HudElementRegistry.addLast(HUD_ID, (graphics, deltaTracker) -> {
            // TODO: port MediaHUD.render to GuiGraphicsExtractor for mc-26.1.2
        });

        // Poll Alt key state each tick via GLFW (same pattern as mc-1.21.11)
        // Note: in mc-26.1.2 the client class is Minecraft and Window.handle() not getHandle()
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean altHeld = GLFW.glfwGetKey(client.getWindow().handle(), GLFW.GLFW_KEY_LEFT_ALT)
                              == GLFW.GLFW_PRESS;
            MediaHUD.INSTANCE.onKeyPress(altHeld);
        });
    }
}
