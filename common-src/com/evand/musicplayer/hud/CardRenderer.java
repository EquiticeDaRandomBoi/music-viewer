// common-src/com/evand/musicplayer/hud/CardRenderer.java  (MC 1.21.11 / Yarn API)
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
    private static final float CORNER_R   = 10f;
    private static final int   THUMB_SIZE = 40;
    private static final float THUMB_R    = 7f;
    private static final int   PAD        = 6;   // outer padding
    private static final int   THUMB_GAP  = 6;   // gap between thumb and text column
    private static final int   BUTTON_W   = 24;
    private static final int   BUTTON_H   = 18;
    private static final int   BAR_H      = 3;

    public static int playBtnX, playBtnY;
    public static int prevBtnX, prevBtnY;
    public static int nextBtnX, nextBtnY;
    public static int btnW = BUTTON_W, btnH = BUTTON_H;
    public static int barX, barY, barWidth;
    public static boolean cardDrawn = false;

    public static void draw(DrawContext ctx, MediaInfo info, MarqueeText marquee,
                            PillAnimation anim, int x, int y, float alpha,
                            SeekCallback onSeek) {
        cardDrawn = true;
        int w = (int) anim.width();
        int h = (int) anim.height();

        RoundedRectRenderer.fill(ctx, x, y, w, h, CORNER_R, BG_COLOR);

        if (alpha <= 0.01f) return;

        int a       = (int)(alpha * 255) << 24;
        int textCol = 0x00FFFFFF | a;
        int dimCol  = 0x00888888 | a;
        int barBg   = 0x00333333 | a;
        int barFg   = 0x00FFFFFF | a;

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        // ── Row 1: thumbnail (left) + title/artist (right) ────────────────────
        int thumbX = x + PAD;
        int thumbY = y + PAD;

        if (ThumbnailManager.INSTANCE.hasAnyTexture()) {
            Identifier thumb = ThumbnailManager.INSTANCE.getTexture();
            RoundedRectRenderer.drawTextureSquircle(ctx, thumb, thumbX, thumbY, THUMB_SIZE, THUMB_R, BG_COLOR);
        } else {
            RoundedRectRenderer.fillSquircle(ctx, thumbX, thumbY, THUMB_SIZE, THUMB_R, 0xFF333333);
        }

        String title  = coalesce(info != null ? info.title()  : null, "Unknown");
        String artist = coalesce(info != null ? info.artist() : null, "");

        int textX     = thumbX + THUMB_SIZE + THUMB_GAP;   // x+52
        int textAreaW = w - PAD - THUMB_SIZE - THUMB_GAP - PAD;  // 155-6-40-6-6=97

        // Vertically center the text block inside the thumb height
        int textBlockH = artist.isEmpty() ? 8 : (8 + 3 + 8);
        int textTopY   = thumbY + (THUMB_SIZE - textBlockH) / 2;

        marquee.update(title);
        marquee.setBoxWidth(textAreaW);
        ctx.enableScissor(textX, textTopY, textX + textAreaW, textTopY + 8);
        int off = marquee.drawOffset();
        ctx.drawText(tr, title, textX - off, textTopY, textCol, false);
        if (marquee.scrolls()) {
            ctx.drawText(tr, title, textX - off + marquee.textWidth() + MarqueeText.GAP_PX, textTopY, textCol, false);
        }
        ctx.disableScissor();

        if (!artist.isEmpty()) {
            int artistY = textTopY + 8 + 3;
            String displayArtist = tr.getWidth(artist) > textAreaW
                    ? tr.trimToWidth(artist, textAreaW - tr.getWidth("...")) + "..."
                    : artist;
            ctx.enableScissor(textX, artistY, textX + textAreaW, artistY + 8);
            ctx.drawText(tr, displayArtist, textX, artistY, dimCol, false);
            ctx.disableScissor();
        }

        // ── Row 2: timeline bar ───────────────────────────────────────────────
        int rowBottom = thumbY + THUMB_SIZE;   // y+46
        barX     = x + PAD;
        barY     = rowBottom + 4;              // y+50
        barWidth = w - PAD * 2;               // 143

        if (info != null && info.durationMs() > 0) {
            RoundedRectRenderer.fill(ctx, barX, barY, barWidth, BAR_H, BAR_H / 2f, barBg);
            int progW = (int)(barWidth * info.progress());
            if (progW > 0) {
                RoundedRectRenderer.fill(ctx, barX, barY, progW, BAR_H, BAR_H / 2f, barFg);
            }

            // Timestamps
            int tsY = barY + BAR_H + 3;   // y+56
            String elapsed   = info.elapsedFormatted();
            String remaining = info.remainingFormatted();
            ctx.drawText(tr, elapsed,   barX,                                    tsY, dimCol, false);
            ctx.drawText(tr, remaining, barX + barWidth - tr.getWidth(remaining), tsY, dimCol, false);
        }

        // ── Row 3: prev / play-pause / next buttons (centered) ───────────────
        int btnY    = barY + BAR_H + 3 + 8 + PAD;
        int btnGap  = 4;
        playBtnX = x + w / 2 - BUTTON_W / 2;
        playBtnY = btnY;
        prevBtnX = playBtnX - BUTTON_W - btnGap;
        prevBtnY = btnY;
        nextBtnX = playBtnX + BUTTON_W + btnGap;
        nextBtnY = btnY;
        String playLabel = (info != null && info.isPlaying()) ? "⏸" : "▶";
        drawButton(ctx, tr, prevBtnX, btnY, BUTTON_W, BUTTON_H, "⏮", textCol);
        drawButton(ctx, tr, playBtnX, btnY, BUTTON_W, BUTTON_H, playLabel, textCol);
        drawButton(ctx, tr, nextBtnX, btnY, BUTTON_W, BUTTON_H, "⏭", textCol);
    }

    private static void drawButton(DrawContext ctx, TextRenderer tr,
                                   int x, int y, int w, int h,
                                   String label, int color) {
        RoundedRectRenderer.fill(ctx, x, y, w, h, 4f, 0xFF1A1A1A);
        int lw = tr.getWidth(label);
        ctx.drawText(tr, label, x + (w - lw) / 2, y + (h - 8) / 2, color, false);
    }

    private static String coalesce(String s, String fallback) {
        return (s != null && !s.isEmpty()) ? s : fallback;
    }
}
