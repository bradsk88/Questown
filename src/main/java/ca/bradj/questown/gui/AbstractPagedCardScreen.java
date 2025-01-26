package ca.bradj.questown.gui;

import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.JEI;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.Internal;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.common.util.MathUtil;
import mezz.jei.gui.elements.DrawableNineSliceTexture;
import mezz.jei.gui.elements.GuiIconButtonSmall;
import mezz.jei.gui.textures.Textures;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Supplier;

public abstract class AbstractPagedCardScreen<T extends AbstractContainerMenu, D> extends AbstractContainerScreen<T> {
    protected static final int backgroundWidth = 176;
    protected static final int backgroundHeight = 166;
    protected static final int borderPadding = 6;

    protected static final int buttonWidth = 13;
    private static final int buttonHeight = 13;
    private final DrawableNineSliceTexture background;
    private final GuiIconButtonSmall nextPage;
    private final GuiIconButtonSmall previousPage;
    private final JEI.NineNine cardBackground;
    private int currentPage = 0;

    protected static final int SMALL_PADDING = 1;
    protected static final int MED_PADDING = 5;
    protected static final int BIG_PADDING = 10;
    protected static final int CARD_HEIGHT = 42;
    protected static final int CARD_WIDTH = (backgroundWidth) - (BIG_PADDING * 2);
    private static final int MAX_CARDS_PER_PAGE = (backgroundHeight - BIG_PADDING) / (CARD_HEIGHT + SMALL_PADDING);

    public AbstractPagedCardScreen(
            T p_97741_,
            Inventory p_97742_,
            Component p_97743_
    ) {
        super(p_97741_, p_97742_, p_97743_);
        Textures textures = Internal.getTextures();
        this.background = textures.getRecipeGuiBackground();
        IDrawableStatic arrowNext = JEI.getArrowNext();
        IDrawableStatic arrowPrevious = JEI.getArrowPrevious();

        this.nextPage = JEI.guiIconButtonSmall(
                0, 0, buttonWidth, buttonHeight, arrowNext, b -> nextPage()
        );
        this.previousPage = JEI.guiIconButtonSmall(
                0, 0, buttonWidth, buttonHeight, arrowPrevious, b -> previousPage()
        );
        this.cardBackground = JEI.getRecipeBackground();
    }

    @Override
    protected void init() {
        super.init();
        int y = (this.height - backgroundHeight) / 2;
        int pageStringY = y + borderPadding;
        int x = ((this.width - backgroundWidth) / 2);
        this.previousPage.x = x + borderPadding;
        this.previousPage.y = pageStringY;
        this.nextPage.x = x + backgroundWidth - buttonWidth - borderPadding;
        this.nextPage.y = pageStringY;
        this.addRenderableWidget(this.previousPage);
        this.addRenderableWidget(this.nextPage);
    }

    @Override
    protected void renderBg(
            PoseStack stack,
            float v,
            int i,
            int i1
    ) {
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - backgroundHeight) / 2;
        this.background.draw(stack, bgX, bgY, backgroundWidth, backgroundHeight);
    }

    public record Card<D>(
            int index,
            CardCoordinates coords,
            D data
    ) {
    }

    @Override
    public void render(
            PoseStack poseStack,
            int mouseX,
            int mouseY,
            float partialTicks
    ) {
        super.render(poseStack, mouseX, mouseY, partialTicks);

        int bgX = (this.width - backgroundWidth) / 2;
        renderPageNum(poseStack, bgX);

        cards().forEach(card -> {
            setRenderColorForCard(card.data());
            int x2 = card.coords().leftX();
            int y2 = card.coords.topY();
            this.cardBackground.draw(poseStack, x2, y2, CARD_WIDTH, CARD_HEIGHT);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            renderCardContent(poseStack, card, mouseX, mouseY);
        });

        // Render the page buttons
        this.previousPage.render(poseStack, mouseX, mouseY, partialTicks);
        this.nextPage.render(poseStack, mouseX, mouseY, partialTicks);
        this.renderTooltip(poseStack, mouseX, mouseY);
    }

    protected Iterable<Card<D>> cards() {
        return getCardLayout(width, height, backgroundWidth, backgroundHeight, currentPage, this::cardsData);
    }

    public static @NotNull <D> ImmutableList<Card<D>> getCardLayout(
            int screenWidth,
            int screenHeight,
            int bgWidth,
            int bgHeight,
            int currentPage,
            Supplier<? extends List<D>> cardsDataFn
    ) {
        int bgX = (screenWidth - bgWidth) / 2;
        int bgY = (screenHeight - bgHeight) / 2;
        int x = bgX;
        int y = bgY;
        int pageStringY = y + BIG_PADDING;
        y = pageStringY + BIG_PADDING;
        int MAX_CARDS_PER_PAGE = (bgHeight - BIG_PADDING) / (CARD_HEIGHT + SMALL_PADDING);

        int startIndex = currentPage * MAX_CARDS_PER_PAGE;
        List<D> cardsData = cardsDataFn.get();
        int endIndex = Math.min(startIndex + MAX_CARDS_PER_PAGE, cardsData.size());

        x = x + BIG_PADDING;
        y = y + BIG_PADDING;

        ImmutableList.Builder<Card<D>> b = ImmutableList.builder();
        for (int i = startIndex; i < endIndex; i++) {
            int row = i - startIndex;
            int cardY = getCardY(y, row);
            D data = cardsData.get(i);
            CardCoordinates coords = new CardCoordinates(
                    x,
                    x + BIG_PADDING,
                    cardY,
                    cardY + BIG_PADDING,
                    x + CARD_WIDTH,
                    x + CARD_WIDTH - BIG_PADDING,
                    cardY + CARD_HEIGHT,
                    cardY + CARD_HEIGHT - BIG_PADDING
            );
            b.add(new Card<>(i, coords, data));
        }
        return b.build();
    }

    private static int getCardY(
            int y,
            int row
    ) {
        return y + row * (CARD_HEIGHT + SMALL_PADDING);
    }

    private void renderPageNum(
            PoseStack poseStack,
            int x
    ) {
        // Draw page numbers
        fill(
                poseStack,
                x + borderPadding + buttonWidth,
                nextPage.y,
                x + backgroundWidth - borderPadding - buttonWidth,
                nextPage.y + buttonHeight,
                0x30000000
        );
        int totalPages = (int) Math.ceil((double) cardsData().size() / MAX_CARDS_PER_PAGE);
        String pageString = "Page " + (currentPage + 1) + " / " + totalPages;

        ImmutableRect2i pageArea = MathUtil.union(previousPage.getArea(), nextPage.getArea());
        ImmutableRect2i textArea = MathUtil.centerTextArea(pageArea, font, pageString);
        Compat.drawLightText(font, poseStack, pageString, textArea.getX(), textArea.getY());
    }

    public record CardCoordinates(
            int leftX,
            int leftXPadded,
            int topY,
            int topYPadded,
            int rightX,
            int rightXPadded,
            int bottomY,
            int bottomYPadded
    ) {
        public CardCoordinates shiftedUp(int i) {
            return new CardCoordinates(
                    leftX,
                    leftXPadded,
                    topY - i,
                    topYPadded - i,
                    rightX,
                    rightXPadded,
                    bottomY,
                    bottomYPadded
            );
        }

        public CardCoordinates shiftedDown() {
            return new CardCoordinates(
                    leftX,
                    leftXPadded,
                    topY + borderPadding,
                    topYPadded + borderPadding,
                    rightX,
                    rightXPadded,
                    bottomY,
                    bottomYPadded
            );
        }
    }

    protected abstract void renderCardContent(
            PoseStack poseStack,
            Card<D> card,
            int mouseX,
            int mouseY
    );

    protected void setRenderColorForCard(D data) {
    }

    private void nextPage() {
        int totalPages = (int) Math.ceil((double) cardsData().size() / MAX_CARDS_PER_PAGE);
        if (currentPage < totalPages - 1) {
            currentPage++;
        }
    }

    private void previousPage() {
        if (currentPage > 0) {
            currentPage--;
        }
    }

    @Override
    public boolean mouseScrolled(
            double scrollX,
            double scrollY,
            double scrollDelta
    ) {
        final double x = JEI.getX();
        final double y = JEI.getY();
        if (isMouseOver(x, y)) {
            if (scrollDelta < 0) {
                this.nextPage();
                return true;
            } else if (scrollDelta > 0) {
                this.previousPage();
                return true;
            }
        }
        return super.mouseScrolled(scrollX, scrollY, scrollDelta);
    }

    public List<Rect2i> getExtraAreas() {
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        return ImmutableList.of(
                new Rect2i(x, y, backgroundWidth, backgroundHeight)
        );
    }

    protected abstract ImmutableList<D> cardsData();
}
