package ca.bradj.questown.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.Internal;
import mezz.jei.gui.elements.DrawableNineSliceTexture;
import mezz.jei.gui.textures.Textures;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

public class GathererInventoryScreen extends AbstractContainerScreen<GathererInventoryContainer> {

    private static final int backgroundWidth = 176;
    private static final int backgroundHeight = 166;

    private final DrawableNineSliceTexture background;

    public GathererInventoryScreen(GathererInventoryContainer gathererInv, Inventory playerInv, Component title) {
        super(gathererInv, playerInv, title);
        Textures textures = Internal.getTextures();
        this.background = textures.getRecipeGuiBackground();
    }

    @Override
    public void render(PoseStack stack, int mouseX, int mouseY, float partialTicks) {
        super.renderBackground(stack);
        super.render(stack, mouseX, mouseY, partialTicks);
        super.renderTooltip(stack, mouseX, mouseY);
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        for (ResourceLocation rl : this.menu.gathererInventory) {
            Item i = ForgeRegistries.ITEMS.getValue(rl);
            this.itemRenderer.renderAndDecorateItem(new ItemStack(i, 1), x += 10, y);
        }
    }

    @Override
    protected void renderBg(PoseStack stack, float p_230450_2_, int p_230450_3_, int p_230450_4_) {
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        this.background.draw(stack, x, y, backgroundWidth, backgroundHeight);
    }
}