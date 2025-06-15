package ca.bradj.questown.gui;

import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.core.network.UnlockJobMessage;
import ca.bradj.questown.gui.villager.advancements.VillagerAdvancements;
import ca.bradj.questown.jobs.Jobs;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.JEI;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
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

public class JobChangeConfirmScreen extends AbstractContainerScreen<JobChangeConfirmMenu> {

    private static final int backgroundWidth = 176;
    private static final int backgroundHeight = 166;

    private final JEI.NineNine background;
    private final IDrawableStatic slot;
    private int textX;
    private FormattedText texts;
    private int gathererSlotY;
    private Button unlockButton;

    public JobChangeConfirmScreen(
            JobChangeConfirmMenu menu,
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
        this.unlockButton = this.addRenderableWidget(new Button(
                maybeX, maybeY, buttonWidth, 20, Compat.translatable("menu.common.unlock"), (p_96776_) -> {
            QuestownNetwork.CHANNEL.sendToServer(new UnlockJobMessage(
                    menu.flagPos,
                    menu.villagerUUID,
                    menu.jobId,
                    false
            ));
            Minecraft.getInstance().setScreen(null);
        }
        ));
        this.addRenderableWidget(new Button(
                maybeX + buttonWidth,
                maybeY,
                buttonWidth,
                20,
                Compat.translatable("menu.common.cancel"),
                (p_96776_) -> Minecraft.getInstance().setScreen(null)
        ));
        this.textX = maybeX + slot.getWidth() + 8;
    }

    @Override
    public void render(
            PoseStack stack,
            int mouseX,
            int mouseY,
            float partialTicks
    ) {
        if (unlockButton != null) {
            unlockButton.active = menu.hasBlockOfProgress();
        }
        super.renderBackground(stack);
        super.render(stack, mouseX, mouseY, partialTicks);
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - backgroundHeight) / 2;
        int jobIconX = bgX + 8 + slot.getWidth() + 4;
        renderText(stack, jobIconX, bgY + menu.gathererInventoryYOffset);
        this.renderTooltip(stack, mouseX, mouseY);
    }

    private void renderText(
            PoseStack stack,
            int x,
            int y1
    ) {
        int bgY = y1;
        int textWidth = backgroundWidth - slot.getWidth() - (8 * 2);
        List<FormattedCharSequence> parts = font.split(Compat.translatable("menu.unlock_root.insert_bop"), textWidth);
        for (FormattedCharSequence part : parts) {
            Compat.drawDarkText(font, stack, part, x, bgY);
            bgY += 8;
        }
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
        renderInventory(stack);
    }

    private void renderInventory(PoseStack stack) {
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        int yCoord;
        gathererSlotY = Integer.MAX_VALUE;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            int xCoord = x - 1 + s.x;
            yCoord = y - 1 + s.y;
            this.gathererSlotY = Math.min(yCoord, gathererSlotY);
            this.slot.draw(stack, xCoord, yCoord);
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
            PoseStack p_97808_,
            int p_97809_,
            int p_97810_
    ) {
    }
}