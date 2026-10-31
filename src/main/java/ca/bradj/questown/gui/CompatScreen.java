package ca.bradj.questown.gui;

import ca.bradj.questown.mc.Compat;
import com.mojang.blaze3d.vertex.PoseStack;
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

    // 1.19.2 has no setTooltipForNextRenderPass: the tooltip a leaf asks for this frame is drawn
    // after the content.
    private List<? extends FormattedCharSequence> pendingTooltip;

    protected CompatScreen(Component title) {
        super(title);
    }

    @Override
    public final void render(PoseStack gui, int mouseX, int mouseY, float partialTicks) {
        RenderContext ctx = this.renderContext(gui);
        this.pendingTooltip = null;
        this.renderPrologue(ctx, mouseX, mouseY, partialTicks);
        // 1.20.6's Screen.render draws the background first; 1.19.2's leaves it to the screen.
        this.renderScreenSurface(ctx, mouseX, mouseY, partialTicks);
        super.render(gui, mouseX, mouseY, partialTicks);
        this.renderScreenContent(ctx, mouseX, mouseY, partialTicks);
        if (this.pendingTooltip != null) {
            this.renderTooltip(gui, this.pendingTooltip, mouseX, mouseY);
        }
    }

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

    // -- version-neutral hooks (leaves override these, not the PoseStack-taking methods)
    protected void renderPrologue(RenderContext ctx, int mouseX, int mouseY, float partialTicks) { }

    protected void renderScreenSurface(RenderContext ctx, int mouseX, int mouseY, float partialTicks) { }

    protected void renderScreenContent(RenderContext ctx, int mouseX, int mouseY, float partialTicks) { }

    protected void showTooltip(Component tooltip) {
        this.pendingTooltip = this.font.split(tooltip, Math.max(this.width / 2, 200));
    }

    protected void showTooltip(List<FormattedCharSequence> lines) {
        this.pendingTooltip = lines;
    }

    protected RenderContext renderContext(PoseStack gui) {
        return new RenderContext(this.font, Compat.itemRenderer(), gui);
    }
}
