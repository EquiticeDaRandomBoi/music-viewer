// mc-26.1.2/src/main/java/com/evand/musicplayer/hud/MarqueeText.java
// Mojang API: Font (was TextRenderer in Yarn/1.21.11)
package com.evand.musicplayer.hud;

import net.minecraft.client.gui.Font;

public class MarqueeText {
    private static final float SCROLL_PX_PER_MS = 0.03f; // 30 px/sec
    private static final long  PAUSE_MS         = 3000L;
    public  static final int   GAP_PX           = 20;

    private String text;
    private int    boxWidth;
    private final Font font;

    private int     textWidth = 0;
    private float   offset    = 0f;
    private boolean pausing   = true;  // start paused so the title is readable
    private long    pauseStart = 0L;   // 0 = initialise to nowMs on first tick

    public MarqueeText(String text, int boxWidth, Font font) {
        this.font     = font;
        this.boxWidth = boxWidth;
        update(text);
    }

    public void update(String newText) {
        if (newText == null) newText = "";
        if (!newText.equals(this.text)) {
            this.text  = newText;
            textWidth  = font.width(newText);
            offset     = 0f;
            pausing    = true;
            pauseStart = 0L;
        }
    }

    public void setBoxWidth(int width) { this.boxWidth = width; }

    public void tick(long nowMs, float deltaMs) {
        if (textWidth <= boxWidth) { offset = 0; return; }
        float overflow = textWidth - boxWidth;

        if (pausing) {
            if (pauseStart == 0L) pauseStart = nowMs;  // latch on first tick
            if (nowMs - pauseStart >= PAUSE_MS) pausing = false;
            return;
        }

        offset += SCROLL_PX_PER_MS * deltaMs;
        if (offset >= textWidth + GAP_PX) {
            offset     = 0f;
            pausing    = true;
            pauseStart = 0L;
        }
    }

    public int  drawOffset() { return (int) offset; }
    public String text()     { return text; }
    public int  textWidth()  { return textWidth; }
    public boolean scrolls() { return textWidth > boxWidth; }
}
