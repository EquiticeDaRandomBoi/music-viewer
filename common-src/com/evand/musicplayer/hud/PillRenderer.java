// common-src/com/evand/musicplayer/hud/PillRenderer.java
package com.evand.musicplayer.hud;

import com.evand.musicplayer.media.MediaInfo;
import com.evand.musicplayer.media.ThumbnailManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

public class PillRenderer {
    private static final int   BG_COLOR   = 0xFF000000;
    private static final int   TEXT_COLOR = 0xFFFFFFFF;
    private static final float CORNER_R   = 14f;
    private static final int   THUMB_SIZE = 20;
    private static final float THUMB_R    = 5f;
    private static final int   PADDING    = 4;

    public static void draw(DrawContext ctx, MediaInfo info, MarqueeText marquee,
                             int x, int y, int w, int h) {
        // Background pill
        RoundedRectRenderer.fill(ctx, x, y, w, h, CORNER_R, BG_COLOR);

        int thumbX = x + PADDING;
        int thumbY = y + (h - THUMB_SIZE) / 2;

        // Thumbnail or grey placeholder
        Identifier thumb = ThumbnailManager.INSTANCE.getTexture();
        if (thumb != null) {
            RoundedRectRenderer.drawTextureSquircle(ctx, thumb, thumbX, thumbY, THUMB_SIZE, THUMB_R, BG_COLOR);
        } else {
            RoundedRectRenderer.fillSquircle(ctx, thumbX, thumbY, THUMB_SIZE, THUMB_R, 0xFF333333);
        }

        // Title text (scrolled)
        int textX    = thumbX + THUMB_SIZE + PADDING;
        int textMaxW = (x + w) - textX - PADDING;
        int textY    = y + (h - 8) / 2; // 8 = default MC font height

        // Enable scissor to clip marquee text
        ctx.enableScissor(textX, y, textX + textMaxW, y + h);

        var tr = MinecraftClient.getInstance().textRenderer;
        String title = (info != null && info.title() != null && !info.title().isEmpty())
                       ? info.title() : "No media";
        marquee.update(title);
        marquee.setBoxWidth(textMaxW);
        ctx.drawText(tr, title, textX - marquee.drawOffset(), textY, TEXT_COLOR, false);

        ctx.disableScissor();
    }
}
