package ca.bradj.questown.gui;

import ca.bradj.questown.mc.Compat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Base for questown's plain {@link Screen} leaves. Owns the version-bound render entry points
 * (the 1.20.6 {@code render(GuiGraphics, ...)} and {@code renderBackground(GuiGraphics, ...)}
 * overrides) so a leaf expresses the version-neutral draw order via
 * {@link #renderPrologue}, {@link #renderScreenSurface} and {@link #renderScreenContent},
 * and its {@link #showTooltip} surface, without ever naming the render-argument type.
 *
 * Render order (mirrors the per-screen 1.20.6 chain):
 * {@code renderPrologue} → {@code super.render} (which calls {@code renderBackground} →
 * {@code renderScreenSurface}, then the renderable widgets) → {@code renderScreenContent}.
 */
public abstract class CompatScreen extends Screen {

    protected CompatScreen(Component title) {
        super(title);
    }

    @Override
    public final void render(GuiGraphics gui, int mouseX, int mouseY, float partialTicks) {
        this.renderPrologue(this.renderContext(gui), mouseX, mouseY, partialTicks);
        super.render(gui, mouseX, mouseY, partialTicks);
        this.renderScreenContent(this.renderContext(gui), mouseX, mouseY, partialTicks);
    }

    @Override
    public void renderBackground(GuiGraphics gui, int mouseX, int mouseY, float partialTicks) {
        this.renderScreenSurface(this.renderContext(gui), mouseX, mouseY, partialTicks);
    }

    /**
     * The inventory key closes the screen, as it does every container screen (vanilla
     * {@code AbstractContainerScreen.keyPressed}: the screen and its focused widget get the key
     * first, so typing into a focused text box still works). 2026-10-20 playtest: the item jobs
     * screen ignored [E] while every other questown screen closed on it.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (this.minecraft == null || !this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            return false;
        }
        this.onClose();
        return true;
    }

    // -- version-neutral hooks (leaves override these, not the GuiGraphics-taking methods)
    protected void renderPrologue(RenderContext ctx, int mouseX, int mouseY, float partialTicks) { }

    protected void renderScreenSurface(RenderContext ctx, int mouseX, int mouseY, float partialTicks) { }

    protected void renderScreenContent(RenderContext ctx, int mouseX, int mouseY, float partialTicks) { }

    /**
     * The 1.20.6 tooltip surface. The single-Component super method is {@code protected}, so a
     * screen in another package cannot call it; the base (in the GUI seam) does.
     */
    protected void showTooltip(Component tooltip) {
        this.setTooltipForNextRenderPass(tooltip);
    }

    protected void showTooltip(List<FormattedCharSequence> lines) {
        this.setTooltipForNextRenderPass(lines);
    }

    protected RenderContext renderContext(GuiGraphics gui) {
        return new RenderContext(this.font, Compat.itemRenderer(), gui);
    }
}
