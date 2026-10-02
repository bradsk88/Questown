package ca.bradj.questown.gui;

import ca.bradj.questown.mc.Compat;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Paged-card container base for questown's screen family (quests, economics, town status).
 * Extends {@link CompatContainerScreen}, so the version-bound entry points and the blurred-world
 * background are the compat base's; the paged surface (page-number strip, arrow buttons, cards)
 * plugs in through the neutral hooks:
 *
 * <ul>
 *   <li>{@link #renderBgContent} — the paged NineNine panel (decomposed from the 1.20.6
 *       {@code renderBg} override);</li>
 *   <li>{@link #renderScreenContent} — the paged {@code afterRender} (page buttons + cards, plus
 *       the card's tooltip) and then {@link #renderScreenExtras}, the leaf's extra content
 *       (tabs, the "all done" state). The leaf extras land after the paged content, matching the
 *       former leaf {@code render} overrides that ran after {@code super.render};</li>
 *   <li>{@link #renderCardContent} — the per-card content, over {@link RenderContext}; the
 *       delegate's {@code CardRenderer} interface takes it directly.</li>
 * </ul>
 *
 * The {@link PagedCardScreen} delegate is version-neutral on its public surface
 * (it takes {@link RenderContext} and crosses to the client API through the record);
 * this base stays the hand-off for the vanilla entry points. One vanilla hook stays
 * version-bound here — the per-frame {@code renderTooltip} override, which forwards to the
 * neutral {@link #renderTooltipContent} (the same hand-off {@code CompatContainerScreen} makes
 * for its hooks) — so the base joins the gate's seam family rather than the grandfather list.
 */
public abstract class AbstractPagedCardScreen<T extends AbstractContainerMenu, D>
        extends CompatContainerScreen<T> implements ScrollableScreen {

    private final PagedCardScreen<D> delegate;
    protected final int cardHeight;

    public AbstractPagedCardScreen(
            T menu,
            Inventory playerInventory,
            Component title
    ) {
        super(menu, playerInventory, title);
        this.delegate = new PagedCardScreen<>(
                () -> height,
                () -> width,
                this::cardsData,
                this::setRenderColorForCard,
                (rc, c, p) -> this.renderCardContent(rc, c, p.x(), p.y()),
                1,
                0,
                0
        );
        this.cardHeight = delegate.cardHeight;
    }

    protected abstract List<Component> renderCardContent(
            RenderContext ctx,
            PagedCardScreen.Card<D> dCard,
            int mouseX,
            int mouseY
    );

    protected abstract void setRenderColorForCard(D d);

    protected abstract ImmutableList<D> cardsData();

    protected Collection<PagedCardScreen.Card<D>> cards() {
        return delegate.cards();
    }

    @Override
    protected void init() {
        super.init();
        this.delegate.afterInit(this::addRenderableWidget);
    }

    @Override
    protected void renderBgContent(RenderContext ctx, float partialTicks, int mouseX, int mouseY) {
        this.delegate.renderBg(ctx, partialTicks, mouseX, mouseY);
    }

    @Override
    protected void renderScreenContent(RenderContext ctx, int mouseX, int mouseY, float partialTicks) {
        renderPagedContent(ctx, mouseX, mouseY, partialTicks);
        this.renderScreenExtras(ctx, mouseX, mouseY, partialTicks);
    }

    /** The paged surface: page-number strip + arrow buttons + cards (and the hovered card's tooltip). */
    protected void renderPagedContent(RenderContext ctx, int mouseX, int mouseY, float partialTicks) {
        @Nullable List<Component> tooltips = this.delegate.afterRender(
                ctx,
                mouseX,
                mouseY,
                partialTicks,
                cardsData(),
                true
        );

        if (tooltips == null || tooltips.isEmpty()) {
            return;
        }

        renderTooltipWithDynamicWrapping(ctx, mouseX, mouseY, tooltips);
    }

    /**
     * The leaf's extra content (tabs, the all-done state), drawn after the paged surface — the
     * same relative position the leaves' former {@code render} overrides had.
     */
    protected void renderScreenExtras(RenderContext ctx, int mouseX, int mouseY, float partialTicks) { }

    // The per-frame tooltip hook: 1.20.6's AbstractContainerScreen calls renderTooltip during its
    // render chain, so leaves keep it as a version-neutral content hook (the render argument only
    // crosses as the render context).
    @Override
    protected void renderTooltip(GuiGraphics gui, int mouseX, int mouseY) {
        this.renderTooltipContent(this.renderContext(gui), mouseX, mouseY);
    }

    protected void renderTooltipContent(RenderContext ctx, int mouseX, int mouseY) { }

    // TODO: Move to RenderUtils for reuse
    private void renderTooltipWithDynamicWrapping(
            RenderContext ctx,
            int mouseX,
            int mouseY,
            List<Component> tooltip
    ) {
        int screenWidthMargin = width - delegate.backgroundWidth();
        int rightMargin = screenWidthMargin / 2;
        int rightEdge = width - rightMargin;
        int widthRightOfCursor = Math.max(rightEdge - mouseX, 80); // Ensure at least 150px for tooltips
        List<FormattedCharSequence> tt = tooltip.stream()
                                                .map(t -> Compat.splitText(ctx.font(), t, widthRightOfCursor))
                                                .flatMap(Collection::stream).toList();
        this.showTooltip(tt);
    }

    @Override
    public boolean mouseScrolled(
            double scrollX,
            double scrollY,
            double scrollDelta
    ) {
        return this.delegate.mouseScrolled(scrollX, scrollY, scrollDelta, this::isMouseOver, (x, y, d) -> false);
    }

    public List<Rect2i> getExtraAreas() {
        return this.delegate.getExtraAreas();
    }

    protected int backgroundHeight() {
        return delegate.backgroundHeight;
    }
}
