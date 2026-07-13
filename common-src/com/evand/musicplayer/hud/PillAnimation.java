// common-src/com/evand/musicplayer/hud/PillAnimation.java
package com.evand.musicplayer.hud;

public class PillAnimation {
    public static final float PILL_W       = 155f;
    public static final float PILL_H       =  26f;
    public static final float CARD_W       = 155f;
    public static final float CARD_H       =  96f;
    private static final float EXPAND_MS   = 250f;
    private static final float COLLAPSE_MS = 200f;

    private float   progress  = 0f;   // 0 = collapsed, 1 = expanded
    private boolean expanding = false;

    public void expand()   { expanding = true; }
    public void collapse() { expanding = false; }

    public void tick(float deltaMs) {
        if (expanding) {
            progress = Math.min(1f, progress + deltaMs / EXPAND_MS);
        } else {
            progress = Math.max(0f, progress - deltaMs / COLLAPSE_MS);
        }
    }

    public float progress()      { return progress; }
    public boolean isExpanded()  { return progress >= 1f; }
    public boolean isCollapsed() { return progress <= 0f; }

    public float width()  { return lerp(PILL_W, CARD_W, easeOut(progress)); }
    public float height() { return lerp(PILL_H, CARD_H, easeOut(progress)); }

    /** Content fades in during last 40% of expand; fades out immediately on collapse. */
    public float contentAlpha() {
        if (!expanding && progress < 1f) return Math.max(0f, progress * 2.5f - 1.5f);
        float t = (progress - 0.6f) / 0.4f;
        return Math.max(0f, Math.min(1f, t));
    }

    private static float easeOut(float t) { return 1f - (1f - t) * (1f - t) * (1f - t); }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}
