package ca.bradj.questown.gui;

import ca.bradj.questown.mc.Compat;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public abstract class AbstractPagedCardScreen<T extends AbstractContainerMenu, D> extends AbstractContainerScreen<T> {

    private final PagedCardScreen<D> delegate;
    protected final int cardHeight;

    public AbstractPagedCardScreen(
            T p_97741_,
            Inventory p_97742_,
            Component p_97743_
    ) {
        super(p_97741_, p_97742_, p_97743_);
        this.delegate = new PagedCardScreen<>(
                () -> height,
                () -> width,
                this::cardsData,
                this::setRenderColorForCard,
                (s, c, p) -> this.renderCardContent(s, c, p.x(), p.y()),
                1,
                0,
                0
        );
        this.cardHeight = delegate.cardHeight;
    }

    protected abstract List<Component> renderCardContent(
            PoseStack stack,
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
    protected void renderBg(
            PoseStack poseStack,
            float v,
            int i,
            int i1
    ) {
        this.delegate.renderBg(poseStack, v, i, i1);
    }

    @Override
    public void render(
            PoseStack p_97795_,
            int p_97796_,
            int p_97797_,
            float p_97798_
    ) {
        super.render(p_97795_, p_97796_, p_97797_, p_97798_);
        @Nullable List<Component> tooltips = this.delegate.afterRender(
                p_97795_,
                font,
                p_97796_,
                p_97797_,
                p_97798_,
                cardsData(),
                true
        );

        if (tooltips == null || tooltips.isEmpty()) {
            return;
        }

        renderTooltipWithDynamicWrapping(p_97795_, p_97796_, p_97797_, tooltips);
    }

    // TODO: Move to RenderUtils for reuse
    private void renderTooltipWithDynamicWrapping(
            PoseStack stack,
            int mouseX,
            int mouseY,
            List<Component> tooltip
    ) {
        int screenWidthMargin = width - delegate.backgroundWidth();
        int rightMargin = screenWidthMargin / 2;
        int rightEdge = width - rightMargin;
        int widthRightOfCursor = Math.max(rightEdge - mouseX, 80); // Ensure at least 150px for tooltips
        List<FormattedCharSequence> tt = tooltip.stream().map(t -> Compat.splitText(font, t, widthRightOfCursor))
                                                .flatMap(Collection::stream).toList();
        renderTooltip(stack, tt, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(
            double p_94686_,
            double p_94687_,
            double p_94688_
    ) {
        return this.delegate.mouseScrolled(p_94686_, p_94687_, p_94688_, this::isMouseOver, super::mouseScrolled);
    }

    public List<Rect2i> getExtraAreas() {
        return this.delegate.getExtraAreas();
    }

    protected int backgroundHeight() {
        return delegate.backgroundHeight;
    }
}
