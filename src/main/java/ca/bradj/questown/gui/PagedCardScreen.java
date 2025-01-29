package ca.bradj.questown.gui;

import ca.bradj.questown.core.UtilClean.Pair;
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
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.Rect2i;
import org.apache.logging.log4j.util.TriConsumer;
import org.jetbrains.annotations.NotNull;
import vazkii.patchouli.api.TriPredicate;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class PagedCardScreen<D> {

    protected static final int backgroundWidth = 176;
    public final int backgroundHeight;
    protected static final int borderPadding = 6;
    protected static final int buttonWidth = 13;
    protected static final int SMALL_PADDING = 1;
    protected static final int MED_PADDING = 5;
    protected static final int BIG_PADDING = 10;
    protected static final int UNSCALED_CARD_HEIGHT = 42;
    protected static final int CARD_WIDTH = (backgroundWidth) - (BIG_PADDING * 2);
    public static final int buttonHeight = 13;
    private final Supplier<Integer> height;
    private final Supplier<Integer> width;
    private final DrawableNineSliceTexture background;
    final GuiIconButtonSmall nextPage;
    final GuiIconButtonSmall previousPage;
    private final JEI.NineNine cardBackground;
    private final Consumer<D> setRenderColorForCard;
    private final TriConsumer<PoseStack, Card<D>, Pair<Integer, Integer>> renderCardContent;
    private final Supplier<List<D>> cardsData;
    private final int MAX_CARDS_PER_PAGE;
    public final int cardHeight;
    private final int buttonY;
    private int currentPage = 0;

    public PagedCardScreen(
            Supplier<Integer> height,
            Supplier<Integer> width,
            Supplier<List<D>> cardsData,
            Consumer<D> setRenderColorForCard,
            TriConsumer<PoseStack, Card<D>, Pair<Integer, Integer>> renderCardContent,
            int heightScale,
            int buttonY,
            int extraHeight
    ) {
        this.height = height;
        this.width = width;

        this.backgroundHeight = 166 + extraHeight;

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

        this.cardsData = cardsData;
        this.setRenderColorForCard = setRenderColorForCard;
        this.renderCardContent = renderCardContent;
        this.cardHeight = UNSCALED_CARD_HEIGHT * heightScale;
        this.MAX_CARDS_PER_PAGE = (backgroundHeight - BIG_PADDING) / (cardHeight + SMALL_PADDING);
        this.buttonY = buttonY;
    }

    public @NotNull <D> ImmutableList<Card<D>> getCardLayout(
            int screenWidth,
            int screenHeight,
            int bgWidth,
            int bgHeight,
            int currentPage,
            List<D> cardsData
    ) {
        int bgX = (screenWidth - bgWidth) / 2;
        int bgY = (screenHeight - bgHeight) / 2;
        int x = bgX;
        int y = bgY;
        int pageStringY = y + BIG_PADDING;
        y = pageStringY + BIG_PADDING;
        int MAX_CARDS_PER_PAGE = (bgHeight - BIG_PADDING) / (cardHeight + SMALL_PADDING);

        int startIndex = currentPage * MAX_CARDS_PER_PAGE;
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
                    cardY + cardHeight,
                    cardY + cardHeight - BIG_PADDING
            );
            b.add(new Card<>(i, coords, data));
        }
        return b.build();
    }

    private int getCardY(
            int y,
            int row
    ) {
        return y + row * (cardHeight + SMALL_PADDING);
    }

    protected void afterInit(
            Consumer<GuiIconButtonSmall> addRenderableWidget
    ) {
        int y = (this.height.get() - backgroundHeight) / 2;
        int pageStringY = y + borderPadding;
        int x = ((this.width.get() - backgroundWidth) / 2);
        this.previousPage.x = x + borderPadding;
        this.previousPage.y = pageStringY + buttonY;
        this.nextPage.x = x + backgroundWidth - buttonWidth - borderPadding;
        this.nextPage.y = pageStringY + buttonY;
        addRenderableWidget.accept(this.previousPage);
        addRenderableWidget.accept(this.nextPage);
    }

    protected void renderBg(
            PoseStack stack,
            float v,
            int i,
            int i1
    ) {
        int bgX = (this.width.get() - backgroundWidth) / 2;
        int bgY = (this.height.get() - backgroundHeight) / 2;
        this.background.draw(stack, bgX, bgY, backgroundWidth, backgroundHeight);
    }

    public void afterRender(
            PoseStack poseStack,
            Font font,
            int mouseX,
            int mouseY,
            float partialTicks,
            List<D> cardsData,
            boolean drawCardBg
    ) {
        renderButtons(poseStack, font, mouseX, mouseY, partialTicks, cardsData);
        renderCards(poseStack, mouseX, mouseY, drawCardBg);
    }

    public void renderButtons(
            PoseStack poseStack,
            Font font,
            int mouseX,
            int mouseY,
            float partialTicks,
            List<D> cardsData
    ) {
        int bgX = (this.width.get() - backgroundWidth) / 2;
        renderPageNum(
                poseStack, font, bgX, cardsData, (s, pos, size) -> GuiComponent.fill(
                        poseStack, pos.a(), pos.b(), size.a(), size.b(), 0x30000000
                )
        );

        // Render the page buttons
        this.previousPage.render(poseStack, mouseX, mouseY, partialTicks);
        this.nextPage.render(poseStack, mouseX, mouseY, partialTicks);
    }

    private void renderCards(
            PoseStack poseStack,
            int mouseX,
            int mouseY,
            boolean drawCardBg
    ) {
        cards().forEach(card -> {
            if (drawCardBg) {
                setRenderColorForCard.accept(card.data());
                int x2 = card.coords().leftX();
                int y2 = card.coords.topY();
                this.cardBackground.draw(poseStack, x2, y2, CARD_WIDTH, cardHeight);
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            }
            renderCardContent.accept(poseStack, card, new Pair<>(mouseX, mouseY));
        });
    }

    public Iterable<Card<D>> cards() {
        return getCardLayout(
                width.get(),
                height.get(),
                backgroundWidth,
                backgroundHeight,
                currentPage,
                this.cardsData.get()
        );
    }

    private void renderPageNum(
            PoseStack poseStack,
            Font font,
            int x,
            List<D> cardsData,
            TriConsumer<PoseStack, Pair<Integer, Integer>, Pair<Integer, Integer>> fill
    ) {
        // Draw page numbers
        int xx = x + borderPadding + buttonWidth;
        int y = nextPage.y;
        int width = xx + backgroundWidth - borderPadding - buttonWidth;
        int height = nextPage.y + buttonHeight;
        fill.accept(
                poseStack,
                new Pair<>(xx, y),
                new Pair<>(width, height)
        );
        int totalPages = (int) Math.ceil((double) cardsData.size() / MAX_CARDS_PER_PAGE);
        String pageString = "Page " + (currentPage + 1) + " / " + totalPages;

        ImmutableRect2i pageArea = MathUtil.union(previousPage.getArea(), nextPage.getArea());
        ImmutableRect2i textArea = MathUtil.centerTextArea(pageArea, font, pageString);
        Compat.drawLightText(font, poseStack, pageString, textArea.getX(), textArea.getY());
    }

    private void nextPage() {
        int totalPages = (int) Math.ceil((double) cardsData.get().size() / MAX_CARDS_PER_PAGE);
        if (currentPage < totalPages - 1) {
            currentPage++;
        }
    }

    private void previousPage() {
        if (currentPage > 0) {
            currentPage--;
        }
    }

    public boolean mouseScrolled(
            double scrollX,
            double scrollY,
            double scrollDelta,
            BiPredicate<Double, Double> isMouseOver,
            TriPredicate<Double, Double, Double> mouseScrolled
    ) {
        final double x = JEI.getX();
        final double y = JEI.getY();
        if (isMouseOver.test(x, y)) {
            if (scrollDelta < 0) {
                this.nextPage();
                return true;
            } else if (scrollDelta > 0) {
                this.previousPage();
                return true;
            }
        }
        return mouseScrolled.test(scrollX, scrollY, scrollDelta);
    }

    public List<Rect2i> getExtraAreas() {
        int x = (this.width.get() - backgroundWidth) / 2;
        int y = (this.height.get() - backgroundHeight) / 2;
        return ImmutableList.of(
                new Rect2i(x, y, backgroundWidth, backgroundHeight)
        );
    }

    public record Card<D>(
            int index,
            CardCoordinates coords,
            D data
    ) {
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

        public CardCoordinates shiftedDown(int i) {
            return new CardCoordinates(
                    leftX,
                    leftXPadded,
                    topY + i,
                    topYPadded + i,
                    rightX,
                    rightXPadded,
                    bottomY,
                    bottomYPadded
            );
        }
    }
}
