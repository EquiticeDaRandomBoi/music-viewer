// common-src/com/evand/musicplayer/hud/DragHandler.java
package com.evand.musicplayer.hud;

import com.evand.musicplayer.config.ModConfig;

public class DragHandler {
    private final ModConfig config;

    private boolean keyHeld  = false;
    private boolean dragging = false;
    private double  offsetX  = 0;
    private double  offsetY  = 0;

    // Track the HORIZONTAL CENTER so the pill/card stays centred as its width changes.
    // -1 signals "not yet initialised — use config fraction on first call".
    private double centerX = -1;
    private double topY    = -1;

    public DragHandler(ModConfig config) { this.config = config; }

    public void onKeyPress(boolean isAlt)   { keyHeld = isAlt; if (!keyHeld) endDrag(); }
    public void onKeyRelease(boolean isAlt) { if (!isAlt) { keyHeld = false; endDrag(); } }

    public void onMousePress(double mx, double my, int px, int py, int pw, int ph) {
        if (!keyHeld) return;
        if (mx >= px && mx <= px + pw && my >= py && my <= py + ph) {
            dragging = true;
            offsetX  = mx - px; // offset from left edge of pill
            offsetY  = my - py;
        }
    }

    public void onMouseRelease() { endDrag(); }

    public void onMouseMove(double mx, double my, int screenW, int screenH, int pw, int ph) {
        if (!dragging) return;
        double newLeft = mx - offsetX;
        double newTop  = my - offsetY;
        newLeft  = Math.max(0, Math.min(screenW - pw, newLeft));
        newTop   = Math.max(0, Math.min(screenH - ph, newTop));
        centerX  = newLeft + pw / 2.0;
        topY     = newTop;
    }

    public boolean isDragging() { return dragging; }
    public boolean isKeyHeld()  { return keyHeld; }

    /**
     * Returns the left edge of the pill/card.
     * Centering is based on {@code centerX}, so expanding the card keeps it centred.
     */
    public int getPillX(int screenW, int pillW) {
        if (centerX < 0) centerX = config.pillXFrac * screenW;
        return (int)(centerX - pillW / 2.0);
    }

    public int getPillY(int screenH, int pillH) {
        if (topY < 0) topY = config.pillYFrac * screenH;
        return (int) topY;
    }

    /** Sync center-X and top-Y fractions to config while dragging. */
    public void syncToConfig(int pillY, int screenW, int screenH) {
        config.pillXFrac = (float)(centerX / screenW);
        config.pillYFrac = (float)(pillY)  / screenH;
    }

    private void endDrag() {
        if (!dragging) return;
        dragging = false;
        config.save();
    }
}
