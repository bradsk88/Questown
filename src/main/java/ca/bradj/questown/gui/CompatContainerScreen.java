package ca.bradj.questown.gui;

import ca.bradj.questown.mc.Compat;
import com.mojang.blaze3d.vertex.PoseStack;
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

    // 1.19.2 has no setTooltipForNextRenderPass (see CompatScreen).
    private List<? extends FormattedCharSequence> pendingTooltip;

    protected CompatContainerScreen(
            T menu,
            Inventory playerInventory,
            Component title
    ) {
        super(menu, playerInventory, title);
    }

    @Override
    public final void render(PoseStack gui, int mouseX, int mouseY, float partialTicks) {
        RenderContext ctx = this.renderContext(gui);
        this.pendingTooltip = null;
        this.renderPrologue(ctx, mouseX, mouseY, partialTicks);
        // The 1.19.2 golden container screens dim the world behind the window themselves.
        this.renderBackground(gui);
        super.render(gui, mouseX, mouseY, partialTicks);
        this.renderScreenContent(ctx, mouseX, mouseY, partialTicks);
        if (this.pendingTooltip != null) {
            this.renderTooltip(gui, this.pendingTooltip, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(PoseStack gui, float partialTicks, int mouseX, int mouseY) {
        this.renderBgContent(this.renderContext(gui), partialTicks, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(PoseStack gui, int mouseX, int mouseY) {
        this.renderLabelsContent(this.renderContext(gui), mouseX, mouseY);
    }

    // -- version-neutral hooks (leaves override these, not the PoseStack-taking methods)
    protected void renderPrologue(RenderContext ctx, int mouseX, int mouseY, float partialTicks) { }

    protected void renderBgContent(RenderContext ctx, float partialTicks, int mouseX, int mouseY) { }

    protected void renderLabelsContent(RenderContext ctx, int mouseX, int mouseY) { }

    protected void renderScreenContent(RenderContext ctx, int mouseX, int mouseY, float partialTicks) { }

    // -- tooltip surface (see CompatScreen.showTooltip)
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
