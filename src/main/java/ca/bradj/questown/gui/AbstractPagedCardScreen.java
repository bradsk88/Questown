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

/** Base for the paged-card screens: quests, economics and town status. */
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
        this.renderTooltipContent(ctx, mouseX, mouseY);
    }

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

    /** Drawn after the cards: tabs, the all-done state. */
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
