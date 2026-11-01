package ca.bradj.questown.gui;

import ca.bradj.questown.core.Coordinate;
import ca.bradj.questown.mc.JEI;
import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Version-neutral handle over the render argument (a {@code GuiGraphics} in 1.20.6, a
 * {@code PoseStack} in 1.19.2): screens and widget helpers name only this.
 */
public record RenderContext(
        Font font,
        ItemRenderer itemRenderer,
        PoseStack gui
) {
    /** Fills the font from the running client. */
    public RenderContext(
            ItemRenderer itemRenderer,
            PoseStack gui
    ) {
        this(Minecraft.getInstance().font, itemRenderer, gui);
    }

    // -- transforms
    public PoseStack pose() {
        return gui;
    }

    // Legacy name kept for existing callers.
    public PoseStack stack() {
        return gui;
    }

    // -- fills
    public void fill(int x1, int y1, int x2, int y2, int color) {
        GuiComponent.fill(gui, x1, y1, x2, y2, color);
    }

    public void fillGradient(int x1, int y1, int x2, int y2, int top, int bottom) {
        GRADIENT.fill(gui, x1, y1, x2, y2, top, bottom);
    }

    // -- text
    public void text(FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        if (shadow) {
            font.drawShadow(gui, text, x, y, color);
        } else {
            font.draw(gui, text, x, y, color);
        }
    }

    public void text(String text, int x, int y, int color, boolean shadow) {
        if (shadow) {
            font.drawShadow(gui, text, x, y, color);
        } else {
            font.draw(gui, text, x, y, color);
        }
    }

    public void text(Component text, int x, int y, int color, boolean shadow) {
        if (shadow) {
            font.drawShadow(gui, text, x, y, color);
        } else {
            font.draw(gui, text, x, y, color);
        }
    }

    public void darkText(FormattedCharSequence text, int x, int y) {
        font.draw(gui, text, x, y, 0xFF404040);
    }

    public void darkText(String text, int x, int y) {
        font.draw(gui, text, x, y, 0xFF404040);
    }

    public void darkText(Component text, int x, int y) {
        font.draw(gui, text, x, y, 0xFF404040);
    }

    /** {@link #darkText} centred on {@code centerX}: the body-text look, not a shadowed title. */
    public void centeredDark(FormattedCharSequence text, int centerX, int y) {
        font.draw(gui, text, centerX - font.width(text) / 2, y, 0xFF404040);
    }

    public void lightText(String text, int x, int y) {
        font.drawShadow(gui, text, x, y, 0xFFFFFFFF);
    }

    public void centered(Component text, int centerX, int y, int color) {
        GuiComponent.drawCenteredString(gui, font, text, centerX, y, color);
    }

    public void centered(String text, int centerX, int y, int color) {
        GuiComponent.drawCenteredString(gui, font, text, centerX, y, color);
    }

    public void centered(FormattedCharSequence text, int centerX, int y, int color) {
        GuiComponent.drawCenteredString(gui, font, text, centerX, y, color);
    }

    // -- items
    public void renderItem(ItemStack stack, int x, int y) {
        itemRenderer.renderAndDecorateItem(stack, x, y);
    }

    public void renderItemDecorations(ItemStack stack, int x, int y) {
        itemRenderer.renderGuiItemDecorations(font, stack, x, y);
    }

    public void renderItemAndDecorations(ItemStack stack, int x, int y) {
        itemRenderer.renderAndDecorateItem(stack, x, y);
        itemRenderer.renderGuiItemDecorations(font, stack, x, y);
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

    // Vanilla's own checkbox art. 1.19.2 has no sprite atlas: the
    // 64x64 checkbox sheet holds 20x20 cells, u 0/20 = plain/highlighted, v 0/20 = off/on.
    private static final ResourceLocation CHECKBOX = new ResourceLocation("textures/gui/checkbox.png");

    public void checkbox(int x, int y, int size, boolean checked, boolean hovered) {
        com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, CHECKBOX);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        GuiComponent.blit(gui, x, y, size, size, hovered ? 20 : 0, checked ? 20 : 0, 20, 20, 64, 64);
    }

    // -- textures
    /** A region of a 256x256 texture sheet at 1:1. */
    public void blit(ResourceLocation texture, int x, int y, int u, int v, int width, int height) {
        com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, texture);
        GuiComponent.blit(gui, x, y, u, v, width, height, 256, 256);
    }

    /** A region of a texture of the given size at 1:1. */
    public void blit(ResourceLocation texture, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight) {
        com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, texture);
        GuiComponent.blit(gui, x, y, u, v, width, height, textureWidth, textureHeight);
    }

    public void enableScissor(int x1, int y1, int x2, int y2) {
        GuiComponent.enableScissor(x1, y1, x2, y2);
    }

    public void disableScissor() {
        GuiComponent.disableScissor();
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

    private static final Gradient GRADIENT = new Gradient();

    /** 1.19.2's fillGradient is a protected GuiComponent instance method. */
    private static final class Gradient extends GuiComponent {
        void fill(PoseStack pose, int x1, int y1, int x2, int y2, int top, int bottom) {
            fillGradient(pose, x1, y1, x2, y2, top, bottom);
        }
    }
}
