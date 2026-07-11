// common-src/com/evand/musicplayer/hud/DragHandler.java
package com.evand.musicplayer.hud;

import com.evand.musicplayer.config.ModConfig;

public class DragHandler {
    private final ModConfig config;

    private boolean keyHeld    = false;
    private boolean dragging   = false;
    private double  offsetX    = 0;
    private double  offsetY    = 0;
    private double  currentX   = -1; // -1 = use frac from config
    private double  currentY   = -1;

    public DragHandler(ModConfig config) {
        this.config = config;
    }

    public void onKeyPress(boolean isAlt)   { keyHeld = isAlt; if (!keyHeld) endDrag(); }
    public void onKeyRelease(boolean isAlt) { if (!isAlt) { keyHeld = false; endDrag(); } }

    public void onMousePress(double mx, double my, int px, int py, int pw, int ph) {
        if (!keyHeld) return;
        if (mx >= px && mx <= px + pw && my >= py && my <= py + ph) {
            dragging = true;
            offsetX  = mx - px;
            offsetY  = my - py;
        }
    }

    public void onMouseRelease() { endDrag(); }

    public void onMouseMove(double mx, double my, int screenW, int screenH, int pw, int ph) {
        if (!dragging) return;
        double newX = mx - offsetX;
        double newY = my - offsetY;
        newX = Math.max(0, Math.min(screenW - pw, newX));
        newY = Math.max(0, Math.min(screenH - ph, newY));
        currentX = newX;
        currentY = newY;
    }

    public boolean isDragging() { return dragging; }
    public boolean isKeyHeld()  { return keyHeld; }

    public int getPillX(int screenW, int pillW) {
        if (currentX < 0) currentX = config.pillXFrac * screenW - pillW / 2.0;
        return (int) currentX;
    }

    public int getPillY(int screenH, int pillH) {
        if (currentY < 0) currentY = config.pillYFrac * screenH;
        return (int) currentY;
    }

    private void endDrag() {
        if (!dragging) return;
        dragging = false;
        // Save to config as fractions so it's resolution-independent
        // (screenW/H not available here; saved lazily in MediaHUD on next frame)
        config.save();
    }

    /** Call from MediaHUD after computing pillX/pillY to update config fracs. */
    public void syncToConfig(int pillX, int pillY, int screenW, int screenH) {
        config.pillXFrac = (float)(pillX + 0f) / screenW;
        config.pillYFrac = (float)(pillY + 0f) / screenH;
    }
}
