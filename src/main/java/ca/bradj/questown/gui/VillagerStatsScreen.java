package ca.bradj.questown.gui;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.JEI;
import ca.bradj.questown.mc.Util;
import com.google.common.collect.ImmutableList;
import mezz.jei.common.util.ImmutableRect2i;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class VillagerStatsScreen extends CompatContainerScreen<VillagerStatsMenu> {
    public static final int HALF_WIDTH = 82;
    private static final int backgroundWidth = 176;
    private static final int backgroundHeight = 166;
    private static final ResourceLocation icons = Questown.ResourceLocation("textures/gui/bars.png");
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

    private void renderCustomTooltip(
            RenderContext rc,
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
                key -> this.showTooltip(Compat.translatable(key))
        )) {
            return;
        }

        if (JEI.isCoordInBox(mouseX, mouseY, expBar)) {
            this.showTooltip(
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
            this.showTooltip(
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
            this.showTooltip(
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
            this.showTooltip(
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
    protected void renderScreenContent(
            RenderContext rc,
            int mouseX,
            int mouseY,
            float partialTicks
    ) {
        int position = 0;
        this.expBar = renderExperience(rc, position);
        position++;
//        renderMood(rc, position);
        position++;
        if (Config.HUNGER_ENABLED.get()) {
            this.hngBar = renderHunger(rc, position);
            position++;
        }
//        renderDamage(rc, position);
        renderCustomTooltip(rc, mouseX, mouseY);
    }

    private ImmutableRect2i renderExperience(
            RenderContext rc,
            int position
    ) {
        Component title = Compat.translatable("menu.villager_stats.experience");
        return renderBar(
                rc,
                position,
                title,
                (int) (100f * menu.getExperienceValue() / (float) menu.getExperienceTarget())
        );
    }

    private void renderMood(
            RenderContext rc,
            int position
    ) {
        Component title = Compat.translatable("menu.villager_stats.mood");
        renderBar(rc, position, title, menu.getMoodPercent());
    }

    private ImmutableRect2i renderHunger(
            RenderContext rc,
            int position
    ) {
        int fullnessPercent = menu.getFullnessPercent();
        Component title = Compat.translatable("menu.villager_stats.hunger");
        return renderBar(rc, position, title, fullnessPercent);
    }

    private ImmutableRect2i renderDamage(
            RenderContext rc,
            int position
    ) {
        int damageLevel = menu.getDamageLevel();
        Component title = Compat.translatable("menu.villager_stats.damage");
        return renderBar(rc, position, title, damageLevel);
    }

    private ImmutableRect2i renderBar(
            RenderContext rc,
            int index,
            Component title,
            int fullnessPercent
    ) {
        int bgX = (this.width - backgroundWidth) / 2;
        int bxY = (this.height - backgroundHeight) / 2;
        bxY = bxY + (25 * index);
        rc.darkText(title, bgX + 8, bxY + 16);
        int x = 8 + bgX;
        int y = 28 + bxY;
        int height = 5;
        int barLeftX = x - 1;
        // bars.png (256x256): left half-bands at u0..94, right half-bands at u100..181
        // (rebaked with the dark right cap the golden had). Band rows: track v64..68,
        // fill v69..73 (v74 down is the purple bands). Each 82px half fills 0..50% /
        // 50..100%, like the golden's two-half layout. Util.blit is the verified
        // 1:1 pixel blit over the 256 sheet.
        Util.blit(rc.gui(), icons, barLeftX, y, 1, 64, HALF_WIDTH, height);
        Util.blit(rc.gui(), icons, x + 78, y, 100, 64, HALF_WIDTH, height);
        float fP = fullnessPercent / 100f;
        int leftWidth = (int) (HALF_WIDTH * (2 * (Math.min(0.5, fP))));
        int rightWidth = (int) (HALF_WIDTH * (2 * (Math.min(0.5, (fP) - 0.5))));
        Util.blit(rc.gui(), icons, barLeftX, y, 1, 69, leftWidth, height);
        Util.blit(rc.gui(), icons, x + 78, y, 100, 69, rightWidth, height);
        return new ImmutableRect2i(barLeftX, y, HALF_WIDTH * 2, height);
    }

    @Override
    protected void renderBgContent(
            RenderContext ctx,
            float partialTicks,
            int mouseX,
            int mouseY
    ) {
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        ctx.drawNineNine(this.background, x, y, backgroundWidth, backgroundHeight);
        this.tabs.draw(ctx, x, y);
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
