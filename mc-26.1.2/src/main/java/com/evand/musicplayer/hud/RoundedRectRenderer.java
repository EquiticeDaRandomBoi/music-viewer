package com.evand.musicplayer.hud;

import com.evand.musicplayer.media.ThumbnailManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public class RoundedRectRenderer {

    public static void fill(GuiGraphicsExtractor ctx, float x, float y, float w, float h, float r, int color) {
        r = Math.min(r, Math.min(w / 2f, h / 2f));

        int ix  = (int) x;
        int iy  = (int) y;
        int ix2 = (int)(x + w);
        int iy2 = (int)(y + h);
        int ir  = (int) r;

        if (ir <= 0) {
            ctx.fill(ix, iy, ix2, iy2, color);
            return;
        }

        if (iy + ir < iy2 - ir) {
            ctx.fill(ix, iy + ir, ix2, iy2 - ir, color);
        }

        for (int row = 0; row < ir; row++) {
            float dy     = ir - row - 0.5f;
            float dx     = (float) Math.sqrt((double) ir * ir - dy * dy);
            int   xInset = Math.round(ir - dx);

            ctx.fill(ix + xInset, iy + row,      ix2 - xInset, iy + row + 1,  color);
            ctx.fill(ix + xInset, iy2 - row - 1, ix2 - xInset, iy2 - row,     color);
        }
    }

    public static void fillSquircle(GuiGraphicsExtractor ctx, float x, float y, float size, float r, int color) {
        fill(ctx, x, y, size, size, r, color);
    }

    /**
     * Draw a texture in a rounded square, with a dip-to-black crossfade when the
     * thumbnail changes.  Uses DynamicTexture GPU views directly (avoids TextureManager lookup).
     * The {@code tex} Identifier parameter is accepted for API symmetry but not used here.
     */
    public static void drawTextureSquircle(GuiGraphicsExtractor ctx, Identifier tex,
                                           float x, float y, float size, float r,
                                           int bgColor) {
        int ix = (int) x;
        int iy = (int) y;
        int is = (int) size;
        int ir = (int) r;

        float          fade   = ThumbnailManager.INSTANCE.getFadeProgress();
        DynamicTexture currDt = ThumbnailManager.INSTANCE.getTextureObj();
        DynamicTexture prevDt = ThumbnailManager.INSTANCE.getPrevTextureObj();
        boolean        hasPrev = prevDt != null;

        if (hasPrev && fade < 0.5f) {
            blitDt(ctx, prevDt, ix, iy, is);
            fillBlack(ctx, ix, iy, is, fade * 2f);
        } else if (currDt != null) {
            blitDt(ctx, currDt, ix, iy, is);
            if (hasPrev && fade < 1f) {
                fillBlack(ctx, ix, iy, is, (1f - fade) * 2f);
            }
        }

        maskCorners(ctx, ix, iy, is, ir, bgColor);
    }

    private static void blitDt(GuiGraphicsExtractor ctx, DynamicTexture dt, int ix, int iy, int is) {
        var view = dt.getTextureView();
        if (view != null) {
            ctx.blit(view, dt.getSampler(), ix, iy, ix + is, iy + is, 0f, 1f, 0f, 1f);
        }
    }

    private static void fillBlack(GuiGraphicsExtractor ctx, int ix, int iy, int is, float alpha) {
        if (alpha <= 0.001f) return;
        int a = (int)(Math.min(1f, alpha) * 255) & 0xFF;
        ctx.fill(ix, iy, ix + is, iy + is, a << 24);
    }

    private static void maskCorners(GuiGraphicsExtractor ctx, int ix, int iy, int is, int ir, int bgColor) {
        if (ir <= 0) return;
        for (int row = 0; row < ir; row++) {
            float dy     = ir - row - 0.5f;
            float dx     = (float) Math.sqrt((double) ir * ir - dy * dy);
            int   xInset = Math.round(ir - dx);
            if (xInset <= 0) continue;

            ctx.fill(ix,               iy + row,          ix + xInset,       iy + row + 1,       bgColor);
            ctx.fill(ix + is - xInset, iy + row,          ix + is,           iy + row + 1,       bgColor);
            ctx.fill(ix,               iy + is - row - 1, ix + xInset,       iy + is - row,      bgColor);
            ctx.fill(ix + is - xInset, iy + is - row - 1, ix + is,           iy + is - row,      bgColor);
        }
    }
}
