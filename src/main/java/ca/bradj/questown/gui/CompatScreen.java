package ca.bradj.questown.gui;

import ca.bradj.questown.mc.Compat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
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

    protected void showTooltip(List<Component> tooltips) {
        List<FormattedCharSequence> ordered = new ArrayList<>();
        for (Component tooltip : tooltips) {
            ordered.add(tooltip.getVisualOrderText());
        }
        this.setTooltipForNextRenderPass(ordered);
    }

    protected RenderContext renderContext(GuiGraphics gui) {
        return new RenderContext(this.font, Compat.itemRenderer(), gui);
    }
}
