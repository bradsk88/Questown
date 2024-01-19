package ca.bradj.questown.gui;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.elements.DrawableNineSliceTexture;
import mezz.jei.common.gui.textures.Textures;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Supplier;

public class VillagerStatsScreen extends AbstractContainerScreen<VillagerStatsMenu> {
    private static final int backgroundWidth = 176;
    private static final int backgroundHeight = 166;
    private final DrawableNineSliceTexture background;
    private final VillagerTabs tabs;

    public VillagerStatsScreen(
            VillagerStatsMenu menu,
            Inventory playerInv,
            Component title,
            Supplier<Screen> questScreen,
            Supplier<Screen> inventoryAndStatusScreen
    ) {
        super(menu, playerInv, title);
        super.imageWidth = 256;
        super.imageHeight = 220;

        Textures textures = Internal.getTextures();
        this.background = textures.getRecipeGuiBackground();
        this.tabs = new VillagerTabs(inventoryAndStatusScreen, questScreen, null);
    }

    @Override
    protected void init() {
        super.init();

        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - backgroundHeight) / 2;
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
    public void render(
            PoseStack poseStack,
            int mouseX,
            int mouseY,
            float partialTicks
    ) {
        this.renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTicks);
    }

    @Override
    protected void renderBg(
            PoseStack stack,
            float partialTicks,
            int mouseX,
            int mouseY
    ) {
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        this.background.draw(stack, x, y, backgroundWidth, backgroundHeight);
        this.tabs.draw(stack, x, y);
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

    @Override
    public boolean isMouseOver(
            double mouseX,
            double mouseY
    ) {
        return true;
    }
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int p_97750_) {
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        this.tabs.mouseClicked(minecraft, x, y, mouseX, mouseY);
        return super.mouseClicked(mouseX, mouseY, p_97750_);
    }

}