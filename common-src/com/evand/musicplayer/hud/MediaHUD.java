// common-src/com/evand/musicplayer/hud/MediaHUD.java
package com.evand.musicplayer.hud;

import com.evand.musicplayer.config.ModConfig;
import com.evand.musicplayer.media.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class MediaHUD {
    public static final MediaHUD INSTANCE = new MediaHUD();

    private ModConfig     config;
    private PillAnimation anim;
    private MarqueeText   marquee;   // lazy — null until first render
    private DragHandler   drag;

    private long lastFrameMs = System.currentTimeMillis();

    private MediaHUD() {}

    // ── Lifecycle ────────────────────────────────────────────────────────────

    public void init(ModConfig config) {
        this.config = config;
        this.anim   = new PillAnimation();
        this.drag   = new DragHandler(config);
        // marquee is lazy-initialised on first render (needs client.textRenderer)
        MediaPoller.INSTANCE.start();
        MediaController.INSTANCE.init();
    }

    // ── Render ───────────────────────────────────────────────────────────────

    public void render(DrawContext ctx, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden) return;

        // Time delta
        long  nowMs   = System.currentTimeMillis();
        float deltaMs = nowMs - lastFrameMs;
        lastFrameMs   = nowMs;

        anim.tick(deltaMs);
        ThumbnailManager.INSTANCE.tick();

        // Determine what to show
        MediaInfo osInfo  = MediaPoller.INSTANCE.get();
        String    mcTrack = MediaPoller.INSTANCE.getMinecraftTrack();
        MediaInfo display;

        if (osInfo != null) {
            display = osInfo;
        } else if (mcTrack != null) {
            display = new MediaInfo(mcTrack, null, true, 0, 0, null, "minecraft");
        } else {
            display = null;
        }

        // Nothing playing and animation is fully gone — nothing to render
        if (display == null && anim.isCollapsed()) return;

        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();

        int pillW = (int) anim.width();
        int pillH = (int) anim.height();

        int pillX = drag.getPillX(screenW, pillW);
        int pillY = drag.getPillY(screenH, pillH);

        // Persist pill position to config only while actively dragging
        if (drag.isDragging()) {
            drag.syncToConfig(pillX, pillY, screenW, screenH);
        }

        // Lazy marquee init — once we have a TextRenderer available
        if (marquee == null) {
            marquee = new MarqueeText("", pillW - 38, client.textRenderer);
        }
        marquee.tick(nowMs, deltaMs);

        if (anim.isCollapsed() || anim.progress() < 0.05f) {
            PillRenderer.draw(ctx, display, marquee, pillX, pillY, pillW, pillH);
        } else {
            CardRenderer.draw(ctx, display, marquee, anim, pillX, pillY,
                    anim.contentAlpha(),
                    posMs -> MediaController.INSTANCE.seek(posMs));
        }
    }

    // ── Input ────────────────────────────────────────────────────────────────

    /**
     * Returns true if the event was consumed (caller should not pass it on).
     */
    public boolean onMouseClick(double mx, double my, int button) {
        if (anim == null) return false; // not yet initialised

        MinecraftClient client = MinecraftClient.getInstance();
        int pillW = (int) anim.width();
        int pillH = (int) anim.height();
        int pillX = drag.getPillX(client.getWindow().getScaledWidth(),  pillW);
        int pillY = drag.getPillY(client.getWindow().getScaledHeight(), pillH);

        // Right-click: toggle expand / collapse when cursor is over the pill
        if (button == 1) {
            if (inBounds(mx, my, pillX, pillY, pillW, pillH)) {
                if (anim.isCollapsed() || anim.progress() < 0.5f) {
                    anim.expand();
                } else {
                    anim.collapse();
                    CardRenderer.cardDrawn = false;
                }
                return true;
            }
        }

        // Left-click on expanded card: hit-test control buttons and timeline
        if (button == 0 && !anim.isCollapsed() && CardRenderer.cardDrawn) {
            if (inBounds(mx, my, CardRenderer.prevBtnX, CardRenderer.prevBtnY,
                         CardRenderer.btnW, CardRenderer.btnH)) {
                MediaController.INSTANCE.previous();
                return true;
            }
            if (inBounds(mx, my, CardRenderer.playBtnX, CardRenderer.playBtnY,
                         CardRenderer.btnW, CardRenderer.btnH)) {
                MediaController.INSTANCE.toggle();
                return true;
            }
            if (inBounds(mx, my, CardRenderer.nextBtnX, CardRenderer.nextBtnY,
                         CardRenderer.btnW, CardRenderer.btnH)) {
                MediaController.INSTANCE.next();
                return true;
            }

            // Timeline seek
            if (my >= CardRenderer.barY && my <= CardRenderer.barY + 8
                    && mx >= CardRenderer.barX
                    && mx <= CardRenderer.barX + CardRenderer.barWidth) {
                MediaInfo info = MediaPoller.INSTANCE.get();
                if (info != null && info.durationMs() > 0) {
                    float frac = (float)(mx - CardRenderer.barX) / CardRenderer.barWidth;
                    MediaController.INSTANCE.seek((long)(frac * info.durationMs()));
                    return true;
                }
            }
        }

        // Left-click: pass to drag handler (may start a drag)
        if (button == 0) {
            drag.onMousePress(mx, my, pillX, pillY, pillW, pillH);
        }

        return false;
    }

    public void onMouseRelease(double mx, double my, int button) {
        if (button == 0 && drag != null) drag.onMouseRelease();
    }

    public void onMouseMove(double mx, double my) {
        if (drag == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        drag.onMouseMove(mx, my,
                client.getWindow().getScaledWidth(),
                client.getWindow().getScaledHeight(),
                (int) anim.width(),
                (int) anim.height());
    }

    public void onKeyPress(boolean isAlt)   { if (drag != null) drag.onKeyPress(isAlt); }
    public void onKeyRelease(boolean isAlt) { if (drag != null) drag.onKeyRelease(isAlt); }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static boolean inBounds(double mx, double my, int bx, int by, int bw, int bh) {
        return mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
    }
}
