// common-src/com/evand/musicplayer/hud/MediaHUD.java  (MC 1.21.11 / Yarn API)
package com.evand.musicplayer.hud;

import com.evand.musicplayer.config.ModConfig;
import com.evand.musicplayer.media.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import java.util.Objects;

public class MediaHUD {
    public static final MediaHUD INSTANCE = new MediaHUD();

    private ModConfig     config;
    private PillAnimation anim;
    private MarqueeText   marquee;   // lazy — needs TextRenderer from client
    private DragHandler   drag;

    private long lastFrameMs = System.currentTimeMillis();

    // Timeline seek-drag state (left-click and hold on the bar)
    private boolean seekDragging = false;
    private float   seekFrac     = 0f;

    // Optimistic play/pause state for immediate icon feedback
    private Boolean optimisticPlaying = null;
    private long    optimisticExpiry  = 0L;

    private long seekClockHint = -1L;
    private long seekSentMs   = -1L;
    private static final long SEEK_COOLDOWN_MS = 5000L;

    // Local playback clock — runs independently of SMTC position update frequency
    private boolean clockRunning    = false;
    private long    clockBase       = 0L;   // positionMs at clockStartMs
    private long    clockStartMs    = 0L;   // wall-clock when clockBase was set
    private long    lastPolledPos   = -1L;
    private String  lastPolledTitle = null;

    private MediaHUD() {}

    // ── Lifecycle ────────────────────────────────────────────────────────────

    public void init(ModConfig config) {
        this.config = config;
        this.anim   = new PillAnimation();
        this.drag   = new DragHandler(config);
        MediaPoller.INSTANCE.start();
        MediaController.INSTANCE.init();
    }

    // ── Render ───────────────────────────────────────────────────────────────

    public void render(DrawContext ctx, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden) return;

        long  nowMs   = System.currentTimeMillis();
        float deltaMs = nowMs - lastFrameMs;
        lastFrameMs   = nowMs;

        anim.tick(deltaMs);
        ThumbnailManager.INSTANCE.tick();

        // Resolve display MediaInfo
        MediaInfo osInfo  = MediaPoller.INSTANCE.get();
        String    mcTrack = MediaPoller.INSTANCE.getMinecraftTrack();
        MediaInfo display;

        if (osInfo != null) {
            long   polledPos = osInfo.positionMs();
            boolean playing  = osInfo.isPlaying();
            String  curTitle = osInfo.title();

            if (!playing) {
                // Paused/stopped: trust SMTC position directly; stop clock
                clockRunning    = false;
                clockBase       = polledPos;
                clockStartMs    = nowMs;
                lastPolledPos   = polledPos;
                lastPolledTitle = curTitle;
            } else {
                // Playing: use our own monotonic clock so the timer advances every frame
                // even if the source app (browser) doesn't update SMTC positionMs continuously.
                boolean titleChanged = !Objects.equals(curTitle, lastPolledTitle);
                // Detect a real seek: position changed in the poll AND doesn't match our extrapolation
                boolean seeked = !titleChanged
                    && polledPos != lastPolledPos
                    && Math.abs(polledPos - (clockBase + (nowMs - clockStartMs))) > 1000L;

                boolean inSeekGrace = seekSentMs >= 0 && (nowMs - seekSentMs) < SEEK_COOLDOWN_MS;
                if (seekClockHint >= 0) {
                    clockBase       = seekClockHint;
                    clockStartMs    = nowMs;
                    clockRunning    = true;
                    lastPolledPos   = polledPos;
                    lastPolledTitle = curTitle;
                    seekClockHint   = -1L;
                } else if (!clockRunning || titleChanged || (seeked && !inSeekGrace)) {
                    clockBase       = polledPos;
                    clockStartMs    = nowMs;
                    clockRunning    = true;
                    lastPolledTitle = curTitle;
                }
                lastPolledPos = polledPos;
            }

            long displayPos = clockRunning ? clockBase + (nowMs - clockStartMs) : clockBase;
            long dur        = osInfo.durationMs();
            if (dur > 0) displayPos = Math.min(displayPos, dur);

            osInfo = new MediaInfo(osInfo.title(), osInfo.artist(), playing,
                    displayPos, dur, osInfo.thumbnailPath(), osInfo.sourceApp());
            display = osInfo;
        } else if (mcTrack != null) {
            display = new MediaInfo(mcTrack, null, true, 0, 0, null, "minecraft");
        } else {
            display = null;
        }

        if (display == null && anim.isCollapsed()) return;

        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();
        int pillW   = (int) anim.width();
        int pillH   = (int) anim.height();
        int pillX   = drag.getPillX(screenW, pillW);
        int pillY   = drag.getPillY(screenH, pillH);

        if (drag.isDragging()) {
            drag.syncToConfig(pillY, screenW, screenH);
        }

        if (marquee == null) {
            marquee = new MarqueeText("", pillW - 34, client.textRenderer);
        }
        marquee.tick(nowMs, deltaMs);

        // Apply optimistic play/pause state so icon responds immediately on click
        if (display != null && optimisticPlaying != null) {
            if (nowMs > optimisticExpiry) {
                optimisticPlaying = null;
            } else {
                display = new MediaInfo(display.title(), display.artist(),
                        optimisticPlaying, display.positionMs(), display.durationMs(),
                        display.thumbnailPath(), display.sourceApp());
            }
        }

        // Override display position while seek-bar is being dragged
        if (seekDragging && display != null && display.durationMs() > 0) {
            display = new MediaInfo(display.title(), display.artist(), display.isPlaying(),
                    (long)(seekFrac * display.durationMs()), display.durationMs(),
                    display.thumbnailPath(), display.sourceApp());
        }

        if (anim.isCollapsed() || anim.progress() < 0.05f) {
            PillRenderer.draw(ctx, display, marquee, pillX, pillY, pillW, pillH);
        } else {
            CardRenderer.draw(ctx, display, marquee, anim, pillX, pillY,
                    anim.contentAlpha(),
                    posMs -> MediaController.INSTANCE.seek(posMs));
        }
    }

    // ── Input ────────────────────────────────────────────────────────────────

    /** Returns true if the event was consumed (caller should not pass it on). */
    public boolean onMouseClick(double mx, double my, int button) {
        if (anim == null) return false;

        MinecraftClient client = MinecraftClient.getInstance();
        int pillW = (int) anim.width();
        int pillH = (int) anim.height();
        int pillX = drag.getPillX(client.getWindow().getScaledWidth(),  pillW);
        int pillY = drag.getPillY(client.getWindow().getScaledHeight(), pillH);

        // Right-click: toggle expand / collapse
        if (button == 1 && inBounds(mx, my, pillX, pillY, pillW, pillH)) {
            if (anim.isCollapsed() || anim.progress() < 0.5f) {
                anim.expand();
            } else {
                anim.collapse();
                CardRenderer.cardDrawn = false;
            }
            return true;
        }

        if (button == 0) {
            boolean cardVisible = !anim.isCollapsed() && CardRenderer.cardDrawn;

            // Timeline seek-drag start — tall hit zone so the thin bar is easy to grab
            if (cardVisible
                    && my >= CardRenderer.barY - 8 && my <= CardRenderer.barY + 14
                    && mx >= CardRenderer.barX      && mx <= CardRenderer.barX + CardRenderer.barWidth) {
                seekDragging = true;
                seekFrac = clamp01((float)(mx - CardRenderer.barX) / CardRenderer.barWidth);
                return true;
            }

            // Control buttons
            if (cardVisible) {
                if (inBounds(mx, my, CardRenderer.playBtnX, CardRenderer.playBtnY,
                             CardRenderer.btnW, CardRenderer.btnH)) {
                    MediaInfo cur = MediaPoller.INSTANCE.get();
                    if (cur != null) {
                        optimisticPlaying = !cur.isPlaying();
                        optimisticExpiry  = System.currentTimeMillis() + 8000L;
                    }
                    MediaController.INSTANCE.toggle(); return true;
                }
            }

            // Drag to reposition pill
            drag.onMousePress(mx, my, pillX, pillY, pillW, pillH);
        }

        return false;
    }

    public void onMouseRelease(double mx, double my, int button) {
        if (button == 0) {
            if (seekDragging) {
                seekDragging = false;
                MediaInfo info = MediaPoller.INSTANCE.get();
                if (info != null && info.durationMs() > 0) {
                    long targetMs = (long)(seekFrac * info.durationMs());
                    seekClockHint = targetMs;
                    seekSentMs    = System.currentTimeMillis();
                    MediaController.INSTANCE.seek(targetMs);
                }
            }
            if (drag != null) drag.onMouseRelease();
        }
    }

    public void onMouseMove(double mx, double my) {
        // Update seek fraction while dragging bar
        if (seekDragging && CardRenderer.cardDrawn && CardRenderer.barWidth > 0) {
            seekFrac = clamp01((float)(mx - CardRenderer.barX) / CardRenderer.barWidth);
            return; // don't move pill while seeking
        }
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

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
}
