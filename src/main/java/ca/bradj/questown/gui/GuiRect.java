package ca.bradj.questown.gui;

import ca.bradj.questown.core.UtilClean;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;

/**
 * An immutable screen-space rectangle.
 */
public record GuiRect(
        int x,
        int y,
        int width,
        int height
) {
    public static final GuiRect EMPTY = new GuiRect(0, 0, 0, 0);

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isEmpty() {
        return width <= 0 || height <= 0;
    }

    public GuiRect moveUp(int amount) {
        return new GuiRect(x, y - amount, width, height);
    }

    public GuiRect moveDown(int amount) {
        return new GuiRect(x, y + amount, width, height);
    }

    public GuiRect expandBy(int amount) {
        return new GuiRect(x - amount, y - amount, width + amount * 2, height + amount * 2);
    }

    public boolean contains(
            double pointX,
            double pointY
    ) {
        return UtilClean.isCoordInBox(pointX, pointY, x, y, width, height);
    }

    /** The smallest rectangle holding both; an empty one is ignored. */
    public static GuiRect union(
            GuiRect a,
            GuiRect b
    ) {
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        int left = Math.min(a.x, b.x);
        int top = Math.min(a.y, b.y);
        int right = Math.max(a.x + a.width, b.x + b.width);
        int bottom = Math.max(a.y + a.height, b.y + b.height);
        return new GuiRect(left, top, right - left, bottom - top);
    }

    public static GuiRect centerArea(
            GuiRect outer,
            int width,
            int height
    ) {
        return new GuiRect(
                outer.x + Math.round((outer.width - width) / 2.0f),
                outer.y + Math.round((outer.height - height) / 2.0f),
                width,
                height
        );
    }

    public static GuiRect centerTextArea(
            GuiRect outer,
            Font font,
            String text
    ) {
        return centerArea(outer, font.width(text), font.lineHeight);
    }

    public static GuiRect centerTextArea(
            GuiRect outer,
            Font font,
            FormattedText text
    ) {
        return centerArea(outer, font.width(text), font.lineHeight);
    }
}
