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
    // 166 + a 16 px bottom band for the auto-request toggle, which sat on the border (r3 #4).
    private static final int backgroundHeight = 182;
    private static final int borderPadding = 6;

    private static final int buttonWidth = 13;
    private static final int buttonHeight = 13;

    private static final int TEXT_COLOR = 0xFF404040;

    private static final int CARD_PADDING = 1;
    private static final int PAGE_PADDING = 10;
    private static final int CARD_WIDTH = (backgroundWidth) - (PAGE_PADDING * 2);
    private static final int CARD_HEIGHT = 42;

    // Cards start below the page header (two paddings + the first card's padding) and stop above
    // the toggle band (checkbox + border padding + a gap). 13 px keeps 3 cards per page.
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
    private Map<Position, Runnable> removes = new HashMap<>();

    // Wide (position + pixel width) hover/click regions for bottom-band toggles:
    // the auto-request glyph is 9px and the old 16x17 hit box made it nearly
    // impossible to hit - "no tooltip / checkbox doesn't toggle" (2026-10-15
    // playtest). The map key dedupes per frame, same as `removes`.
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
        this.addMoreBtn.setX(x + backgroundWidth - buttonWidth - borderPadding);
        this.addMoreBtn.setY(pageStringY);
        this.addRenderableWidget(this.addMoreBtn);
        this.previousPage.setX(x + borderPadding);
        this.previousPage.setY(pageStringY);
        this.nextPage.setX(x + backgroundWidth - (2 * buttonWidth) - borderPadding);
        this.nextPage.setY(pageStringY);
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
            // Day-1 playtest: an empty board gave no hint at what requests are or how to add
            // one. Reopened 2026-10-12: when the town has a top unmet need, the empty state
            // points at posting it instead of the vague teaser (job-board-empty-state.md).
            Coordinate emptyTop = new Coordinate(x, y + PAGE_PADDING);
            int headlineHeight = ctx.darkTextWrap(emptyTop, backgroundWidth - (PAGE_PADDING * 2),
                    Compat.translatable("job_board.empty.1"));
            // The top_need line no longer names the item (user copy, 2026-10-14) - the
            // name only picks WHICH second line: the production hint when the town has
            // an unmet need, the vague teaser when it doesn't.
            String topNeed = menu.getTopUnmetNeedName();
            Component second = topNeed == null || topNeed.isEmpty()
                    ? Compat.translatable("job_board.empty.2")
                    : Compat.translatable("job_board.empty.top_need");
            // A blank line between the headline and the hint (playtest 2026-10-17: the hint
            // sat 2px under "No work requests yet."). The gap is one wrapped line's step.
            int hintY = emptyTop.y() + headlineHeight + (int) (font.lineHeight * 1.5);
            ctx.darkTextWrap(emptyTop.withY(hintY), backgroundWidth - (PAGE_PADDING * 2),
                    second);
        }

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
            // Name the request ("Raw Food", "Wheat Seeds") instead of "An item was requested".
            ctx.text(Ingredients.getName(jobPosting.getResultWanted().asIngredient()), idX, idY, TEXT_COLOR, false);

            int removeX = idX + CARD_WIDTH - (PAGE_PADDING * 2) - buttonWidth;
            ctx.lightText(
                    "x",
                    removeX + borderPadding - 1,
                    iconY + borderPadding - 1
            );
            highlightAndTooltip(
                    ctx,
                    mouseX,
                    mouseY,
                    removeX,
                    iconY,
                    Compat.translatable("job_board.remove_work")
            );
            this.removes.put(new Position(removeX, iconY), () -> menu.sendRemoveRequest(jobPosting));
        }
        slots.clear();
        slots.addAll(b.build());

        // Render the page buttons
        ctx.renderWidget(this.addMoreBtn, mouseX, mouseY, partialTicks);

        // F2: the board's auto-request toggle, pinned to the bottom band.
        int panelTop = (this.height - backgroundHeight) / 2;
        int tgX = x + PAGE_PADDING;
        int tgY = panelTop + backgroundHeight - borderPadding - CHECKBOX_SIZE;
        boolean on = menu.isBoardAutoRequest();
        Component toggleLabel = Compat.translatable("job_board.auto_request");
        // Hover + click span box AND label - a glyph-only hit box made the tooltip effectively
        // unreachable (2026-10-15 playtest: "It also doesn't appear to have a tooltip").
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
            highlightAndTooltip(ctx, mouseX, mouseY, iconX, y, itemStack.getItem().getName(itemStack));
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
                nextPage.getY(),
                x + backgroundWidth - borderPadding - buttonWidth,
                nextPage.getY() + buttonHeight,
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
        // Flip locally first so the checkbox is instant; the server applies it and re-opens
        // the board, which resyncs the flag state (and this checkbox) from the server.
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
