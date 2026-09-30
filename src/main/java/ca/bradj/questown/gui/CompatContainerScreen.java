package ca.bradj.questown.gui;

import ca.bradj.questown.mc.Compat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.List;

/**
 * Base for questown's {@link AbstractContainerScreen} leaves. Owns the version-bound render entry
 * points (the 1.20.6 {@code render}/{@code renderBg}/{@code renderLabels}/{@code renderBackground}
 * overrides) so a leaf expresses the version-neutral draw order via {@link #renderPrologue},
 * {@link #renderBgContent}, {@link #renderLabelsContent} and {@link #renderScreenContent}, and its
 * {@link #showTooltip} surface, without naming the render-argument type.
 *
 * Render order (mirrors the 1.20.6 {@link AbstractContainerScreen#render} chain):
 * {@code renderPrologue} → {@code super.render} (which calls {@code renderBackground} →
 * {@code renderBgContent}, then the widgets, the slots and {@code renderLabelsContent}) →
 * {@code renderScreenContent}.
 */
public abstract class CompatContainerScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {

    protected CompatContainerScreen(
            T menu,
            Inventory playerInventory,
            Component title
    ) {
        super(menu, playerInventory, title);
    }

    @Override
    public final void render(GuiGraphics gui, int mouseX, int mouseY, float partialTicks) {
        this.renderPrologue(this.renderContext(gui), mouseX, mouseY, partialTicks);
        super.render(gui, mouseX, mouseY, partialTicks);
        this.renderScreenContent(this.renderContext(gui), mouseX, mouseY, partialTicks);
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTicks, int mouseX, int mouseY) {
        this.renderBgContent(this.renderContext(gui), partialTicks, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics gui, int mouseX, int mouseY) {
        this.renderLabelsContent(this.renderContext(gui), mouseX, mouseY);
    }

    // -- version-neutral hooks (leaves override these, not the GuiGraphics-taking methods)
    protected void renderPrologue(RenderContext ctx, int mouseX, int mouseY, float partialTicks) { }

    protected void renderBgContent(RenderContext ctx, float partialTicks, int mouseX, int mouseY) { }

    protected void renderLabelsContent(RenderContext ctx, int mouseX, int mouseY) { }

    protected void renderScreenContent(RenderContext ctx, int mouseX, int mouseY, float partialTicks) { }

    // -- tooltip surface (see CompatScreen.showTooltip)
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
