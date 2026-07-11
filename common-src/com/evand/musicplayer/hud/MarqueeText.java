// common-src/com/evand/musicplayer/hud/MarqueeText.java
package com.evand.musicplayer.hud;

import net.minecraft.client.font.TextRenderer;

public class MarqueeText {
    private static final float SCROLL_PX_PER_MS = 0.03f; // 30px/sec
    private static final long  PAUSE_MS         = 500L;

    private String text;
    private int    boxWidth;
    private final TextRenderer renderer;

    private int   textWidth   = 0;
    private float offset      = 0f;
    private boolean pausing   = false;
    private long   pauseStart = 0L;
    private boolean forward   = true;

    public MarqueeText(String text, int boxWidth, TextRenderer renderer) {
        this.renderer = renderer;
        this.boxWidth = boxWidth;
        update(text);
    }

    public void update(String newText) {
        if (newText == null) newText = "";
        if (!newText.equals(this.text)) {
            this.text   = newText;
            textWidth   = renderer.getWidth(newText);
            offset      = 0f;
            forward     = true;
            pausing     = false;
        }
    }

    public void setBoxWidth(int width) { this.boxWidth = width; }

    public void tick(long nowMs, float deltaMs) {
        if (textWidth <= boxWidth) { offset = 0; return; }

        float overflow = textWidth - boxWidth;

        if (pausing) {
            if (nowMs - pauseStart >= PAUSE_MS) {
                pausing = false;
                forward = !forward;
            }
            return;
        }

        if (forward) {
            offset += SCROLL_PX_PER_MS * deltaMs;
            if (offset >= overflow) {
                offset = overflow;
                pausing = true;
                pauseStart = nowMs;
            }
        } else {
            offset -= SCROLL_PX_PER_MS * deltaMs;
            if (offset <= 0f) {
                offset = 0f;
                pausing = true;
                pauseStart = nowMs;
            }
        }
    }

    /** Pixel offset to shift text left when rendering. Always >= 0. */
    public int drawOffset() { return (int) offset; }

    public String text()     { return text; }
    public int textWidth()   { return textWidth; }
    public boolean scrolls() { return textWidth > boxWidth; }
}
