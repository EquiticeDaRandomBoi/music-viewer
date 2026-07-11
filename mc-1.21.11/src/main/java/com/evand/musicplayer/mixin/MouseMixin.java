// mc-1.21.11/src/main/java/com/evand/musicplayer/mixin/MouseMixin.java
package com.evand.musicplayer.mixin;

import com.evand.musicplayer.hud.MediaHUD;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts raw mouse events in Minecraft's Mouse class and forwards them to
 * MediaHUD. This avoids replacing GLFW callbacks directly (which would remove
 * Minecraft's own handlers) while still giving the HUD first-look at clicks and
 * cursor moves.
 */
@Mixin(Mouse.class)
public class MouseMixin {

    /**
     * Inject at the head of the mouse-button handler.
     * Signature: onMouseButton(long window, MouseInput mouseInput, int action)
     * where action is GLFW_PRESS / GLFW_RELEASE / GLFW_REPEAT.
     */
    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void hudOnMouseButton(long window, MouseInput mouseInput, int action, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;

        double scale = mc.getWindow().getScaleFactor();
        double[] mx = new double[1], my = new double[1];
        GLFW.glfwGetCursorPos(window, mx, my);
        double gx = mx[0] / scale;
        double gy = my[0] / scale;
        int button = mouseInput.button();

        if (action == GLFW.GLFW_PRESS) {
            if (MediaHUD.INSTANCE.onMouseClick(gx, gy, button)) {
                ci.cancel(); // HUD consumed the click — don't let Minecraft also handle it
            }
        } else if (action == GLFW.GLFW_RELEASE) {
            MediaHUD.INSTANCE.onMouseRelease(gx, gy, button);
        }
    }

    /**
     * Inject at the head of the cursor-position handler.
     * Signature: onCursorPos(long window, double x, double y)
     */
    @Inject(method = "onCursorPos", at = @At("HEAD"))
    private void hudOnCursorPos(long window, double x, double y, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;

        double scale = mc.getWindow().getScaleFactor();
        MediaHUD.INSTANCE.onMouseMove(x / scale, y / scale);
    }
}
