package ca.bradj.questown.gui;

import ca.bradj.questown.QT;
import ca.bradj.questown.mc.JEI;
import ca.bradj.questown.mc.Compat;
import com.google.common.collect.ImmutableList;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.common.util.MathUtil;
import mezz.jei.gui.elements.GuiIconButtonSmall;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class AddWorkScreen extends CompatContainerScreen<AddWorkContainer> implements ScrollableScreen {
    private static final int backgroundWidth = 176;
    private static final int backgroundHeight = 166;
    private static final int borderPadding = 6;

    private static final int buttonWidth = 13;
    private static final int buttonHeight = 13;

    private static final int TEXT_COLOR = 0xFF404040;

    private static final int PAGE_PADDING = 10;

    // The requests are drawn as an icon grid, so pages hold grid slots, not cards (r16 #2: a
    // card-sized page of 3 made a 4-item list "Page 1 / 2" with every item on both pages).
    private static final int GRID_COLUMNS = 8;
    private static final int GRID_ROWS = 7;
    private static final int SLOTS_PER_PAGE = GRID_COLUMNS * GRID_ROWS;

    private final List<Ingredient> work;
    private final List<Ingredient> categories;
    private final List<RequestSlot> requestSlots;
    private final JEI.NineNine background;
    private final JEI.NineNine cardBackground;
    private final GuiIconButtonSmall nextPage;
    private final GuiIconButtonSmall previousPage;

    private int currentPage = 0;

    public AddWorkScreen(
            AddWorkContainer container,
            Inventory playerInv,
            Component title
    ) {
        super(container, playerInv, title);
        super.imageWidth = 256;
        super.imageHeight = 220;

        this.categories = container.getCategories();
        this.work = ImmutableList.<Ingredient>builder()
                .addAll(this.categories)
                .addAll(container.getAddableWork())
                .build();
        this.requestSlots = flattenRequests();
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

        x = x + PAGE_PADDING;
        y = y + PAGE_PADDING;

        ImmutableList.Builder<Slot> b = ImmutableList.builder();
        List<RequestSlot> page = Pages.slice(requestSlots, currentPage, SLOTS_PER_PAGE);
        ImmutableList<Slot> slotz = renderRequestItemIcons(ctx, page, x, y, mouseX, mouseY);
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
    private final Map<Slot, Ingredient> slotRequests = new IdentityHashMap<>();

    private boolean isCategory(Ingredient ing) {
        return categories.contains(ing);
    }

    /**
     * One grid slot per request: a category is a single slot that requests the whole tag; a
     * multi-item ingredient gets its own slot followed by one per member.
     */
    private record RequestSlot(Ingredient request, ItemStack[] shown, @Nullable Component categoryName) {
    }

    private List<RequestSlot> flattenRequests() {
        ImmutableList.Builder<RequestSlot> b = ImmutableList.builder();
        for (Ingredient ing : work) {
            ItemStack[] allPossible = ing.getItems();
            if (allPossible.length == 0) {
                continue;
            }
            // A category (a tag: "Food") is one slot that requests the whole category; its members
            // are not listed after it (most of a tag is not something the town can make - the
            // producible ones already have their own slots).
            boolean category = isCategory(ing);
            b.add(new RequestSlot(ing, allPossible, category ? Ingredients.getName(ing) : null));
            if (category || allPossible.length == 1) {
                continue;
            }
            for (ItemStack specificItem : allPossible) {
                b.add(new RequestSlot(Ingredient.of(specificItem), new ItemStack[]{specificItem}, null));
            }
        }
        return b.build();
    }

    private ImmutableList<Slot> renderRequestItemIcons(
            RenderContext ctx,
            List<RequestSlot> page,
            int x,
            int y,
            int mouseX,
            int mouseY
    ) {
        Container dummyInv = new SimpleContainer(SLOTS_PER_PAGE);
        ImmutableList.Builder<Slot> b = ImmutableList.builder();
        slotRequests.clear();
        int curSeconds = (int) (System.currentTimeMillis() / 1000);
        for (int i = 0; i < page.size(); i++) {
            RequestSlot r = page.get(i);
            int iconX = x + 8 + (i % GRID_COLUMNS) * 18;
            int iconY = y + (i / GRID_COLUMNS) * 16;
            ItemStack itemStack = r.shown()[curSeconds % r.shown().length];
            renderRequest(ctx, iconY, mouseX, mouseY, itemStack, iconX, dummyInv, i, b,
                    r.request(), r.categoryName());
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
            Container dummyInv,
            int slotIndex,
            ImmutableList.Builder<Slot> b,
            Ingredient request,
            @Nullable Component categoryName
    ) {
        // No tint on category slots: they come first, so the tint read as a darker first row (r17,
        // dev 2026-10-25). The cycling icon and the category tooltip mark them.
        ctx.renderItemAndDecorations(itemStack, iconX, y + 1);
        if (mouseX >= iconX && mouseY >= y && mouseX < iconX + 16 && mouseY < y + 17) {
            ctx.fill(
                    iconX,
                    y + 1,
                    iconX + 16,
                    y + 17,
                    0x80FFFFFF
            ); // transparent white square behind hovered item slot
            if (categoryName != null) {
                this.showTooltip(List.of(
                        Compat.visualOrder(categoryName),
                        Compat.visualOrder(Compat.translatableStyled(
                                "job_board.add_work.category_hint",
                                net.minecraft.network.chat.Style.EMPTY.withColor(net.minecraft.ChatFormatting.GRAY)
                        ))
                ));
            } else {
                this.showTooltip(itemStack.getItem().getName(itemStack)); // hovered item's name as tooltip
            }
        }
        Slot element = new Slot(dummyInv, slotIndex, iconX, y + 1);
        element.set(itemStack);
        b.add(element);
        slotRequests.put(element, request);
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
        int totalPages = Pages.count(requestSlots.size(), SLOTS_PER_PAGE);
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
        int totalPages = Pages.count(requestSlots.size(), SLOTS_PER_PAGE);
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
                // The request the slot stands for: a category slot requests the category, not the
                // member it happens to be showing.
                Ingredient item = slotRequests.getOrDefault(s, Ingredient.of(s.getItem()));
                if (!menu.canRequest(item)) {
                    QT.logBug("AddWorkScreen: ingredient slot not acceptable: {}", s.getItem());
                    return false;
                }
                menu.sendRequest(item);
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
