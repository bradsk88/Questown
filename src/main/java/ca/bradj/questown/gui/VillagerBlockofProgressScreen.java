package ca.bradj.questown.gui;

import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.core.network.EconomicsUpdate;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.JEI;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class VillagerBlockofProgressScreen extends AbstractContainerScreen<VillagerBlockofProgressMenu> {
    private static final int backgroundWidth = 176;
    private static final int backgroundHeight = 166;
    private final VillagerTabs tabs;

    public static EconomicsUpdate lastUpdate = new EconomicsUpdate(ImmutableList.of());
    private final JEI.NineNine background;

    IngredientRenderer ingredientRenderer = new IngredientRenderer();

    public VillagerBlockofProgressScreen(
            VillagerBlockofProgressMenu menu,
            Inventory playerInv,
            Component title
    ) {
        super(menu, playerInv, title);
        super.imageWidth = 256;
        super.imageHeight = 220;

        this.background = JEI.getRecipeBackground();
        this.tabs = VillagerTabs.forMenu(menu);
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
    protected void renderLabels(
            PoseStack p_97808_,
            int p_97809_,
            int p_97810_
    ) {
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
        int iconX = (width - 16) /2;
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - backgroundHeight) / 2;
        bgY += 32;
        RenderUtil.renderItemScaled(itemRenderer, 8, ItemsInit.BLOCK_OF_PROGRESS.get().getDefaultInstance(), iconX, bgY);
        bgY += 64;
        Compat.drawDarkText(font, poseStack, Compat.translatable("menu.block_of_progress.v_has_earned", menu.villagerUUID), bgX, bgY);
        Compat.drawDarkText(font, poseStack, Compat.translatable("menu.block_of_progress.will_deposit"), bgX, bgY);
    }

    @Override
    protected void renderBg(
            PoseStack stack,
            float partialTicks,
            int mouseX,
            int mouseY
    ) {
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - backgroundHeight) / 2;
        this.background.draw(stack, bgX, bgY, backgroundWidth, backgroundHeight);
        this.tabs.draw(new RenderContext(itemRenderer, stack), bgX, bgY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public List<Rect2i> getExtraAreas() {
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        return ImmutableList.of(new Rect2i(x, y, backgroundWidth, backgroundHeight));
    }

    @Override
    public boolean isMouseOver(
            double mouseX,
            double mouseY
    ) {
        return true;
    }

    @Override
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int p_97750_
    ) {
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        this.tabs.mouseClicked(x, y, mouseX, mouseY);
        return super.mouseClicked(mouseX, mouseY, p_97750_);
    }
}