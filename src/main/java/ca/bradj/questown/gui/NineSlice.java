package ca.bradj.questown.gui;

import net.minecraft.resources.ResourceLocation;

/**
 * A texture cut into nine pieces: the corners are drawn as they are and the edges and middle are tiled to fill
 * any size.
 */
public record NineSlice(
        ResourceLocation texture,
        int textureWidth,
        int textureHeight,
        int left,
        int right,
        int top,
        int bottom
) {
    public void draw(
            RenderContext ctx,
            int x,
            int y,
            int width,
            int height
    ) {
        int middleU = textureWidth - left - right;
        int middleV = textureHeight - top - bottom;
        int innerWidth = width - left - right;
        int innerHeight = height - top - bottom;
        int rightX = x + width - right;
        int bottomY = y + height - bottom;
        int rightU = textureWidth - right;
        int bottomV = textureHeight - bottom;

        piece(ctx, x, y, 0, 0, left, top);
        piece(ctx, rightX, y, rightU, 0, right, top);
        piece(ctx, x, bottomY, 0, bottomV, left, bottom);
        piece(ctx, rightX, bottomY, rightU, bottomV, right, bottom);
        if (innerWidth > 0) {
            tile(ctx, x + left, y, innerWidth, top, left, 0, middleU, top);
            tile(ctx, x + left, bottomY, innerWidth, bottom, left, bottomV, middleU, bottom);
        }
        if (innerHeight > 0) {
            tile(ctx, x, y + top, left, innerHeight, 0, top, left, middleV);
            tile(ctx, rightX, y + top, right, innerHeight, rightU, top, right, middleV);
        }
        if (innerWidth > 0 && innerHeight > 0) {
            tile(ctx, x + left, y + top, innerWidth, innerHeight, left, top, middleU, middleV);
        }
    }

    private void tile(
            RenderContext ctx,
            int x,
            int y,
            int width,
            int height,
            int u,
            int v,
            int tileWidth,
            int tileHeight
    ) {
        // Rows stack up from the bottom, so a cut-short row is the top one and shows its tile's lower part.
        for (int dx = 0; dx < width; dx += tileWidth) {
            int w = Math.min(tileWidth, width - dx);
            for (int rowBottom = height; rowBottom > 0; rowBottom -= tileHeight) {
                int h = Math.min(tileHeight, rowBottom);
                piece(ctx, x + dx, y + rowBottom - h, u, v + tileHeight - h, w, h);
            }
        }
    }

    private void piece(
            RenderContext ctx,
            int x,
            int y,
            int u,
            int v,
            int width,
            int height
    ) {
        ctx.blit(texture, x, y, u, v, width, height, textureWidth, textureHeight);
    }
}
