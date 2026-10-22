package ca.bradj.questown.gui;

import ca.bradj.questown.core.Coordinate;
import ca.bradj.questown.mc.JEI;
import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Version-neutral handle over the render argument. The strangler keeps the version-specific
 * render API (a {@code GuiGraphics} in 1.20.6, a {@code PoseStack} in 1.19.2) inside the seam
 * family; screens and widget helpers name only this handle and its methods. Each method is a
 * faithful pass-through, so rendering is preserved by construction.
 */
public record RenderContext(
        Font font,
        ItemRenderer itemRenderer,
        GuiGraphics gui
) {
    /** Construction that fills the font from the running client (pre-existing two-arg sites). */
    public RenderContext(
            ItemRenderer itemRenderer,
            GuiGraphics gui
    ) {
        this(Minecraft.getInstance().font, itemRenderer, gui);
    }

    // -- transforms
    public PoseStack pose() {
        return gui.pose();
    }

    // Legacy name kept for existing callers.
    public PoseStack stack() {
        return gui.pose();
    }

    // -- fills
    public void fill(int x1, int y1, int x2, int y2, int color) {
        gui.fill(x1, y1, x2, y2, color);
    }

    public void fillGradient(int x1, int y1, int x2, int y2, int top, int bottom) {
        gui.fillGradient(x1, y1, x2, y2, top, bottom);
    }

    // -- text
    public void text(FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        gui.drawString(font, text, x, y, color, shadow);
    }

    public void text(String text, int x, int y, int color, boolean shadow) {
        gui.drawString(font, text, x, y, color, shadow);
    }

    public void text(Component text, int x, int y, int color, boolean shadow) {
        gui.drawString(font, text, x, y, color, shadow);
    }

    public void darkText(FormattedCharSequence text, int x, int y) {
        gui.drawString(font, text, x, y, 0xFF404040, false);
    }

    public void darkText(String text, int x, int y) {
        gui.drawString(font, text, x, y, 0xFF404040, false);
    }

    public void darkText(Component text, int x, int y) {
        gui.drawString(font, text, x, y, 0xFF404040, false);
    }

    /** {@link #darkText} centred on {@code centerX}: the body-text look, not a shadowed title. */
    public void centeredDark(FormattedCharSequence text, int centerX, int y) {
        gui.drawString(font, text, centerX - font.width(text) / 2, y, 0xFF404040, false);
    }

    public void lightText(String text, int x, int y) {
        gui.drawString(font, text, x, y, 0xFFFFFFFF, true);
    }

    public void centered(Component text, int centerX, int y, int color) {
        gui.drawCenteredString(font, text, centerX, y, color);
    }

    public void centered(String text, int centerX, int y, int color) {
        gui.drawCenteredString(font, text, centerX, y, color);
    }

    public void centered(FormattedCharSequence text, int centerX, int y, int color) {
        gui.drawCenteredString(font, text, centerX, y, color);
    }

    // -- items
    public void renderItem(ItemStack stack, int x, int y) {
        gui.renderItem(stack, x, y);
    }

    public void renderItemDecorations(ItemStack stack, int x, int y) {
        gui.renderItemDecorations(font, stack, x, y);
    }

    public void renderItemAndDecorations(ItemStack stack, int x, int y) {
        gui.renderItem(stack, x, y);
        gui.renderItemDecorations(font, stack, x, y);
    }

    // -- JEI drawables and widgets
    public void drawNineNine(JEI.NineNine drawable, int x, int y, int width, int height) {
        drawable.draw(gui, x, y, width, height);
    }

    public void drawJei(IDrawable drawable, int x, int y) {
        drawable.draw(gui, x, y);
    }

    public void drawJei(IDrawable drawable) {
        drawable.draw(gui);
    }

    public void drawJeiStatic(IDrawableStatic drawable, int x, int y, int width, int height, int maxU, int maxV) {
        drawable.draw(gui, x, y, width, height, maxU, maxV);
    }

    // Vanilla's own checkbox art (1.20.6 sprite atlas), so a toggle looks like a vanilla control
    // (docs/ui-principles.md 1). Vanilla itself draws the 20x20 sprite scaled to its box size.
    private static final ResourceLocation CHECKBOX = new ResourceLocation("widget/checkbox");
    private static final ResourceLocation CHECKBOX_SELECTED = new ResourceLocation("widget/checkbox_selected");
    private static final ResourceLocation CHECKBOX_HIGHLIGHTED = new ResourceLocation("widget/checkbox_highlighted");
    private static final ResourceLocation CHECKBOX_SELECTED_HIGHLIGHTED =
            new ResourceLocation("widget/checkbox_selected_highlighted");

    public void checkbox(int x, int y, int size, boolean checked, boolean hovered) {
        ResourceLocation sprite;
        if (checked) {
            sprite = hovered ? CHECKBOX_SELECTED_HIGHLIGHTED : CHECKBOX_SELECTED;
        } else {
            sprite = hovered ? CHECKBOX_HIGHLIGHTED : CHECKBOX;
        }
        gui.blitSprite(sprite, x, y, size, size);
    }

    public void renderWidget(Button widget, int mouseX, int mouseY, float partialTicks) {
        widget.render(gui, mouseX, mouseY, partialTicks);
    }

    // -- text geometry
    public List<FormattedCharSequence> split(Component text, int width) {
        return font.split(text, width);
    }

    // -- wrapped text (routes through Compat so the gui-lint capture hook is preserved)
    public int darkTextWrap(Coordinate topLeft, int width, Component text) {
        return ca.bradj.questown.mc.Compat.drawDarkTextWrap(font, gui, topLeft, width, text);
    }

    public int redTextWrap(Coordinate topLeft, int width, Component text) {
        return ca.bradj.questown.mc.Compat.drawRedTextWrap(font, gui, topLeft, width, text);
    }

    public int textWrap(Coordinate topLeft, int width, Component text, int color) {
        return ca.bradj.questown.mc.Compat.drawTextWrap(font, gui, topLeft, width, text, color, false);
    }
}
