package com.evand.musicplayer.hud;

import com.evand.musicplayer.media.MediaInfo;
import com.evand.musicplayer.media.ThumbnailManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public class CardRenderer {

    @FunctionalInterface
    public interface SeekCallback { void seek(long positionMs); }

    private static final int   BG_COLOR   = 0xFF000000;
    private static final float CORNER_R   = 10f;
    private static final int   THUMB_SIZE = 40;
    private static final float THUMB_R    = 7f;
    private static final int   PAD        = 6;
    private static final int   THUMB_GAP  = 6;
    private static final int   BUTTON_W   = 24;
    private static final int   BUTTON_H   = 18;
    private static final int   BAR_H      = 3;

    public static int playBtnX, playBtnY;
    public static int btnW = BUTTON_W, btnH = BUTTON_H;
    public static int barX, barY, barWidth;
    public static boolean cardDrawn = false;

    public static void draw(GuiGraphicsExtractor ctx, MediaInfo info, MarqueeText marquee,
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

        Font font = Minecraft.getInstance().font;

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

        int textX     = thumbX + THUMB_SIZE + THUMB_GAP;
        int textAreaW = w - PAD - THUMB_SIZE - THUMB_GAP - PAD;

        int textBlockH = artist.isEmpty() ? 8 : (8 + 3 + 8);
        int textTopY   = thumbY + (THUMB_SIZE - textBlockH) / 2;

        marquee.update(title);
        marquee.setBoxWidth(textAreaW);
        ctx.enableScissor(textX, textTopY, textX + textAreaW, textTopY + 8);
        int off = marquee.drawOffset();
        ctx.text(font, title, textX - off, textTopY, textCol);
        if (marquee.scrolls()) {
            ctx.text(font, title, textX - off + marquee.textWidth() + MarqueeText.GAP_PX, textTopY, textCol);
        }
        ctx.disableScissor();

        if (!artist.isEmpty()) {
            int artistY = textTopY + 8 + 3;
            String displayArtist = ellipsize(font, artist, textAreaW);
            ctx.enableScissor(textX, artistY, textX + textAreaW, artistY + 8);
            ctx.text(font, displayArtist, textX, artistY, dimCol);
            ctx.disableScissor();
        }

        // ── Row 2: timeline bar ───────────────────────────────────────────────
        int rowBottom = thumbY + THUMB_SIZE;
        barX     = x + PAD;
        barY     = rowBottom + 4;
        barWidth = w - PAD * 2;

        if (info != null && info.durationMs() > 0) {
            RoundedRectRenderer.fill(ctx, barX, barY, barWidth, BAR_H, BAR_H / 2f, barBg);
            int progW = (int)(barWidth * info.progress());
            if (progW > 0) {
                RoundedRectRenderer.fill(ctx, barX, barY, progW, BAR_H, BAR_H / 2f, barFg);
            }

            int tsY = barY + BAR_H + 3;
            String elapsed   = info.elapsedFormatted();
            String remaining = info.remainingFormatted();
            ctx.text(font, elapsed,   barX,                                    tsY, dimCol);
            ctx.text(font, remaining, barX + barWidth - font.width(remaining), tsY, dimCol);
        }

        // ── Row 3: play/pause button (centered) ──────────────────────────────
        int btnY = barY + BAR_H + 3 + 8 + PAD;
        playBtnX = x + w / 2 - BUTTON_W / 2;
        playBtnY = btnY;
        String playLabel = (info != null && info.isPlaying()) ? "⏸" : "▶";
        drawButton(ctx, font, playBtnX, btnY, BUTTON_W, BUTTON_H, playLabel, textCol);
    }

    private static void drawButton(GuiGraphicsExtractor ctx, Font font,
                                   int x, int y, int w, int h,
                                   String label, int color) {
        RoundedRectRenderer.fill(ctx, x, y, w, h, 4f, 0xFF1A1A1A);
        int lw = font.width(label);
        ctx.text(font, label, x + (w - lw) / 2, y + (h - 8) / 2, color);
    }

    private static String coalesce(String s, String fallback) {
        return (s != null && !s.isEmpty()) ? s : fallback;
    }

    private static String ellipsize(Font font, String s, int maxPx) {
        if (font.width(s) <= maxPx) return s;
        int limit = maxPx - font.width("...");
        int w = 0, end = 0;
        for (int i = 0; i < s.length(); i++) {
            int cw = font.width(s.substring(i, i + 1));
            if (w + cw > limit) break;
            w += cw; end = i + 1;
        }
        return s.substring(0, end) + "...";
    }
}
