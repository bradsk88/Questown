package ca.bradj.questown.gui;

import ca.bradj.roomrecipes.recipes.RoomRecipe;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class RoomRecipesScreen extends AbstractContainerScreen<TownQuestsContainer> {
    private static final int backgroundWidth = 176;
    private static final int backgroundHeight = 166;

        private static final int CARD_WIDTH = 150;
    private static final int CARD_HEIGHT = 80;
    private static final int CARD_PADDING = 10;

    private final ResourceLocation BACKGROUND_TEXTURE = InventoryMenu.BLOCK_ATLAS;
    private final List<RoomRecipe> quests;

    public RoomRecipesScreen(TownQuestsContainer container, Inventory playerInv, Component title) {
        super(container, playerInv, title);
        this.quests = ImmutableList.copyOf(container.GetQuests());
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTicks);

        int x = (this.width - (CARD_WIDTH + CARD_PADDING) * 2) / 2;
        int y = (this.height - (CARD_HEIGHT + CARD_PADDING) * 3) / 2;

        Inventory dummyInv = new Inventory(null);

        for (int i = 0; i < quests.size(); i++) {
            int col = i % 2;
            int row = i / 2;
            int cardX = x + col * (CARD_WIDTH + CARD_PADDING);
            int cardY = y + row * (CARD_HEIGHT + CARD_PADDING);

            RenderSystem.setShaderTexture(0, BACKGROUND_TEXTURE);
            this.blit(poseStack, cardX, cardY, 0, 0, CARD_WIDTH, CARD_HEIGHT);

            RoomRecipe recipe = quests.get(i);
            if (recipe != null) {
                for (int j = 0; j < recipe.getIngredients().size(); j++) {
                    int iconX = cardX + 8 + j * 18;
                    int iconY = cardY + CARD_HEIGHT - 24;

                    Slot slot = new Slot(dummyInv, j, iconX, iconY);
                    this.blit(poseStack, iconX, iconY, 0, 166, 18, 18);
                    this.renderSlot(poseStack, slot, mouseX, mouseY, partialTicks);
                }
            }
        }
    }

    @Override
    protected void renderBg(PoseStack matrixStack, float partialTicks, int mouseX, int mouseY) {
        // Bind the inventory background texture
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, INVENTORY_LOCATION);

        // Draw the background
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;
        this.blit(matrixStack, x, y, 0, 0, backgroundWidth, backgroundHeight);
    }

    private void renderSlot(PoseStack poseStack, Slot slot, int mouseX, int mouseY, float partialTicks) {
        ItemStack stack = slot.getItem();
        if (!stack.isEmpty()) {
            this.minecraft.getItemRenderer().renderGuiItem(stack, slot.x, slot.y);
            this.minecraft.getItemRenderer().renderGuiItemDecorations(this.font, stack, slot.x, slot.y, "");
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}