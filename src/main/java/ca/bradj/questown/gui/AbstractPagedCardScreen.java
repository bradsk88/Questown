package ca.bradj.questown.gui;

import ca.bradj.questown.mc.Compat;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Paged-card container base for the quests, economics and town-status screens. Through
 * {@link CompatContainerScreen}'s neutral hooks, {@link #renderBgContent} draws the panel,
 * {@link #renderScreenContent} the page strip, arrows and cards (then the leaf's
 * {@link #renderScreenExtras}), and {@link #renderCardContent} each card.
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

    protected int cardsPerPage() {
        return delegate.MAX_CARDS_PER_PAGE;
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
        // Tooltips come from the content pass: 1.20.6's container render never calls a
        // renderTooltip override.
        this.renderTooltipContent(ctx, mouseX, mouseY);
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

    /** The leaf's extra content (tabs, the all-done state), drawn after the paged surface. */
    protected void renderScreenExtras(RenderContext ctx, int mouseX, int mouseY, float partialTicks) { }

    protected void renderTooltipContent(RenderContext ctx, int mouseX, int mouseY) { }

    // TODO: Move to RenderUtils for reuse
    protected void renderTooltipWithDynamicWrapping(
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

    protected void firstPage() {
        delegate.firstPage();
    }

    protected int backgroundHeight() {
        return delegate.backgroundHeight;
    }
}
