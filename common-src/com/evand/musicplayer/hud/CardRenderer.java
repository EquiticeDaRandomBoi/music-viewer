// common-src/com/evand/musicplayer/hud/CardRenderer.java
package com.evand.musicplayer.hud;

import com.evand.musicplayer.media.MediaInfo;
import com.evand.musicplayer.media.ThumbnailManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

public class CardRenderer {

    @FunctionalInterface
    public interface SeekCallback {
        void seek(long positionMs);
    }

    private static final int   BG_COLOR   = 0xFF000000;
    private static final float CORNER_R   = 14f;
    private static final int   THUMB_SIZE = 60;
    private static final float THUMB_R    = 10f;
    private static final int   PADDING    = 10;
    private static final int   BUTTON_W   = 28;
    private static final int   BUTTON_H   = 20;
    private static final int   BAR_H      = 4;

    /** Hit-test bounds, updated each frame before buttons are drawn. */
    public static int prevBtnX, prevBtnY, playBtnX, playBtnY, nextBtnX, nextBtnY;
    public static int btnW = BUTTON_W, btnH = BUTTON_H;
    public static int barX, barY, barWidth;

    public static void draw(DrawContext ctx, MediaInfo info, MarqueeText marquee,
                            PillAnimation anim, int x, int y, float alpha,
                            SeekCallback onSeek) {
        int w = (int) anim.width();
        int h = (int) anim.height();

        // Background card
        RoundedRectRenderer.fill(ctx, x, y, w, h, CORNER_R, BG_COLOR);

        if (alpha <= 0.01f) return;

        int a       = (int)(alpha * 255) << 24;
        int textCol = 0x00FFFFFF | a;
        int dimCol  = 0x00888888 | a;
        int barBg   = 0x00333333 | a;
        int barFg   = 0x00FFFFFF | a;

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int cx   = x + w / 2;
        int curY = y + PADDING;

        // ── Thumbnail ────────────────────────────────────────────────────────
        int thumbX = cx - THUMB_SIZE / 2;
        Identifier thumb = ThumbnailManager.INSTANCE.getTexture();
        if (thumb != null) {
            RoundedRectRenderer.drawTextureSquircle(ctx, thumb, thumbX, curY, THUMB_SIZE, THUMB_R, BG_COLOR);
        } else {
            RoundedRectRenderer.fillSquircle(ctx, thumbX, curY, THUMB_SIZE, THUMB_R, 0xFF333333);
        }
        curY += THUMB_SIZE + PADDING;

        // ── Title (marquee) ──────────────────────────────────────────────────
        String title  = coalesce(info != null ? info.title()  : null, "Unknown");
        String artist = coalesce(info != null ? info.artist() : null, "");

        int textAreaW = w - PADDING * 2;
        int textLeft  = x + PADDING;

        marquee.update(title);
        marquee.setBoxWidth(textAreaW);
        ctx.enableScissor(textLeft, curY, textLeft + textAreaW, curY + 10);
        ctx.drawText(tr, title, textLeft - marquee.drawOffset(), curY, textCol, false);
        ctx.disableScissor();
        curY += 10;

        // ── Artist ───────────────────────────────────────────────────────────
        if (!artist.isEmpty()) {
            ctx.drawText(tr, artist, textLeft, curY, dimCol, false);
            curY += 10;
        }
        curY += 4;

        // ── Timeline bar ─────────────────────────────────────────────────────
        if (info != null && info.durationMs() > 0) {
            barX     = textLeft;
            barY     = curY;
            barWidth = w - PADDING * 2;

            // Background track
            RoundedRectRenderer.fill(ctx, barX, barY, barWidth, BAR_H, BAR_H / 2f, barBg);

            // Progress fill
            int progW = (int)(barWidth * info.progress());
            if (progW > 0) {
                RoundedRectRenderer.fill(ctx, barX, barY, progW, BAR_H, BAR_H / 2f, barFg);
            }

            curY += BAR_H + 3;

            // Elapsed / remaining labels
            String elapsed   = info.elapsedFormatted();
            String remaining = info.remainingFormatted();
            ctx.drawText(tr, elapsed, barX, curY, dimCol, false);
            ctx.drawText(tr, remaining, barX + barWidth - tr.getWidth(remaining), curY, dimCol, false);
            curY += 10 + PADDING;
        } else {
            curY += BAR_H + 3 + 10 + PADDING;
        }

        // ── Buttons row: [⏮] [⏯/⏸] [⏭] ────────────────────────────────────
        int totalBtnW = BUTTON_W * 3 + PADDING * 2;
        int btnStartX = cx - totalBtnW / 2;

        prevBtnX = btnStartX;
        prevBtnY = curY;
        playBtnX = btnStartX + BUTTON_W + PADDING;
        playBtnY = curY;
        nextBtnX = btnStartX + (BUTTON_W + PADDING) * 2;
        nextBtnY = curY;

        drawButton(ctx, tr, prevBtnX, curY, BUTTON_W, BUTTON_H, "<<",  textCol);
        String playLabel = (info != null && info.isPlaying()) ? "||" : "|>";
        drawButton(ctx, tr, playBtnX, curY, BUTTON_W, BUTTON_H, playLabel, textCol);
        drawButton(ctx, tr, nextBtnX, curY, BUTTON_W, BUTTON_H, ">>",  textCol);
    }

    private static void drawButton(DrawContext ctx, TextRenderer tr,
                                   int x, int y, int w, int h,
                                   String label, int color) {
        RoundedRectRenderer.fill(ctx, x, y, w, h, 4f, 0xFF1A1A1A);
        int lw = tr.getWidth(label);
        int lh = 8;
        ctx.drawText(tr, label, x + (w - lw) / 2, y + (h - lh) / 2, color, false);
    }

    private static String coalesce(String s, String fallback) {
        return (s != null && !s.isEmpty()) ? s : fallback;
    }
}
