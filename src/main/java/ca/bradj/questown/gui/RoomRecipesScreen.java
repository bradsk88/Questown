package ca.bradj.questown.gui;

import ca.bradj.roomrecipes.recipes.RoomRecipe;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.Internal;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.gui.elements.DrawableNineSliceTexture;
import mezz.jei.gui.elements.GuiIconButtonSmall;
import mezz.jei.gui.textures.Textures;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class RoomRecipesScreen extends AbstractContainerScreen<TownQuestsContainer> {
    private static final int backgroundWidth = 176;
    private static final int backgroundHeight = 166;

    private static final int buttonWidth = 13;
    private static final int buttonHeight = 13;

    private static final int CARD_PADDING = 10;
    private static final int CARD_WIDTH = (backgroundWidth) - (CARD_PADDING * 2);
    private static final int CARD_HEIGHT = 32;

    private static final int MAX_CARDS_PER_PAGE = (backgroundHeight - CARD_PADDING) / (CARD_HEIGHT + CARD_PADDING);

    private final List<RoomRecipe> quests;
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
        int pageStringY = y + CARD_PADDING;
        int x = ((this.width - backgroundWidth) / 2);
        this.previousPage.x = x;
        this.previousPage.y = pageStringY;
        this.nextPage.x = x + backgroundWidth - buttonWidth;
        this.nextPage.y = pageStringY;
        this.addRenderableWidget(this.previousPage);
        this.addRenderableWidget(this.nextPage);
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

        int y = (this.height - backgroundHeight) / 2;

        // Draw page numbers
        int totalPages = (int) Math.ceil((double) quests.size() / MAX_CARDS_PER_PAGE);
        String pageString = "Page " + (currentPage + 1) + " / " + totalPages;
        int pageStringWidth = this.font.width(pageString);
        int pageStringX = (this.width - pageStringWidth) / 2;
        int pageStringY = y + CARD_PADDING;
        this.font.draw(poseStack, pageString, pageStringX, pageStringY, 0x404040);

        y = pageStringY + CARD_PADDING;

        int startIndex = currentPage * MAX_CARDS_PER_PAGE;
        int endIndex = Math.min(startIndex + MAX_CARDS_PER_PAGE, quests.size());

        Inventory dummyInv = new Inventory(null);

        int x = ((this.width - backgroundWidth) / 2);
        x = x + CARD_PADDING;
        y = y + CARD_PADDING;

        for (int i = startIndex; i < endIndex; i++) {
            int row = i - startIndex;
            int cardY = y + row * (CARD_HEIGHT + CARD_PADDING);

            this.cardBackground.draw(poseStack, x, cardY, CARD_WIDTH, CARD_HEIGHT);

            RoomRecipe recipe = quests.get(i);
            if (recipe != null) {
                for (int j = 0; j < recipe.getIngredients().size(); j++) {
                    int iconX = x + 8 + j * 18;
                    int iconY = cardY + CARD_HEIGHT - 24;

                    Slot slot = new Slot(dummyInv, j, iconX, iconY);
                    slot.set(recipe.getIngredients().get(j).getItems()[0]);
                    this.renderSlot(poseStack, slot, mouseX, mouseY, partialTicks);
                }
            }
        }

        // Render the page buttons
        this.previousPage.render(poseStack, mouseX, mouseY, partialTicks);
        this.nextPage.render(poseStack, mouseX, mouseY, partialTicks);
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
}