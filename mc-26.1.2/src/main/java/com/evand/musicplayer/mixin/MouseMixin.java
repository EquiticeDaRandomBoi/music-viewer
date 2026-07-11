// mc-26.1.2/src/main/java/com/evand/musicplayer/mixin/MouseMixin.java
// mc-26.1.2 uses MouseHandler (Mojang name), onButton/onMove methods, MouseButtonInfo type.
package com.evand.musicplayer.mixin;

import com.evand.musicplayer.hud.MediaHUD;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts raw mouse events from MouseHandler (mc-26.1.2 name for Mouse) and
 * forwards them to MediaHUD for drag and control-click handling.
 *
 * Differences from mc-1.21.11:
 *   class          : MouseHandler             (was Mouse)
 *   button method  : onButton                 (was onMouseButton)
 *   cursor method  : onMove                   (was onCursorPos)
 *   button type    : MouseButtonInfo          (was MouseInput)
 *   scale accessor : getGuiScale() → double  (was getScaleFactor())
 *   window handle  : window.handle()         (was getHandle())
 */
@Mixin(MouseHandler.class)
public class MouseMixin {

    /**
     * Inject at HEAD of mouse button handler.
     * Signature: onButton(long window, MouseButtonInfo mouseButtonInfo, int action)
     */
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void hudOnButton(long window, MouseButtonInfo mouseButtonInfo, int action, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        double scale = mc.getWindow().getGuiScale();
        double[] mx = new double[1], my = new double[1];
        GLFW.glfwGetCursorPos(window, mx, my);
        double gx = mx[0] / scale;
        double gy = my[0] / scale;
        int button = mouseButtonInfo.button();

        if (action == GLFW.GLFW_PRESS) {
            if (MediaHUD.INSTANCE.onMouseClick(gx, gy, button)) {
                ci.cancel(); // HUD consumed the click — prevent Minecraft handling
            }
        } else if (action == GLFW.GLFW_RELEASE) {
            MediaHUD.INSTANCE.onMouseRelease(gx, gy, button);
        }
    }

    /**
     * Inject at HEAD of cursor position handler.
     * Signature: onMove(long window, double x, double y)
     */
    @Inject(method = "onMove", at = @At("HEAD"))
    private void hudOnMove(long window, double x, double y, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        double scale = mc.getWindow().getGuiScale();
        MediaHUD.INSTANCE.onMouseMove(x / scale, y / scale);
    }
}
