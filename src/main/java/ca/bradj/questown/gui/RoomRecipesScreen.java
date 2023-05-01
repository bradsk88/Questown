package ca.bradj.questown.gui;

import ca.bradj.questown.logic.RoomRecipes;
import ca.bradj.roomrecipes.recipes.RoomRecipe;
import com.google.common.collect.ImmutableList;
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
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class RoomRecipesScreen extends AbstractContainerScreen<TownQuestsContainer> {
    private static final int backgroundWidth = 176;
    private static final int backgroundHeight = 166;
    private static final int borderPadding = 6;

    private static final int buttonWidth = 13;
    private static final int buttonHeight = 13;

    private static final int TEXT_COLOR = 0x404040;

    private static final int CARD_PADDING = 1;
    private static final int PAGE_PADDING = 10;
    private static final int CARD_WIDTH = (backgroundWidth) - (PAGE_PADDING * 2);
    private static final int CARD_HEIGHT = 42;

    private static final int MAX_CARDS_PER_PAGE = (backgroundHeight - PAGE_PADDING) / (CARD_HEIGHT + CARD_PADDING);

    private final List<UIQuest> quests;
    private final DrawableNineSliceTexture background;
    private final DrawableNineSliceTexture cardBackground;
    private final GuiIconButtonSmall nextPage;
    private final GuiIconButtonSmall previousPage;

    private int currentPage = 0;

    public RoomRecipesScreen(
            TownQuestsContainer container,
            Inventory playerInv,
            Component title
    ) {
        super(container, playerInv, title);
        super.imageWidth = 256;
        super.imageHeight = 220;

        this.quests = ImmutableList.copyOf(container.GetQuests());
        Textures textures = Internal.getTextures();
        this.background = textures.getRecipeGuiBackground();
        this.cardBackground = textures.getRecipeBackground();

        IDrawableStatic arrowNext = textures.getArrowNext();
        IDrawableStatic arrowPrevious = textures.getArrowPrevious();

        this.nextPage = new GuiIconButtonSmall(
                0, 0, buttonWidth, buttonHeight, arrowNext, b -> nextPage()
        );
        this.previousPage = new GuiIconButtonSmall(
                0, 0, buttonWidth, buttonHeight, arrowPrevious, b -> previousPage()
        );

    }

    @Override
    protected void init() {
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
    public boolean keyReleased(
            int keyCode, int scanCode, int modifiers
    ) {
        if (keyCode == GLFW.GLFW_KEY_Q) { // TODO: Get from user's config

            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(
            PoseStack poseStack,
            int mouseX,
            int mouseY,
            float partialTicks
    ) {
        this.renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTicks);

        int x = ((this.width - backgroundWidth) / 2);
        int y = (this.height - backgroundHeight) / 2;
        int pageStringY = y + PAGE_PADDING;

        renderPageNum(poseStack, x);

        y = pageStringY + PAGE_PADDING;

        int startIndex = currentPage * MAX_CARDS_PER_PAGE;
        int endIndex = Math.min(startIndex + MAX_CARDS_PER_PAGE, quests.size());

        x = x + PAGE_PADDING;
        y = y + PAGE_PADDING;

        ImmutableList.Builder<Slot> b = ImmutableList.builder();
        for (int i = startIndex; i < endIndex; i++) {
            int row = i - startIndex;
            int cardY = y + row * (CARD_HEIGHT + CARD_PADDING);

            this.cardBackground.draw(poseStack, x, cardY, CARD_WIDTH, CARD_HEIGHT);

            UIQuest recipe = quests.get(i);
            if (recipe != null) {
                int iconY = cardY + CARD_HEIGHT - 24;
                ImmutableList<Slot> slotz = renderRecipeCard(poseStack, recipe, x, iconY, mouseX, mouseY, partialTicks);
                b.addAll(slotz);
            }
        }
        slots.clear();
        slots.addAll(b.build());

        // Render the page buttons
        this.previousPage.render(poseStack, mouseX, mouseY, partialTicks);
        this.nextPage.render(poseStack, mouseX, mouseY, partialTicks);
    }

    private int lastRenderTick = 0;
    private int currentItemIndex = 0;

    private List<Slot> slots = new ArrayList<>();

    private ImmutableList<Slot> renderRecipeCard(
            PoseStack poseStack,
            UIQuest recipe,
            int x, int y, int mouseX, int mouseY,
            float partialTicks
    ) {
        Inventory dummyInv = new Inventory(null);
        Collection<Ingredient> ingredients = recipe.getIngredients();
        ingredients = RoomRecipes.filterSpecialBlocks
                (ingredients);
        int j = 0;

        ImmutableList.Builder<Slot> b = ImmutableList.builder();

        for (Ingredient ing : ingredients) {
            int iconX = x + 8 + j * 18;

            ItemStack[] matchingStacks = ing.getItems();
            if (matchingStacks.length > 0) {
                int curSeconds = (int) (System.currentTimeMillis() / 1000);
                ItemStack itemStack = matchingStacks[curSeconds % matchingStacks.length];
                this.itemRenderer.renderAndDecorateItem(itemStack, iconX, y + 1);
                if (mouseX >= iconX && mouseY >= y && mouseX < iconX + 16 && mouseY < y + 17) {
                    fill(poseStack, iconX, y + 1, iconX + 16, y + 17, 0x80FFFFFF); // transparent white square behind hovered item slot
                    renderTooltip(poseStack, itemStack.getItem().getName(itemStack), mouseX, mouseY); // render hovered item's name as a tooltip
                }
                Slot element = new Slot(dummyInv, j, iconX, y + 1);
                element.set(itemStack);
                b.add(element);
            }
            j++;
        }

        int idX = x + PAGE_PADDING;
        int idY = y - 10;
        this.font.draw(poseStack, recipe.getName().getString(), idX, idY, TEXT_COLOR);
        return b.build();
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
                0x30000000);
        int totalPages = (int) Math.ceil((double) quests.size() / MAX_CARDS_PER_PAGE);
        String pageString = "Page " + (currentPage + 1) + " / " + totalPages;

        ImmutableRect2i pageArea = MathUtil.union(previousPage.getArea(), nextPage.getArea());
        ImmutableRect2i textArea = MathUtil.centerTextArea(pageArea, font, pageString);
        font.drawShadow(poseStack, pageString, textArea.getX(), textArea.getY(), 0xFFFFFFFF);
    }

    @Override
    protected void renderBg(
            PoseStack poseStack,
            float partialTicks,
            int mouseX,
            int mouseY
    ) {
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        this.background.draw(poseStack, x, y, backgroundWidth, backgroundHeight);
    }

    private void renderSlot(
            PoseStack poseStack,
            Slot slot,
            int mouseX,
            int mouseY,
            float partialTicks
    ) {
        ItemStack stack = slot.getItem();
        if (!stack.isEmpty()) {
            this.minecraft.getItemRenderer().renderGuiItem(stack, slot.x, slot.y);
            this.minecraft.getItemRenderer().renderGuiItemDecorations(this.font, stack, slot.x, slot.y, "");
        }
    }

    private void nextPage() {
        int totalPages = (int) Math.ceil((double) quests.size() / MAX_CARDS_PER_PAGE);
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

    public ItemStack getHoveredIngredient(int mouseX, int mouseY) {
        Predicate<Slot> slotPredicate = s -> mouseX >= s.x && mouseX <= s.x + 16 && mouseY >= s.y + 1 && mouseY <= s.y + 17;
        Stream<Slot> matches = slots.stream().filter(slotPredicate);
        Optional<Slot> found = matches.findAny();
        return found.map(Slot::getItem).orElse(ItemStack.EMPTY);
    }
}