// common-src/com/evand/musicplayer/hud/RoundedRectRenderer.java
package com.evand.musicplayer.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * Utility for drawing rounded rectangles and squircle-masked textures in the GUI.
 *
 * <p>In MC 1.21.11, the immediate-mode Tessellator/RenderSystem pipeline used in
 * older versions has been replaced by a deferred render-state system.  All drawing
 * here therefore routes through {@link DrawContext}, which is the correct public API.
 *
 * <p>Corner arcs are produced by a per-scanline fill: for each horizontal row
 * inside the corner radius, the inset from the edge is computed from the circle
 * equation, and {@code DrawContext.fill()} is called for the trimmed span.
 * This produces pixel-accurate quarter-circle arcs without any GPU buffer setup.
 */
public class RoundedRectRenderer {

    // Number of scanlines used for corner arc rendering.
    // Setting to a constant keeps behaviour deterministic; the scanline approach
    // automatically adapts to the actual pixel radius rather than using a fixed
    // segment count as the old Tessellator fan did.
    private static final int CORNER_SEGMENTS = 12; // retained for API compatibility notes

    /**
     * Draw a filled rounded rectangle.
     *
     * <p>Note: the MC 1.21.11 GUI API uses {@link DrawContext} rather than the
     * legacy {@code MatrixStack} + Tessellator pipeline.  Callers that previously
     * passed {@code ctx.getMatrices()} should pass {@code ctx} directly.
     *
     * @param ctx   current draw context
     * @param x     left edge (screen pixels)
     * @param y     top edge  (screen pixels)
     * @param w     width
     * @param h     height
     * @param r     corner radius (clamped to min(w/2, h/2))
     * @param color ARGB packed color
     */
    public static void fill(DrawContext ctx, float x, float y, float w, float h, float r, int color) {
        r = Math.min(r, Math.min(w / 2f, h / 2f));

        int ix  = (int) x;
        int iy  = (int) y;
        int ix2 = (int) (x + w);
        int iy2 = (int) (y + h);
        int ir  = (int) r;

        if (ir <= 0) {
            ctx.fill(ix, iy, ix2, iy2, color);
            return;
        }

        // Centre band — full width, between the corner arc rows
        if (iy + ir < iy2 - ir) {
            ctx.fill(ix, iy + ir, ix2, iy2 - ir, color);
        }

        // Scanline fill for top and bottom corner zones
        for (int row = 0; row < ir; row++) {
            // Vertical distance from arc-centre to scanline mid-point
            float dy = ir - row - 0.5f;
            // Horizontal extent of the arc at this distance
            float dx = (float) Math.sqrt((double) ir * ir - dy * dy);
            // Pixels to inset from the left/right edges at this row
            int xInset = Math.round(ir - dx);

            // Top scanline
            ctx.fill(ix + xInset, iy + row, ix2 - xInset, iy + row + 1, color);
            // Bottom scanline (mirrored)
            ctx.fill(ix + xInset, iy2 - row - 1, ix2 - xInset, iy2 - row, color);
        }
    }

    /**
     * Fill a squircle (square with rounded corners) — convenience wrapper over
     * {@link #fill}.  Used as a placeholder background for thumbnails.
     *
     * @param ctx   current draw context
     * @param x     left edge
     * @param y     top edge
     * @param size  side length
     * @param r     corner radius
     * @param color ARGB packed color
     */
    public static void fillSquircle(DrawContext ctx, float x, float y, float size, float r, int color) {
        fill(ctx, x, y, size, size, r, color);
    }

    /**
     * Draw a texture clipped to a squircle shape.
     *
     * <p>Renders the full square texture first, then overlays {@code bgColor}
     * corner "ears" to mask the round cutout.  The caller must supply the
     * background color that surrounds the widget so the corners blend correctly.
     *
     * @param ctx     current draw context
     * @param tex     texture identifier
     * @param x       left edge
     * @param y       top edge
     * @param size    side length
     * @param r       corner radius
     * @param bgColor ARGB background color used for corner masking
     */
    public static void drawTextureSquircle(DrawContext ctx, Identifier tex,
                                           float x, float y, float size, float r,
                                           int bgColor) {
        int ix  = (int) x;
        int iy  = (int) y;
        int is  = (int) size;
        int ir  = (int) r;

        // Draw texture as a full square (u0,v0 = 0,0  u1,v1 = 1,1)
        ctx.drawTexturedQuad(tex, ix, iy, ix + is, iy + is, 0f, 0f, 1f, 1f);

        // Paint corner ears in bgColor to produce the round mask
        maskCorners(ctx, ix, iy, is, ir, bgColor);
    }

    // ── private helpers ──────────────────────────────────────────────────────

    /**
     * Fill the four corner "ear" regions (the areas outside the arc but inside
     * the r×r corner square) with {@code bgColor}.
     *
     * <p>This is the overlay pass used by {@link #drawTextureSquircle} to clip
     * the texture to a squircle shape.
     */
    private static void maskCorners(DrawContext ctx, int ix, int iy, int is, int ir, int bgColor) {
        if (ir <= 0) return;

        for (int row = 0; row < ir; row++) {
            float dy     = ir - row - 0.5f;
            float dx     = (float) Math.sqrt((double) ir * ir - dy * dy);
            int   xInset = Math.round(ir - dx);
            if (xInset <= 0) continue;

            // Top-left ear
            ctx.fill(ix,             iy + row,        ix + xInset,      iy + row + 1,  bgColor);
            // Top-right ear
            ctx.fill(ix + is - xInset, iy + row,      ix + is,          iy + row + 1,  bgColor);
            // Bottom-left ear
            ctx.fill(ix,             iy + is - row - 1, ix + xInset,    iy + is - row, bgColor);
            // Bottom-right ear
            ctx.fill(ix + is - xInset, iy + is - row - 1, ix + is,      iy + is - row, bgColor);
        }
    }
}
