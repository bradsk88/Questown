package ca.bradj.questown.gui;

import ca.bradj.questown.jobs.requests.WorkRequest;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.JEI;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class CreateStockRequestScreen extends CompatContainerScreen<CreateStockRequestContainer> implements ScrollableScreen {
    private static final int backgroundWidth = 176;
    private static final int backgroundHeight = 166;
    private static final int borderPadding = 6;

    private static final int buttonWidth = 13;
    private static final int buttonHeight = 13;

    private static final int TEXT_COLOR = 0xFF404040;

    private static final int CARD_PADDING = 1;
    private static final int PAGE_PADDING = 10;
    private static final int CARD_WIDTH = 18;
    private static final int CARD_HEIGHT = 16;

    // TODO: Update AddWorkScreen to use this logic
    private static final int MAX_CARDS_PER_PAGE = ((backgroundHeight - PAGE_PADDING) / (CARD_HEIGHT + CARD_PADDING)) * (backgroundWidth / CARD_WIDTH);

    private final List<Ingredient> work;
    private final JEI.NineNine background;
    private final JEI.NineNine cardBackground;
    private final GuiIconButtonSmall nextPage;
    private final GuiIconButtonSmall previousPage;

    private int currentPage = 0;

    public CreateStockRequestScreen(
            CreateStockRequestContainer container,
            Inventory playerInv,
            Component title
    ) {
        super(container, playerInv, title);
        super.imageWidth = 256;
        super.imageHeight = 220;

        this.work = ImmutableList.copyOf(container.getAddableWork());
        this.background = JEI.getRecipeGuiBackground();
        this.cardBackground = JEI.getRecipeBackground();

        IDrawableStatic arrowNext = JEI.getArrowNext();
        IDrawableStatic arrowPrevious = JEI.getArrowPrevious();

        this.nextPage = JEI.guiIconButtonSmall(
                0, 0, buttonWidth, buttonHeight, arrowNext, b -> nextPage()
        );
        this.previousPage = JEI.guiIconButtonSmall(
                0, 0, buttonWidth, buttonHeight, arrowPrevious, b -> previousPage()
        );
    }

    @Override
    protected void init() {
        int y = (this.height - backgroundHeight) / 2;
        int pageStringY = y + borderPadding;
        int x = ((this.width - backgroundWidth) / 2);
        this.previousPage.setX(x + borderPadding);
        this.previousPage.setY(pageStringY);
        this.nextPage.setX(x + backgroundWidth - buttonWidth - borderPadding);
        this.nextPage.setY(pageStringY);
        this.addRenderableWidget(this.previousPage);
        this.addRenderableWidget(this.nextPage);
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

        ImmutableList.Builder<Slot> b = ImmutableList.builder();
        ImmutableList<Slot> slotz = renderRequestItemIcons(ctx, work, x, y, mouseX, mouseY);
        b.addAll(slotz);

        int idX = x + PAGE_PADDING;
        int idY = y - 10;
        ctx.text(Compat.translatable("job_board.add_work.title"), idX, idY, TEXT_COLOR, false);
        slots.clear();
        slots.addAll(b.build());

        // Render the page buttons
        ctx.renderWidget(this.previousPage, mouseX, mouseY, partialTicks);
        ctx.renderWidget(this.nextPage, mouseX, mouseY, partialTicks);
    }

    private List<Slot> slots = new ArrayList<>();

    private ImmutableList<Slot> renderRequestItemIcons(
            RenderContext ctx,
            Iterable<Ingredient> possibleRequests,
            int x,
            int y,
            int mouseX,
            int mouseY
    ) {
        Inventory dummyInv = new Inventory(null);
        ImmutableList.Builder<Slot> b = ImmutableList.builder();

        int slotIndex = 0;
        int slotX = 0;
        for (Ingredient ing : possibleRequests) {
            if (slotX >= 8) {
                y = y + 16;
                slotX = 0;
            }
            int iconX = x + 8 + slotX * 18;

            ItemStack[] allPossible = ing.getItems();
            if (allPossible.length > 0) {
                int curSeconds = (int) (System.currentTimeMillis() / 1000);
                ItemStack itemStack = allPossible[curSeconds % allPossible.length];
                renderRequest(ctx, y, mouseX, mouseY, itemStack, iconX, dummyInv, slotIndex, b);
                slotIndex++;
                slotX++;
            }
            if (allPossible.length == 1) {
                continue;
            }
            for (ItemStack specificItem : allPossible) {
                if (slotX >= 8) {
                    y = y + 16;
                    slotX = 0;
                }
                iconX = x + 8 + slotX * 18;
                renderRequest(ctx, y, mouseX, mouseY, specificItem, iconX, dummyInv, slotIndex, b);
                slotIndex++;
                slotX++;
            }
        }
        return b.build();
    }

    private void renderRequest(
            RenderContext ctx,
            int y,
            int mouseX,
            int mouseY,
            ItemStack itemStack,
            int iconX,
            Inventory dummyInv,
            int slotIndex,
            ImmutableList.Builder<Slot> b
    ) {
        ctx.renderItemAndDecorations(itemStack, iconX, y + 1);
        if (mouseX >= iconX && mouseY >= y && mouseX < iconX + 16 && mouseY < y + 17) {
            ctx.fill(
                    iconX,
                    y + 1,
                    iconX + 16,
                    y + 17,
                    0x80FFFFFF
            ); // transparent white square behind hovered item slot
            this.showTooltip(itemStack.getItem().getName(itemStack)); // hovered item's name as tooltip
        }
        Slot element = new Slot(dummyInv, slotIndex, iconX, y + 1);
        element.set(itemStack);
        b.add(element);
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
    public boolean mouseClicked(
            double x,
            double y,
            int p_97750_
    ) {
        for (Slot s : slots) {
            if (s.x < x && s.x + 16 > x && s.y < y && s.y + 16 > y) {
                menu.sendRequest(WorkRequest.of(s.getItem().getItem()), minecraft.player.getUUID());
                return true;
            }
        }
        return super.mouseClicked(x, y, p_97750_);
    }

    @Override
    public boolean isMouseOver(
            double mouseX,
            double mouseY
    ) {
        return true;
    }
}
