package ca.bradj.questown.gui;

import ca.bradj.questown.core.Coordinate;
import ca.bradj.questown.core.network.OpenJobMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.JEI;
import net.minecraft.client.gui.GuiGraphics;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.Ingredient;


import static ca.bradj.questown.gui.InventoryAndStatusMenu.TE_INVENTORY_FIRST_SLOT_INDEX;

public class InventoryAndStatusScreen extends AbstractContainerScreen<InventoryAndStatusMenu> {

    private static final int backgroundWidth = 176;
    private static final int backgroundHeight = 166;

    private final JEI.NineNine background;
    private final IDrawableStatic slot;
    private final ResourceLocation lockTex;
    private final Tabs tabs;
    private final StatusGraphic status;

    private final IngredientRenderer ingredientRenderer = new IngredientRenderer();

    public InventoryAndStatusScreen(
            InventoryAndStatusMenu menu,
            Inventory playerInv,
            Component title
    ) {
        super(menu, playerInv, title);
        this.background = JEI.getRecipeGuiBackground();
        this.slot = JEI.getSlotDrawable();
        this.lockTex = new ResourceLocation("questown", "textures/menu/gatherer/locked.png");
        this.tabs = VillagerTabs.forMenu(menu);
        this.status = new StatusGraphic(
                menu.jobId,
                () -> new Coordinate(
                        ((this.width - backgroundWidth) / 2) + backgroundWidth - 16 - 32,
                        ((this.height - backgroundHeight) / 2) + 16
                ),
                menu::getStatus
        );
    }

    @Override
    public void onClose() {
        super.onClose();
        menu.onClose();
    }

    @Override
    protected void init() {
        super.init();
        ClientAccess.dismissTutorialToast();
        int maybeX = (this.width / 2) + 32;
        int maybeY = ((this.height - backgroundHeight) / 2) + 32 + 16 + 8;

        this.addRenderableWidget(
                Button.builder(
                        Compat.translatable("menu.quests"),
                        (p_96776_) -> {
                            openQuestsScreen();
                        }
                ).bounds(maybeX, maybeY, 48, 20).build()
        );
    }

    private void openQuestsScreen() {
//        this.minecraft.setScreen(questScreen);
    }

    @Override
    public void render(
            GuiGraphics gui,
            int mouseX,
            int mouseY,
            float partialTicks
    ) {
        super.render(gui, mouseX, mouseY, partialTicks);
        renderTooltips(mouseX, mouseY);
    }

    // 1.19.2 parity: the 1.19.2 background here was the (blurred) world, not an opaque
    // gradient; the HUD was drawn above it. See QuestownHudEvents for the HUD redraw.
    @Override
    public void renderBackground(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        this.renderBlurredBackground(partialTick);
        this.renderBg(guiGraphics, partialTick, mouseX, mouseY);
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
        status.render(gui);
    }

    private void renderInventory(GuiGraphics gui) {
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        int yCoord = 0;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            int xCoord = x - 1 + s.x;
            yCoord = y - 1 + s.y;
            this.slot.draw(gui, xCoord, yCoord);
        }
        int iconX = x - 8;
        for (Ingredient i : ClientJobWantedResources.wantedIngredients) {
            this.ingredientRenderer.render(gui, i, iconX += 16, yCoord + 32);
        }
    }

    private void renderTooltips(
            int mouseX,
            int mouseY
    ) {
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;

        if (this.tabs.renderTooltip(
                x, y, mouseX, mouseY,
                key -> this.setTooltipForNextRenderPass(Compat.translatable(key))
        )) {
            return;
        }

        if (status.renderTooltip(
                mouseX, mouseY,
                key -> this.setTooltipForNextRenderPass(key.stream().map(Component::getVisualOrderText).toList())
        )) {
            return;
        }

        int yCoord = y - 1;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            int xCoord = x - 1 + s.x;
            yCoord = y - 1 + s.y;
            if (i >= TE_INVENTORY_FIRST_SLOT_INDEX) {
                if (renderLocksTooltip(xCoord + 1, yCoord + 16 + 2, mouseX, mouseY)) {
                    return;
                }
            }
        }

        int iconX = x - 12;
        for (Ingredient i : ClientJobWantedResources.wantedIngredients) {
            if (renderNeedsTooltip(iconX += 16 + 4, yCoord + 32, mouseX, mouseY, i)) {
                return;
            }
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
        if (this.tabs.mouseClicked(x, y, mouseX, mouseY)) {
            return true;
        }
        if (status.mouseClicked((int) mouseX, (int) mouseY)) {
            QuestownNetwork.sendToServer(new OpenJobMessage(menu.flagPos, menu.jobId));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, p_97750_);
    }

    private boolean renderLocksTooltip(
            int leftX,
            int topY,
            int mouseX,
            int mouseY
    ) {
        int rightX = leftX + 16;
        int botY = topY + 8;
        if (mouseX > leftX && mouseX < rightX) {
            if (mouseY > topY && mouseY < botY) {
                Component component = Compat.translatable(
                        "tooltips.villagers.job.inventory.locked"
                );
                this.setTooltipForNextRenderPass(component);
                return true;
            }
        }
        return false;
    }

    private boolean renderNeedsTooltip(
            int leftX,
            int topY,
            int mouseX,
            int mouseY,
            Ingredient item
    ) {
        int rightX = leftX + 16;
        int botY = topY + 16;
        if (mouseX > leftX && mouseX < rightX) {
            if (mouseY > topY && mouseY < botY) {
                Component jPart = Compat.translatable(String.format("jobs.%s", menu.getRootJobId()));
                Component component = Compat.translatable(
                        "tooltips.villagers.job.needs", jPart, Ingredients.getName(item)
                );
                this.setTooltipForNextRenderPass(component);
                return true;
            }
        }
        return false;
    }
}