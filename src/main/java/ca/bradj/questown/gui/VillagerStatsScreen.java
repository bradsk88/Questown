package ca.bradj.questown.gui;

import ca.bradj.questown.mc.JEI;
import ca.bradj.questown.mc.Util;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class VillagerStatsScreen extends AbstractContainerScreen<VillagerStatsMenu> {
    private static final int backgroundWidth = 176;
    private static final int backgroundHeight = 166;
    private final JEI.NineNine background;
    private final VillagerTabs tabs;

    public VillagerStatsScreen(
            VillagerStatsMenu menu,
            Inventory playerInv,
            Component title
    ) {
        super(menu, playerInv, title);
        super.imageWidth = 256;
        super.imageHeight = 220;

        this.background = JEI.getRecipeGuiBackground();
        this.tabs = new VillagerTabs(menu::openInv, menu::openQuests, null);
    }

    @Override
    public void onClose() {
        super.onClose();
        menu.onClose();
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
    protected void renderLabels(PoseStack p_97808_, int p_97809_, int p_97810_) {
    }

    @Override
    protected void renderTooltip(PoseStack stack, int mouseX, int mouseY) {
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - backgroundHeight) / 2;
        if (this.tabs.renderTooltip(
                bgX, bgY, mouseX, mouseY,
                key -> super.renderTooltip(stack, Util.translatable(key), mouseX, mouseY)
        )) {
            return;
        }
        super.renderTooltip(stack, mouseX, mouseY);
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

        renderMood(poseStack);
        renderHunger(poseStack);
    }

    private void renderMood(PoseStack stack) {
        Component title = Util.translatable("menu.mood");
        renderBar(stack, 0, title, menu.getMoodPercent());
    }

    private void renderHunger(PoseStack stack) {
        int fullnessPercent = menu.getFullnessPercent();
        Component title = Util.translatable("menu.hunger");
        renderBar(stack, 1, title, fullnessPercent);
    }

    private void renderBar(PoseStack stack, int index, Component title, int fullnessPercent) {
        int bgX = (this.width - backgroundWidth) / 2;
        int bxY = (this.height - backgroundHeight) / 2;
        bxY = bxY + (25 * index);
        font.draw(stack, title, bgX + 8, bxY + 16, 0x00000000);
        RenderSystem.setShaderTexture(0, new ResourceLocation("textures/gui/icons.png"));
        int x = 8 + bgX;
        int y = 28 + bxY;
        int halfWidth = 82;
        int height = 5;
        blit(stack, x - 1, y, 0, 0, 64, halfWidth, height, 256, 256);
        blit(stack, x + 78, y, 0, 100, 64, halfWidth, height, 256, 256);
        float fP = fullnessPercent / 100f;
        int greenY = 69;
        int leftWidth = (int) (halfWidth * (2 * (Math.min(0.5, fP))));
        int rightWidth = (int) ((halfWidth) * (2 * (Math.min(0.5, (fP) - 0.5))));
        blit(stack, x - 1, y, 0, 0, greenY, leftWidth, height, 256, 256);
        blit(stack, x + 78, y, 0, 100, greenY, rightWidth, height, 256, 256);
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
        this.tabs.mouseClicked(x, y, mouseX, mouseY);
        return super.mouseClicked(mouseX, mouseY, p_97750_);
    }

}