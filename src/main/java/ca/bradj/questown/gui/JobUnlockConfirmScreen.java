package ca.bradj.questown.gui;

import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.core.network.UnlockJobMessage;
import ca.bradj.questown.gui.villager.advancements.VillagerAdvancements;
import ca.bradj.questown.jobs.Jobs;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.JEI;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.gui.GuiGraphics;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.List;

public class JobUnlockConfirmScreen extends AbstractContainerScreen<JobUnlockConfirmMenu> {

    private static final int backgroundWidth = 176;
    private static final int backgroundHeight = 166;

    private final JEI.NineNine background;
    private final IDrawableStatic slot;
    private int textX;
    private FormattedText texts;
    private int gathererSlotY;
    private Button unlockButton;

    public JobUnlockConfirmScreen(
            JobUnlockConfirmMenu menu,
            Inventory playerInv,
            Component title
    ) {
        super(menu, playerInv, title);
        this.background = JEI.getRecipeGuiBackground();
        this.slot = JEI.getSlotDrawable();
    }

    @Override
    public void onClose() {
        super.onClose();
        menu.onClose();
    }

    @Override
    protected void init() {
        super.init();
        int maybeX = (this.width - backgroundWidth) / 2;
        maybeX += 8;
        int maybeY = ((this.height - backgroundHeight) / 2) + 32 + 16 + 8;

        int buttonWidth = (backgroundWidth / 2) - 8;
        this.unlockButton = this.addRenderableWidget(Button.builder(
                Compat.translatable("menu.common.unlock"), (p_96776_) -> {
            QuestownNetwork.sendToServer(new UnlockJobMessage(
                    menu.flagPos,
                    menu.villagerUUID,
                    menu.jobId,
                    false
            ));
            Minecraft.getInstance().setScreen(null);
        }
        ).bounds(maybeX, maybeY, buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(
                Compat.translatable("menu.common.cancel"),
                (p_96776_) -> Minecraft.getInstance().setScreen(null)
        ).bounds(maybeX + buttonWidth, maybeY, buttonWidth, 20).build());
        this.textX = maybeX + slot.getWidth() + 8;
    }

    @Override
    public void render(
            GuiGraphics gui,
            int mouseX,
            int mouseY,
            float partialTicks
    ) {
        if (unlockButton != null) {
            unlockButton.active = menu.hasBlockOfProgress();
        }
        super.renderBackground(gui, mouseX, mouseY, partialTicks);
        super.render(gui, mouseX, mouseY, partialTicks);
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - backgroundHeight) / 2;
        int jobIconY = bgY + 6;
        Compat.renderItemAndDecorations(gui, VillagerAdvancements.getIcon(menu.jobId), bgX + 8, jobIconY);
        int jobIconX = bgX + 8 + slot.getWidth() + 4;
        renderText(gui, jobIconX, jobIconY + 5, bgY + menu.gathererInventoryYOffset);
    }

    private void renderText(
            GuiGraphics gui,
            int x,
            int y1,
            int y2
    ) {
        int bgY = y1;
        ImmutableList<Component> jc = Jobs.getComponentsForTooltip(menu.jobId);
        int textWidth = backgroundWidth - slot.getWidth() - (8 * 2);
        List<FormattedCharSequence> parts = font.split(
                Compat.translatable(
                        "menu.common.job_name",
                        jc.get(0),
                        jc.get(1)
                ), textWidth
        );
        for (FormattedCharSequence part : parts) {
            Compat.drawDarkText(font, gui, part, x, bgY);
            bgY += 8;
        }
        bgY = y2;
        parts = font.split(Compat.translatable("menu.unlock_job.insert_bop"), textWidth);
        for (FormattedCharSequence part : parts) {
            Compat.drawDarkText(font, gui, part, x, bgY);
            bgY += 8;
        }
    }

    @Override
    protected void renderBg(
            GuiGraphics gui,
            float partialTicks,
            int mouseX,
            int mouseY
    ) {
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - backgroundHeight) / 2;
        this.background.draw(gui, bgX, bgY, backgroundWidth, backgroundHeight);
        renderInventory(gui);
    }

    private void renderInventory(GuiGraphics gui) {
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        int yCoord;
        gathererSlotY = Integer.MAX_VALUE;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            int xCoord = x - 1 + s.x;
            yCoord = y - 1 + s.y;
            this.gathererSlotY = Math.min(yCoord, gathererSlotY);
            this.slot.draw(gui, xCoord, yCoord);
        }
    }

    @Override
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int p_97750_
    ) {
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        return super.mouseClicked(mouseX, mouseY, p_97750_);
    }

    @Override
    protected void renderLabels(
            GuiGraphics p_97808_,
            int p_97809_,
            int p_97810_
    ) {
    }
}