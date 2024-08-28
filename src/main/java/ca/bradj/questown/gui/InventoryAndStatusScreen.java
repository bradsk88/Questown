package ca.bradj.questown.gui;

import ca.bradj.questown.jobs.IStatus;
import ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob;
import ca.bradj.questown.mc.Compat;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.Internal;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.gui.elements.DrawableNineSliceTexture;
import mezz.jei.gui.textures.Textures;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

import static ca.bradj.questown.gui.InventoryAndStatusMenu.TE_INVENTORY_FIRST_SLOT_INDEX;

public class InventoryAndStatusScreen extends AbstractContainerScreen<InventoryAndStatusMenu> {

    private static final int backgroundWidth = 176;
    private static final int backgroundHeight = 166;

    private final DrawableNineSliceTexture background;
    private final IDrawableStatic slot;
    private final ResourceLocation lockTex;
    private final Tabs tabs;

    public InventoryAndStatusScreen(
            InventoryAndStatusMenu menu,
            Inventory playerInv,
            Component title
    ) {
        super(menu, playerInv, title);
        Textures textures = Internal.getTextures();
        this.background = textures.getRecipeGuiBackground();
        this.slot = textures.getSlotDrawable();
        this.lockTex = new ResourceLocation("questown", "textures/menu/gatherer/locked.png");
        // TODO: Extract a standard "VillagerTabs" that extends "Tabs" so this is easier to copy to the other screens
        this.tabs = VillagerTabs.forMenu(menu);
    }

    @Override
    public void onClose() {
        super.onClose();
        menu.onClose();
    }

    @Override
    protected void init() {
        super.init();
        int maybeX = (this.width / 2) + 32;
        int maybeY = ((this.height - backgroundHeight) / 2) + 32 + 16 + 8;

        this.addRenderableWidget(
                new Button(
                        maybeX, maybeY,
                        48, 20,
                        new TranslatableComponent("menu.quests"),
                        (p_96776_) -> {
                            openQuestsScreen();
                        }
                )
        );
    }

    private void openQuestsScreen() {
//        this.minecraft.setScreen(questScreen);
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
        this.tabs.draw(new RenderContext(itemRenderer, stack), bgX, bgY);
        renderInventory(stack);
    }

    private void renderInventory(PoseStack stack) {
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        renderStatus(stack);
        int yCoord = 0;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            int xCoord = x - 1 + s.x;
            yCoord = y - 1 + s.y;
            this.slot.draw(stack, xCoord, yCoord);
            // TODO: Bring back slot locks (or remove them)
//            if (i >= TE_INVENTORY_FIRST_SLOT_INDEX) {
//                // TODO: Compute or provide this value (6)
//                int statusI = i - TE_INVENTORY_FIRST_SLOT_INDEX;
//                renderSlotStatus(stack, menu.lockedSlots.get(statusI), xCoord + 1, yCoord + 16 + 2);
//            }
        }
        int iconX = x - 12;
        for (Ingredient i : ClientJobWantedResources.wantedIngredients) {
            int curSeconds = (int) (System.currentTimeMillis() / 1000);
            ItemStack[] matchingStacks = i.getItems();
            ItemStack itemStack = matchingStacks[curSeconds % matchingStacks.length];
            this.itemRenderer.renderAndDecorateItem(itemStack, iconX += 16 + 4, yCoord + 32);
        }
    }

    private void renderSlotStatus(
            PoseStack stack,
            DataSlot dataSlot,
            int x,
            int y
    ) {
        if (dataSlot.get() == 0) {
            return;
        }
        if (dataSlot.get() != 1) {
            throw new IllegalStateException("Slot status should only be 0 or 1");
        }
        RenderSystem.setShaderTexture(0, lockTex);
        int srcX = 0;
        int srcY = 0;
        int drawWidth = 16;
        int drawHeight = 8;
        int texWidth = 16;
        int texHeight = 8;
        blit(stack, x, y, srcX, srcY, drawWidth, drawHeight, texWidth, texHeight);
    }

    private void renderStatus(
            PoseStack stack
    ) {
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        RenderSystem.setShaderTexture(0, StatusArt.getTexture(menu.jobId, menu.getStatus()));
        int srcX = 0;
        int srcY = 0;
        int destX = x + backgroundWidth - 16 - 32;
        int destY = y + 16;
        int drawWidth = 32;
        int drawHeight = 32;
        int texWidth = 32;
        int texHeight = 32;
        blit(stack, destX, destY, srcX, srcY, drawWidth, drawHeight, texWidth, texHeight);
    }

    @Override
    protected void renderTooltip(
            @NotNull PoseStack stack,
            int mouseX,
            int mouseY
    ) {
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        int leftX = x + backgroundWidth - 16 - 32;
        int topY = y + 16;
        int rightX = leftX + 32;
        int botY = topY + 32;

        String jobId = menu.getRootJobId();
        TranslatableComponent jobName = new TranslatableComponent("jobs." + jobId);

        if (this.tabs.renderTooltip(
                x, y, mouseX, mouseY,
                key -> super.renderTooltip(stack, new TranslatableComponent(key), mouseX, mouseY)
        )) {
            return;
        }

        if (this.tabs.renderTooltip(
                x, y, mouseX, mouseY,
                key -> super.renderTooltip(stack, Compat.translatable(key), mouseX, mouseY)
        )) {
            return;
        }

        if (mouseX > leftX && mouseX < rightX) {
            if (mouseY > topY && mouseY < botY) {
                // TODO: Render root AND current job
                IStatus<?> status = menu.getStatus();
                @Nullable String cat = status.getCategoryId();
                if (cat == null) {
                    cat = jobId;
                }

                // TODO: Handle work seeker statuses some where else
                if (WorkSeekerJob.isSeekingWork(menu.jobId)) {
                    cat = "work_seeker";
                }

                TranslatableComponent component = new TranslatableComponent(
                        String.format("tooltips.villagers.job.%s.status_1.%s", cat, status.nameV2()),
                        jobName
                );
                TranslatableComponent component2 = new TranslatableComponent(
                        String.format("tooltips.villagers.job.%s.status_2.%s", cat, status.nameV2()),
                        jobName
                );
                super.renderTooltip(stack, ImmutableList.of(component, component2), Optional.empty(), mouseX, mouseY);
                return;
            }
        }

        int yCoord = y - 1;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            int xCoord = x - 1 + s.x;
            yCoord = y - 1 + s.y;
            if (i >= TE_INVENTORY_FIRST_SLOT_INDEX) {
                if (renderLocksTooltip(stack, xCoord + 1, yCoord + 16 + 2, mouseX, mouseY)) {
                    return;
                }
            }
        }

        int iconX = x - 12;
        for (Ingredient i : ClientJobWantedResources.wantedIngredients) {
            if (renderNeedsTooltip(stack, iconX += 16 + 4, yCoord + 32, mouseX, mouseY, i)) {
                return;
            }
        }

        super.renderTooltip(stack, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int p_97750_) {
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        this.tabs.mouseClicked(x, y ,mouseX, mouseY);
        return super.mouseClicked(mouseX, mouseY, p_97750_);
    }

    private boolean renderLocksTooltip(
            @NotNull PoseStack stack,
            int leftX,
            int topY,
            int mouseX,
            int mouseY
    ) {
        int rightX = leftX + 16;
        int botY = topY + 8;
        if (mouseX > leftX && mouseX < rightX) {
            if (mouseY > topY && mouseY < botY) {
                TranslatableComponent component = new TranslatableComponent(
                        "tooltips.villagers.job.inventory.locked"
                );
                super.renderTooltip(stack, ImmutableList.of(component), Optional.empty(), mouseX, mouseY);
                return true;
            }
        }
        return false;
    }

    private boolean renderNeedsTooltip(
            @NotNull PoseStack stack,
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
                TranslatableComponent jPart = new TranslatableComponent(String.format("jobs.%s", menu.getRootJobId()));
                TranslatableComponent component = new TranslatableComponent(
                        "tooltips.villagers.job.needs", jPart, Ingredients.getName(item)
                );
                super.renderTooltip(stack, ImmutableList.of(component), Optional.empty(), mouseX, mouseY);
                return true;
            }
        }
        return false;
    }
}