package com.evand.musicplayer.hud;

import com.evand.musicplayer.media.MediaInfo;
import com.evand.musicplayer.media.ThumbnailManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public class PillRenderer {
    private static final int   BG_COLOR     = 0xFF000000;
    private static final int   TEXT_COLOR   = 0xFFFFFFFF;
    private static final int   DIM_COLOR    = 0xFF999999;
    private static final float CORNER_R     = 13f;
    private static final int   THUMB_SIZE   = 24;
    private static final float THUMB_R      = 5f;
    private static final int   PAD          = 4;
    private static final float TITLE_SCALE  = 0.85f;
    private static final float ARTIST_SCALE = 0.75f;

    public static void draw(GuiGraphicsExtractor ctx, MediaInfo info, MarqueeText marquee,
                            int x, int y, int w, int h) {
        RoundedRectRenderer.fill(ctx, x, y, w, h, CORNER_R, BG_COLOR);

        int thumbX = x + 2;
        int thumbY = y + (h - THUMB_SIZE) / 2;

        if (ThumbnailManager.INSTANCE.hasAnyTexture()) {
            Identifier thumb = ThumbnailManager.INSTANCE.getTexture();
            RoundedRectRenderer.drawTextureSquircle(ctx, thumb, thumbX, thumbY, THUMB_SIZE, THUMB_R, BG_COLOR);
        } else {
            RoundedRectRenderer.fillSquircle(ctx, thumbX, thumbY, THUMB_SIZE, THUMB_R, 0xFF333333);
        }

        Font font = Minecraft.getInstance().font;

        int textX    = thumbX + THUMB_SIZE + PAD;
        int textMaxW = (x + w) - textX - PAD;

        String title  = info != null && info.title()  != null && !info.title().isEmpty()  ? info.title()  : "No media";
        String artist = info != null && info.artist() != null && !info.artist().isEmpty() ? info.artist() : null;

        int titleY, artistY;
        if (artist != null) {
            int blockH = (int)(8 * TITLE_SCALE) + 2 + (int)(8 * ARTIST_SCALE);
            titleY  = y + (h - blockH) / 2;
            artistY = titleY + (int)(8 * TITLE_SCALE) + 2;
        } else {
            titleY  = y + (h - (int)(8 * TITLE_SCALE)) / 2;
            artistY = -1;
        }

        // Title at TITLE_SCALE
        // Scissor extended 1px left to compensate for int-truncation of (textX/scale)*scale.
        ctx.enableScissor(textX - 1, y, textX + textMaxW, y + h);
        marquee.update(title);
        marquee.setBoxWidth((int)(textMaxW / TITLE_SCALE));
        ctx.pose().pushMatrix();
        ctx.pose().scale(TITLE_SCALE, TITLE_SCALE);
        int off = marquee.drawOffset();
        int ty  = (int)(titleY / TITLE_SCALE);
        ctx.text(font, title, (int)((textX - off) / TITLE_SCALE), ty, TEXT_COLOR);
        if (marquee.scrolls()) {
            ctx.text(font, title,
                    (int)((textX - off + marquee.textWidth() + MarqueeText.GAP_PX) / TITLE_SCALE),
                    ty, TEXT_COLOR);
        }
        ctx.pose().popMatrix();
        ctx.disableScissor();

        // Artist at ARTIST_SCALE — trim with ellipsis if too wide
        if (artistY >= 0) {
            float s = ARTIST_SCALE;
            int artistMaxPx = (int)(textMaxW / s);
            String displayArtist = ellipsize(font, artist, artistMaxPx);
            ctx.enableScissor(textX - 1, y, textX + textMaxW, y + h);
            ctx.pose().pushMatrix();
            ctx.pose().scale(s, s);
            ctx.text(font, displayArtist, (int)(textX / s), (int)(artistY / s), DIM_COLOR);
            ctx.pose().popMatrix();
            ctx.disableScissor();
        }
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
