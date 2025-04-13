package ca.bradj.questown.gui;

import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.core.network.UnlockJobMessage;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.JEI;
import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public class JobUnlockConfirmScreen extends AbstractContainerScreen<JobUnlockConfirmMenu> {

    private static final int backgroundWidth = 176;
    private static final int backgroundHeight = 166;

    private final JEI.NineNine background;
    private final IDrawableStatic slot;

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
        int maybeY = ((this.height - backgroundHeight) / 2) + 32 + 16 + 8;

        int buttonWidth = 48;
        this.addRenderableWidget(new Button(
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
    }

    @Override
    public void render(
            PoseStack stack,
            int mouseX,
            int mouseY,
            float partialTicks
    ) {
        super.renderBackground(stack);
        super.render(stack, mouseX, mouseY, partialTicks);
        this.renderTooltip(stack, mouseX, mouseY);
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
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            int xCoord = x - 1 + s.x;
            yCoord = y - 1 + s.y;
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
}