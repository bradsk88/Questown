package ca.bradj.questown.gui;

import ca.bradj.questown.core.Config;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.JEI;
import com.google.common.collect.ImmutableList;
import mezz.jei.common.util.ImmutableRect2i;
import net.minecraft.client.gui.GuiGraphics;
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
    private static final ResourceLocation icons = new ResourceLocation("textures/gui/icons.png");
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
            GuiGraphics gui,
            int p_97809_,
            int p_97810_
    ) {
    }

    private void renderCustomTooltip(
            GuiGraphics gui,
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
                key -> this.setTooltipForNextRenderPass(Compat.translatable(key))
        )) {
            return;
        }

        if (JEI.isCoordInBox(mouseX, mouseY, expBar)) {
            this.setTooltipForNextRenderPass(
                    Compat.translatable(
                            "menu.common.stat_tooltip",
                            Compat.translatable("menu.villager_stats.experience"),
                            menu.getExperienceValue(),
                            menu.getExperienceTarget()
                    )
            );
            return;
        }

        if (JEI.isCoordInBox(mouseX, mouseY, hngBar)) {
            this.setTooltipForNextRenderPass(
                    Compat.translatable(
                            "menu.common.stat_tooltip",
                            Compat.translatable("menu.villager_stats.hunger"),
                            menu.getFullnessPercent(),
                            100
                    )
            );
            return;
        }

        if (JEI.isCoordInBox(mouseX, mouseY, moodBar)) {
            this.setTooltipForNextRenderPass(
                    Compat.translatable(
                            "menu.common.stat_tooltip",
                            Compat.translatable("menu.villager_stats.mood"),
                            menu.getMoodPercent() * 100,
                            100
                    )
            );
            return;
        }
        if (JEI.isCoordInBox(mouseX, mouseY, dmgBar)) {
            this.setTooltipForNextRenderPass(
                    Compat.translatable(
                            "menu.common.stat_tooltip",
                            Compat.translatable("menu.villager_stats.damage"),
                            menu.getDamageLevel() * 100,
                            100
                    )
            );
            return;
        }
    }

    @Override
    public void render(
            GuiGraphics gui,
            int mouseX,
            int mouseY,
            float partialTicks
    ) {
        this.renderBackground(gui);
        super.render(gui, mouseX, mouseY, partialTicks);

        int position = 0;
        this.expBar = renderExperience(gui, position);
        position++;
//        renderMood(gui, position);
        position++;
        if (Config.HUNGER_ENABLED.get()) {
            this.hngBar = renderHunger(gui, position);
            position++;
        }
//        renderDamage(gui, position);
        renderCustomTooltip(gui, mouseX, mouseY);
    }

    private ImmutableRect2i renderExperience(
            GuiGraphics gui,
            int position
    ) {
        Component title = Compat.translatable("menu.villager_stats.experience");
        return renderBar(
                gui,
                position,
                title,
                (int) (100f * menu.getExperienceValue() / (float) menu.getExperienceTarget())
        );
    }

    private void renderMood(
            GuiGraphics gui,
            int position
    ) {
        Component title = Compat.translatable("menu.villager_stats.mood");
        renderBar(gui, position, title, menu.getMoodPercent());
    }

    private ImmutableRect2i renderHunger(
            GuiGraphics gui,
            int position
    ) {
        int fullnessPercent = menu.getFullnessPercent();
        Component title = Compat.translatable("menu.villager_stats.hunger");
        return renderBar(gui, position, title, fullnessPercent);
    }

    private ImmutableRect2i renderDamage(
            GuiGraphics gui,
            int position
    ) {
        int damageLevel = menu.getDamageLevel();
        Component title = Compat.translatable("menu.villager_stats.damage");
        return renderBar(gui, position, title, damageLevel);
    }

    private ImmutableRect2i renderBar(
            GuiGraphics gui,
            int index,
            Component title,
            int fullnessPercent
    ) {
        int bgX = (this.width - backgroundWidth) / 2;
        int bxY = (this.height - backgroundHeight) / 2;
        bxY = bxY + (25 * index);
        gui.drawString(font, title, bgX + 8, bxY + 16, 0x00000000);
        int x = 8 + bgX;
        int y = 28 + bxY;
        int height = 5;
        int barLeftX = x - 1;
        gui.blit(icons, barLeftX, y, 0, 0, 64, HALF_WIDTH, height, 256, 256);
        gui.blit(icons, x + 78, y, 0, 100, 64, HALF_WIDTH, height, 256, 256);
        float fP = fullnessPercent / 100f;
        int greenY = 69;
        int leftWidth = (int) (HALF_WIDTH * (2 * (Math.min(0.5, fP))));
        int rightWidth = (int) (HALF_WIDTH * (2 * (Math.min(0.5, (fP) - 0.5))));
        gui.blit(icons, barLeftX, y, 0, 0, greenY, leftWidth, height, 256, 256);
        gui.blit(icons, x + 78, y, 0, 100, greenY, rightWidth, height, 256, 256);
        return new ImmutableRect2i(barLeftX, y, HALF_WIDTH * 2, height);
    }

    @Override
    protected void renderBg(
            GuiGraphics gui,
            float partialTicks,
            int mouseX,
            int mouseY
    ) {
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        this.background.draw(gui, x, y, backgroundWidth, backgroundHeight);
        this.tabs.draw(new RenderContext(Compat.itemRenderer(), gui), x, y);
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
