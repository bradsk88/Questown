package ca.bradj.questown.gui;

import net.minecraft.resources.ResourceLocation;

/**
 * A whole texture file drawn at 1:1, less a trimmed border.
 */
public record GuiSprite(
        ResourceLocation texture,
        int width,
        int height,
        int trimLeft,
        int trimRight,
        int trimTop,
        int trimBottom
) {
    public GuiSprite(
            ResourceLocation texture,
            int width,
            int height
    ) {
        this(texture, width, height, 0, 0, 0, 0);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void draw(
            RenderContext ctx,
            int x,
            int y
    ) {
        ctx.blit(
                texture,
                x + trimLeft,
                y + trimTop,
                trimLeft,
                trimTop,
                width - trimLeft - trimRight,
                height - trimTop - trimBottom,
                width,
                height
        );
    }
}
