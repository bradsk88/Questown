package ca.bradj.questown.gui;

import ca.bradj.questown.core.network.JobRootChangeMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.JEI;
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

public class JobChangeConfirmScreen extends AbstractContainerScreen<JobChangeConfirmMenu> {

    private static final int backgroundWidth = 176;
    private static final int backgroundHeight = 166;
    private final VillagerTabs tabs;

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
        tabs = VillagerTabs.forMenu(menu);
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
            QuestownNetwork.CHANNEL.sendToServer(new JobRootChangeMessage(
                    menu.flagPos,
                    menu.villagerUUID,
                    Minecraft.getInstance().player.isCreative()
            ));
            Minecraft.getInstance().setScreen(null);
        }
        ).bounds(maybeX, maybeY, buttonWidth, 20).build());
        if (menu.alreadyPending.get() == 0) {
            this.addRenderableWidget(Button.builder(
                    Compat.translatable("menu.common.cancel"),
                    (p_96776_) -> Minecraft.getInstance().setScreen(null)
            ).bounds(maybeX + buttonWidth, maybeY, buttonWidth, 20).build());
        } else {
            unlockButton.visible = false;
            unlockButton.active = false;
        }
        this.textX = maybeX + slot.getWidth() + 8;
    }

    @Override
    public void render(
            GuiGraphics gui,
            int mouseX,
            int mouseY,
            float partialTicks
    ) {
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - backgroundHeight) / 2;
        int jobIconX = bgX + 8 + slot.getWidth() + 4;
        super.renderBackground(gui);
        super.render(gui, mouseX, mouseY, partialTicks);
        if (menu.alreadyPending.get() == 1) {
            renderStatusText(gui, bgX, bgY + menu.gathererInventoryYOffset + 24);
            return;
        }

        if (unlockButton != null) {
            unlockButton.active = menu.tx.hasBlockOfProgress();
        }
        renderChangeText(gui, jobIconX, bgY + menu.gathererInventoryYOffset);
        this.tabs.renderTooltip(bgX, bgY, mouseX, mouseY, key -> this.setTooltipForNextRenderPass(Compat.translatable(key)));
    }

    private void renderChangeText(
            GuiGraphics gui,
            int x,
            int y1
    ) {
        int bgY = y1;
        int textWidth = backgroundWidth - slot.getWidth() - (8 * 2);


        String key = "menu.unlock_root.insert_bop";
        if (menu.isBlessed()) {
            key = "menu.unlock_root.insert_bop_blessed";
        }
        List<FormattedCharSequence> parts = font.split(Compat.translatable(key), textWidth);
        for (FormattedCharSequence part : parts) {
            Compat.drawDarkText(font, gui, part, x, bgY);
            bgY += 8;
        }
    }

    private void renderStatusText(
            GuiGraphics gui,
            int x,
            int y1
    ) {
        int bgY = y1;
        int textWidth = backgroundWidth;
        List<FormattedCharSequence> parts = font.split(
                Compat.translatable("menu.unlock_root.already_pending"),
                textWidth
        );
        for (FormattedCharSequence part : parts) {
            gui.drawCenteredString(font, part, x + (textWidth/2), bgY, -1);
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
        this.tabs.draw(new RenderContext(Compat.itemRenderer(), gui), bgX, bgY);
        renderInventory(gui);
    }

    private void renderInventory(GuiGraphics gui) {
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        int yCoord;
        gathererSlotY = Integer.MAX_VALUE;
        int size = menu.slots.size();
        if (menu.alreadyPending.get() == 1) {
            size = size - 1;
        }
        for (int i = 0; i < size; i++) {
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
        if (tabs.mouseClicked(x, y, mouseX, mouseY)) {
            return true;
        }
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