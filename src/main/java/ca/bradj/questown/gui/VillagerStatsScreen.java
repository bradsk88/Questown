package ca.bradj.questown.gui;

import ca.bradj.questown.core.Config;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.JEI;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.common.util.ImmutableRect2i;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class VillagerStatsScreen extends AbstractContainerScreen<VillagerStatsMenu> {
    public static final int HALF_WIDTH = 82;
    private static final int backgroundWidth = 176;
    private static final int backgroundHeight = 166;
    private final JEI.NineNine background;
    private final VillagerTabs tabs;
    private ImmutableRect2i expBar = new ImmutableRect2i(0, 0, 0, 0);
    private ImmutableRect2i hngBar = new ImmutableRect2i(0, 0, 0, 0);
    private ImmutableRect2i moodBar = new ImmutableRect2i(0, 0, 0, 0);
    private ImmutableRect2i dmgBar = new ImmutableRect2i(0, 0, 0, 0);

    public VillagerStatsScreen(
            VillagerStatsMenu menu,
            Inventory playerInv,
            Component title
    ) {
        super(menu, playerInv, title);
        super.imageWidth = 256;
        super.imageHeight = 220;

        this.background = JEI.getRecipeGuiBackground();
        this.tabs = VillagerTabs.forMenu(menu);
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
    protected void renderLabels(
            PoseStack p_97808_,
            int p_97809_,
            int p_97810_
    ) {
    }

    @Override
    protected void renderTooltip(
            PoseStack stack,
            int mouseX,
            int mouseY
    ) {
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - backgroundHeight) / 2;
        if (this.tabs.renderTooltip(
                bgX,
                bgY,
                mouseX,
                mouseY,
                key -> super.renderTooltip(stack, Compat.translatable(key), mouseX, mouseY)
        )) {
            return;
        }

        if (JEI.isCoordInBox(mouseX, mouseY, expBar)) {
            super.renderTooltip(
                    stack, Compat.translatable(
                            "menu.common.stat_tooltip",
                            Compat.translatable("menu.villager_stats.experience"),
                            menu.getExperienceValue(),
                            menu.getExperienceTarget()
                    ), mouseX, mouseY
            );
            return;
        }

        if (JEI.isCoordInBox(mouseX, mouseY, hngBar)) {
            super.renderTooltip(
                    stack, Compat.translatable(
                            "menu.common.stat_tooltip",
                            Compat.translatable("menu.villager_stats.hunger"),
                            menu.getFullnessPercent() * 100,
                            100
                    ), mouseX, mouseY
            );
            return;
        }

        if (JEI.isCoordInBox(mouseX, mouseY, moodBar)) {
            super.renderTooltip(
                    stack, Compat.translatable(
                            "menu.common.stat_tooltip",
                            Compat.translatable("menu.villager_stats.mood"),
                            menu.getMoodPercent() * 100,
                            100
                    ), mouseX, mouseY
            );
            return;
        }
        if (JEI.isCoordInBox(mouseX, mouseY, dmgBar)) {
            super.renderTooltip(
                    stack, Compat.translatable(
                            "menu.common.stat_tooltip",
                            Compat.translatable("menu.villager_stats.damage"),
                            menu.getDamageLevel() * 100,
                            100
                    ), mouseX, mouseY
            );
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

        int position = 0;
        this.expBar = renderExperience(poseStack, position);
        position++;
//        renderMood(poseStack, position);
        position++;
        if (Config.HUNGER_ENABLED.get()) {
            this.hngBar = renderHunger(poseStack, position);
            position++;
        }
//        renderDamage(poseStack, position);
        renderTooltip(poseStack, mouseX, mouseY);
    }

    private ImmutableRect2i renderExperience(
            PoseStack stack,
            int position
    ) {
        Component title = Compat.translatable("menu.villager_stats.experience");
        return renderBar(
                stack,
                position,
                title,
                (int) (100f * menu.getExperienceValue() / (float) menu.getExperienceTarget())
        );
    }

    private void renderMood(
            PoseStack stack,
            int position
    ) {
        Component title = Compat.translatable("menu.villager_stats.mood");
        renderBar(stack, position, title, menu.getMoodPercent());
    }

    private ImmutableRect2i renderHunger(
            PoseStack stack,
            int position
    ) {
        int fullnessPercent = menu.getFullnessPercent();
        Component title = Compat.translatable("menu.villager_stats.hunger");
        return renderBar(stack, position, title, fullnessPercent);
    }

    private ImmutableRect2i renderDamage(
            PoseStack stack,
            int position
    ) {
        int damageLevel = menu.getDamageLevel();
        Component title = Compat.translatable("menu.villager_stats.damage");
        return renderBar(stack, position, title, damageLevel);
    }

    private ImmutableRect2i renderBar(
            PoseStack stack,
            int index,
            Component title,
            int fullnessPercent
    ) {
        int bgX = (this.width - backgroundWidth) / 2;
        int bxY = (this.height - backgroundHeight) / 2;
        bxY = bxY + (25 * index);
        font.draw(stack, title, bgX + 8, bxY + 16, 0x00000000);
        RenderSystem.setShaderTexture(0, new ResourceLocation("textures/gui/icons.png"));
        int x = 8 + bgX;
        int y = 28 + bxY;
        int height = 5;
        int barLeftX = x - 1;
        blit(stack, barLeftX, y, 0, 0, 64, HALF_WIDTH, height, 256, 256);
        blit(stack, x + 78, y, 0, 100, 64, HALF_WIDTH, height, 256, 256);
        float fP = fullnessPercent / 100f;
        int greenY = 69;
        int leftWidth = (int) (HALF_WIDTH * (2 * (Math.min(0.5, fP))));
        int rightWidth = (int) (HALF_WIDTH * (2 * (Math.min(0.5, (fP) - 0.5))));
        blit(stack, barLeftX, y, 0, 0, greenY, leftWidth, height, 256, 256);
        blit(stack, x + 78, y, 0, 100, greenY, rightWidth, height, 256, 256);
        return new ImmutableRect2i(barLeftX, y, HALF_WIDTH * 2, height);
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
        this.tabs.draw(new RenderContext(itemRenderer, stack), x, y);
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