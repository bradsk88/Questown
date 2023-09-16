package ca.bradj.questown.gui;

import ca.bradj.questown.jobs.GathererJournal;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.Internal;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.gui.elements.DrawableNineSliceTexture;
import mezz.jei.gui.textures.Textures;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

import static ca.bradj.questown.gui.InventoryAndStatusMenu.TE_INVENTORY_FIRST_SLOT_INDEX;

public class InventoryAndStatusScreen extends AbstractContainerScreen<InventoryAndStatusMenu> {

    private static final int backgroundWidth = 176;
    private static final int backgroundHeight = 166;

    private final DrawableNineSliceTexture background;
    private final IDrawableStatic slot;
    private final ResourceLocation lockTex;

    public InventoryAndStatusScreen(
            InventoryAndStatusMenu gathererInv,
            Inventory playerInv,
            Component title
    ) {
        super(gathererInv, playerInv, title);
        Textures textures = Internal.getTextures();
        this.background = textures.getRecipeGuiBackground();
        this.slot = textures.getSlotDrawable();
        this.lockTex = new ResourceLocation("questown", "textures/menu/gatherer/locked.png");
    }

    @NotNull
    private static ResourceLocation getStatusTexture(GathererJournal.Status status) {
        return switch (status) {
            case UNSET -> new ResourceLocation("questown", "textures/error.png");
            case IDLE -> new ResourceLocation("questown", "textures/menu/gatherer/idle.png");
            case NO_SPACE -> new ResourceLocation("questown", "textures/menu/gatherer/no_space.png");
            case NO_FOOD -> new ResourceLocation("questown", "textures/menu/gatherer/no_food.png");
            case NO_GATE -> new ResourceLocation("questown", "textures/menu/gatherer/no_gate.png");
            case STAYING ->
                // TODO: Icon for this
                    new ResourceLocation("questown", "textures/menu/gatherer/idle.png");
            case GATHERING -> new ResourceLocation("questown", "textures/menu/gatherer/leaving.png");
            case RETURNED_SUCCESS -> new ResourceLocation("questown", "textures/menu/gatherer/returned_success.png");
            case RETURNED_FAILURE ->
                // TODO: Icon for this
                    new ResourceLocation("questown", "textures/error.png");
            case RETURNING ->
                // TODO: Icon for this
                    new ResourceLocation("questown", "textures/error.png");
            case CAPTURED ->
                // TODO: Icon for this
                    new ResourceLocation("questown", "textures/error.png");
            case RELAXING ->
                // TODO: Icon for this
                    new ResourceLocation("questown", "textures/menu/gatherer/relaxing.png");
            case DROPPING_LOOT, GATHERING_EATING, GATHERING_HUNGRY, RETURNING_AT_NIGHT ->
                // TODO: Icon for this
                    new ResourceLocation("questown", "textures/error.png");
            case FARMING, WALKING_TO_FARM, COLLECTING_SUPPLIES,
                    GOING_TO_BAKERY, NO_SUPPLIES, BAKING, COLLECTING_BREAD ->
                // TODO: Icon for this
                    new ResourceLocation("questown", "textures/error.png");
        };
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
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
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
        renderStatus(stack);
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            int xCoord = x - 1 + s.x;
            int yCoord = y - 1 + s.y;
            this.slot.draw(stack, xCoord, yCoord);
            if (i >= TE_INVENTORY_FIRST_SLOT_INDEX) {
                // TODO: Compute or provide this value (6)
                int statusI = i - TE_INVENTORY_FIRST_SLOT_INDEX;
                renderSlotStatus(stack, menu.lockedSlots.get(statusI), xCoord + 1, yCoord + 16 + 2);
            }
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
        RenderSystem.setShaderTexture(0, getStatusTexture(menu.getStatus()));
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
        if (mouseX > leftX && mouseX < rightX) {
            if (mouseY > topY && mouseY < botY) {
                TranslatableComponent component = new TranslatableComponent(
                        "tooltips.villagers.job.gatherer.status_1." + menu.getStatus().name(), menu.getJobName()
                );
                TranslatableComponent component2 = new TranslatableComponent(
                        "tooltips.villagers.job.gatherer.status_2." + menu.getStatus().name(), menu.getJobName()
                );
                super.renderTooltip(stack, ImmutableList.of(component, component2), Optional.empty(), mouseX, mouseY);
                return;
            }
        }

        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            int xCoord = x - 1 + s.x;
            int yCoord = y - 1 + s.y;
            if (i >= TE_INVENTORY_FIRST_SLOT_INDEX) {
                if (renderLocksTooltip(stack, xCoord + 1, yCoord + 16 + 2, mouseX, mouseY)) {
                    return;
                }
            }
        }

        super.renderTooltip(stack, mouseX, mouseY);
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
}