package ca.bradj.questown.gui;

import ca.bradj.questown.core.Coordinate;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.JEI;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.common.util.MathUtil;
import mezz.jei.gui.elements.GuiIconButtonSmall;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class WorkScreen extends CompatContainerScreen<TownWorkContainer> implements ScrollableScreen {
    private static final int backgroundWidth = 176;
    // The bottom band holds the auto-request toggle.
    private static final int backgroundHeight = 182;
    private static final int borderPadding = 6;

    private static final int buttonWidth = 13;
    private static final int buttonHeight = 13;

    private static final int TEXT_COLOR = 0xFF404040;
    private static final int PAUSED_TEXT_COLOR = 0xFF8B8B8B;

    private static final int CARD_PADDING = 1;
    private static final int PAGE_PADDING = 10;
    private static final int CARD_WIDTH = (backgroundWidth) - (PAGE_PADDING * 2);
    private static final int CARD_HEIGHT = 42;

    // Cards sit between the page header and the toggle band, three per page.
    private static final int CARDS_TOP = PAGE_PADDING * 3;
    private static final int CHECKBOX_SIZE = 13;
    private static final int TOGGLE_BAND = CHECKBOX_SIZE + borderPadding + 4;
    private static final int MAX_CARDS_PER_PAGE =
            (backgroundHeight - CARDS_TOP - TOGGLE_BAND) / (CARD_HEIGHT + CARD_PADDING);

    private final List<UIWork> work;
    private final JEI.NineNine background;
    private final JEI.NineNine cardBackground;
    private final GuiIconButtonSmall nextPage;
    private final GuiIconButtonSmall previousPage;
    private final GuiIconButtonSmall addMoreBtn;

    private final AddWorkScreen addWorkScreen;

    private int currentPage = 0;
    // A - / + click re-opens the board on the same page.
    private static int pageAfterReopen = -1;
    private Map<Position, Runnable> removes = new HashMap<>();

    // Wider hit boxes for the bottom-band toggles; the glyph alone is too small.
    private record WideRegion(int width, Runnable fn) {}

    private Map<Position, WideRegion> wideRegions = new HashMap<>();

    public WorkScreen(
            TownWorkContainer container,
            Inventory playerInv,
            Component title
    ) {
        super(container, playerInv, title);
        super.imageWidth = 256;
        super.imageHeight = 220;

        this.work = ImmutableList.copyOf(container.getWork());
        if (pageAfterReopen >= 0) {
            int lastPage = Math.max(0, (work.size() - 1) / MAX_CARDS_PER_PAGE);
            this.currentPage = Math.min(pageAfterReopen, lastPage);
            pageAfterReopen = -1;
        }
        this.background = JEI.getRecipeGuiBackground();
        this.cardBackground = JEI.getRecipeBackground();

        IDrawableStatic plusIcon = JEI.getPlusIcon();

        int btnX = backgroundWidth - (buttonWidth + borderPadding);

        IDrawableStatic arrowNext = JEI.getArrowNext();
        IDrawableStatic arrowPrevious = JEI.getArrowPrevious();
        this.nextPage = JEI.guiIconButtonSmall(
                0, 0, buttonWidth, buttonHeight, arrowNext, b -> nextPage()
        );
        this.previousPage = JEI.guiIconButtonSmall(
                0, 0, buttonWidth, buttonHeight, arrowPrevious, b -> previousPage()
        );

        addWorkScreen = new AddWorkScreen(menu.addWorkContainer, playerInv, title);
        this.addMoreBtn = JEI.guiIconButtonSmall(
                btnX, 0, buttonWidth, buttonHeight, plusIcon, b -> addMoreWork()
                );
    }

    @Override
    protected void init() {
        int y = (this.height - backgroundHeight) / 2;
        int pageStringY = y + borderPadding;
        int x = ((this.width - backgroundWidth) / 2);
        this.addMoreBtn.x = x + backgroundWidth - buttonWidth - borderPadding;
        this.addMoreBtn.y = pageStringY;
        this.addRenderableWidget(this.addMoreBtn);
        this.previousPage.x = x + borderPadding;
        this.previousPage.y = pageStringY;
        this.nextPage.x = x + backgroundWidth - (2 * buttonWidth) - borderPadding;
        this.nextPage.y = pageStringY;
        this.addRenderableWidget(this.previousPage);
        this.addRenderableWidget(this.nextPage);
        if (getMenu().skipToAdd()) {
            addMoreWork();
            ;
        }
    }


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
        return false;
    }

    @Override
    public boolean keyReleased(
            int keyCode,
            int scanCode,
            int modifiers
    ) {
        if (keyCode == GLFW.GLFW_KEY_Q) { // TODO: Get from user's config

            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderScreenContent(RenderContext ctx, int mouseX, int mouseY, float partialTicks) {
        int x = ((this.width - backgroundWidth) / 2);
        int y = (this.height - backgroundHeight) / 2;
        int pageStringY = y + PAGE_PADDING;

        renderPageNum(ctx, x);

        y = pageStringY + PAGE_PADDING;

        int startIndex = currentPage * MAX_CARDS_PER_PAGE;
        int endIndex = Math.min(startIndex + MAX_CARDS_PER_PAGE, work.size());

        x = x + PAGE_PADDING;
        y = y + PAGE_PADDING;

        if (work.isEmpty()) {
            // An empty board explains requests, and points at the town's top missing item if it has one.
            Coordinate emptyTop = new Coordinate(x, y + PAGE_PADDING);
            int headlineHeight = ctx.darkTextWrap(emptyTop, backgroundWidth - (PAGE_PADDING * 2),
                    Compat.translatable("job_board.empty.1"));
            String topNeed = menu.getTopUnmetNeedName();
            Component second = topNeed == null || topNeed.isEmpty()
                    ? Compat.translatable("job_board.empty.2")
                    : Compat.translatable("job_board.empty.top_need");
            int hintY = emptyTop.y() + headlineHeight + (int) (font.lineHeight * 1.5);
            ctx.darkTextWrap(emptyTop.withY(hintY), backgroundWidth - (PAGE_PADDING * 2),
                    second);
        }

        Compat.resetShaderColor();
        ImmutableList.Builder<Slot> b = ImmutableList.builder();
        for (int i = startIndex; i < endIndex; i++) {
            int row = i - startIndex;
            int cardY = y + row * (CARD_HEIGHT + CARD_PADDING);

            UIWork jobPosting = work.get(i);
            if (jobPosting == null) {
                continue;
            }
            ctx.drawNineNine(this.cardBackground, x, cardY, CARD_WIDTH, CARD_HEIGHT);
            Compat.resetShaderColor();

            int iconY = cardY + CARD_HEIGHT - 24;
            ImmutableList<Slot> slotz = renderJobCardIcons(ctx, jobPosting, x, iconY, mouseX, mouseY);
            b.addAll(slotz);

            int idX = x + PAGE_PADDING;
            int idY = iconY - 10;
            // A paused request (storage holds enough) is greyed.
            int nameColor = jobPosting.isStocked() ? PAUSED_TEXT_COLOR : TEXT_COLOR;
            ctx.text(Ingredients.getName(jobPosting.getResultWanted().asIngredient()), idX, idY, nameColor, false);

            // x top right, - and + bottom right.
            int rightX = idX + CARD_WIDTH - (PAGE_PADDING * 2) - buttonWidth;
            int removeY = idY - borderPadding + 1;
            ctx.lightText(
                    "x",
                    rightX + borderPadding - 1,
                    idY
            );
            highlightAndTooltip(
                    ctx,
                    mouseX,
                    mouseY,
                    rightX,
                    removeY,
                    Compat.translatable("job_board.remove_work")
            );
            this.removes.put(new Position(rightX, removeY), () -> menu.sendRemoveRequest(jobPosting));

            renderStockAndLimit(ctx, jobPosting, rightX, iconY, mouseX, mouseY);
        }
        slots.clear();
        slots.addAll(b.build());

        // Render the page buttons
        ctx.renderWidget(this.addMoreBtn, mouseX, mouseY, partialTicks);

        int panelTop = (this.height - backgroundHeight) / 2;
        int tgX = x + PAGE_PADDING;
        int tgY = panelTop + backgroundHeight - borderPadding - CHECKBOX_SIZE;
        boolean on = menu.isBoardAutoRequest();
        Component toggleLabel = Compat.translatable("job_board.auto_request");
        // The box and the label both take the hover and the click.
        int toggleWidth = CHECKBOX_SIZE + 4 + this.font.width(toggleLabel);
        boolean hovered = mouseX >= tgX && mouseY >= tgY && mouseX < tgX + toggleWidth
                && mouseY < tgY + CHECKBOX_SIZE;
        ctx.checkbox(tgX, tgY, CHECKBOX_SIZE, on, hovered);
        ctx.text(toggleLabel, tgX + CHECKBOX_SIZE + 4, tgY + (CHECKBOX_SIZE - 8) / 2, TEXT_COLOR, false);
        if (hovered) {
            this.showTooltip(
                    Compat.translatable(on ? "job_board.auto_request.tooltip.on" : "job_board.auto_request.tooltip.off")
            );
        }
        wideRegions.put(new Position(tgX, tgY), new WideRegion(toggleWidth, this::toggleAutoRequest));
        ctx.renderWidget(this.previousPage, mouseX, mouseY, partialTicks);
        ctx.renderWidget(this.nextPage, mouseX, mouseY, partialTicks);
    }

    private List<Slot> slots = new ArrayList<>();


    private void sendLimitStep(UIWork jobPosting, int delta) {
        pageAfterReopen = currentPage;
        menu.sendLimitStep(jobPosting, delta);
    }
    private ImmutableList<Slot> renderJobCardIcons(
            RenderContext ctx,
            UIWork recipe,
            int x,
            int y,
            int mouseX,
            int mouseY
    ) {
        Inventory dummyInv = new Inventory(null);
        Ingredient ing = recipe.getResultWanted().asIngredient();
        ImmutableList.Builder<Slot> b = ImmutableList.builder();

        int iconX = x + 8;

        ItemStack itemStack = Ingredients.representativeItemStack(ing);
        if (itemStack != null) {
            ctx.renderItemAndDecorations(itemStack, iconX, y);
            Component name = itemStack.getItem().getName(itemStack);
            Component tooltip = recipe.isStocked()
                    ? Compat.translatable("job_board.request_stocked", Ingredients.getName(ing), recipe.getLimit())
                    : name;
            highlightAndTooltip(ctx, mouseX, mouseY, iconX, y, tooltip);
            Slot element = new Slot(dummyInv, 0, iconX, y + 1);
            element.set(itemStack);
            b.add(element);
        }
        return b.build();
    }

    private void highlightAndTooltip(
            RenderContext ctx,
            int mouseX,
            int mouseY,
            int iconX,
            int iconY,
            Component tooltipText
    ) {
        if (mouseX >= iconX && mouseY >= iconY && mouseX < iconX + 16 && mouseY < iconY + 17) {
            // transparent white square behind hovered item slot
            ctx.fill(iconX, iconY + 1, iconX + 16, iconY + 17, 0x80FFFFFF);
            // render hovered item's name as a tooltip
            this.showTooltip(tooltipText);
        }
    }

    @Override
    public boolean mouseClicked(
            double x,
            double y,
            int p_97750_
    ) {
        for (Map.Entry<Position, WideRegion> w : wideRegions.entrySet()) {
            int buttonX = w.getKey().x;
            int buttonY = w.getKey().z;
            int width = w.getValue().width();
            if (x >= buttonX && y >= buttonY && x < buttonX + width && y < buttonY + CHECKBOX_SIZE) {
                w.getValue().fn().run();
                return true;
            }
        }
        for (Map.Entry<Position, Runnable> p : removes.entrySet()) {
            int buttonX = p.getKey().x;
            int buttonY = p.getKey().z;
            if (x >= buttonX && y >= buttonY && x < buttonX + 16 && y < buttonY + 17) {
                p.getValue().run();
                return true;
            }
        }
        return super.mouseClicked(x, y, p_97750_);
    }

    private void renderPageNum(
            RenderContext ctx,
            int x
    ) {
        // Draw page numbers
        ctx.fill(
                x + borderPadding + buttonWidth,
                nextPage.y,
                x + backgroundWidth - borderPadding - buttonWidth,
                nextPage.y + buttonHeight,
                0x30000000
        );
        int totalPages = Pages.count(work.size(), MAX_CARDS_PER_PAGE);
        String pageString = "Page " + (currentPage + 1) + " / " + totalPages;

        ImmutableRect2i pageArea = MathUtil.union(previousPage.getArea(), nextPage.getArea());
        ImmutableRect2i textArea = MathUtil.centerTextArea(pageArea, this.font, pageString);
        ctx.lightText(pageString, textArea.getX(), textArea.getY());
    }

    @Override
    protected void renderBgContent(RenderContext ctx, float partialTicks, int mouseX, int mouseY) {
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        ctx.drawNineNine(this.background, x, y, backgroundWidth, backgroundHeight);
    }


    private void nextPage() {
        int totalPages = Pages.count(work.size(), MAX_CARDS_PER_PAGE);
        if (currentPage < totalPages - 1) {
            currentPage++;
        }
    }

    private void previousPage() {
        if (currentPage > 0) {
            currentPage--;
        }
    }

    private void addMoreWork() {
        this.minecraft.setScreen(addWorkScreen);
    }

    private void toggleAutoRequest() {
        // Flip at once; the server re-opens the board with the real state.
        boolean next = !menu.isBoardAutoRequest();
        menu.setBoardAutoRequest(next);
        menu.sendToggleBoardAutoRequest();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public List<Rect2i> getExtraAreas() {
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        return ImmutableList.of(
                new Rect2i(x, y, backgroundWidth, backgroundHeight)
        );
    }

    public ItemStack getHoveredIngredient(
            int mouseX,
            int mouseY
    ) {
        Predicate<Slot> slotPredicate = s -> mouseX >= s.x && mouseX <= s.x + 16 && mouseY >= s.y + 1 && mouseY <= s.y + 17;
        Stream<Slot> matches = slots.stream().filter(slotPredicate);
        Optional<Slot> found = matches.findAny();
        return found.map(Slot::getItem).orElse(ItemStack.EMPTY);
    }

    @Override
    public boolean isMouseOver(
            double mouseX,
            double mouseY
    ) {
        return true;
    }
}
