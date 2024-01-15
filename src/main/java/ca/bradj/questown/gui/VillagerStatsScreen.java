package ca.bradj.questown.gui;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.elements.DrawableNineSliceTexture;
import mezz.jei.common.gui.textures.Textures;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Supplier;

public class VillagerStatsScreen extends AbstractContainerScreen<VillagerStatsMenu> {
    private static final int backgroundWidth = 176;
    private static final int backgroundHeight = 166;
    private final DrawableNineSliceTexture background;
    private final IDrawableStatic tab;
    private final IDrawableStatic unTab;
    private final Supplier<QuestsScreen> questScreen;
    private final Supplier<InventoryAndStatusScreen> invScreen;
    private int tabsY;
    private int invTabX;
    private int questTabX;
    private int statsTabX;

    public VillagerStatsScreen(
            VillagerStatsMenu menu,
            Inventory playerInv,
            Component title,
            Supplier<QuestsScreen> questScreen,
            Supplier<InventoryAndStatusScreen> inventoryAndStatusScreen
    ) {
        super(menu, playerInv, title);
        this.questScreen = questScreen;
        this.invScreen = inventoryAndStatusScreen;
        super.imageWidth = 256;
        super.imageHeight = 220;

        Textures textures = Internal.getTextures();
        this.background = textures.getRecipeGuiBackground();
        this.tab = textures.getTabSelected();
        this.unTab = textures.getTabUnselected();
    }

    public static VillagerStatsScreen withInventoryScreen(
            InventoryAndStatusScreen inventoryAndStatusScreen,
            VillagerStatsMenu menu,
            Inventory playerInv,
            Component title
    ) {
        return new VillagerStatsScreen(
                menu, playerInv, title,
                () -> new QuestsScreen(menu.questsMenu(), playerInv, title),
                () -> inventoryAndStatusScreen
        );
    }

    @Override
    protected void init() {
        super.init();

        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - backgroundHeight) / 2;
        this.tabsY = bgY - this.unTab.getHeight() + 4;
        this.invTabX = bgX + (unTab.getWidth() * 0) + 4;
        this.questTabX = bgX + (unTab.getWidth() * 1) + 4;
        this.statsTabX = bgX + (unTab.getWidth() * 2) + 4;
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
        this.unTab.draw(stack, this.invTabX, this.tabsY);
        this.unTab.draw(stack, this.questTabX, this.tabsY);
        this.tab.draw(stack, this.statsTabX, this.tabsY);
        RenderSystem.setShaderTexture(0, new ResourceLocation("textures/gui/icons.png"));
        blit(stack, statsTabX + 11, tabsY + 11, 0, 0, 15, 9, 9, 256, 256);
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
        if (mouseX > invTabX && mouseX < invTabX + tab.getWidth() && mouseY > tabsY && mouseY < y) {
            this.minecraft.setScreen(invScreen.get());
        }
        if (mouseX > questTabX && mouseX < questTabX + tab.getWidth() && mouseY > tabsY && mouseY < y) {
            this.minecraft.setScreen(questScreen.get());
        }
        return super.mouseClicked(mouseX, mouseY, p_97750_);
    }

}