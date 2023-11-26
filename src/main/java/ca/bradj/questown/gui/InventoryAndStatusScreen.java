package ca.bradj.questown.gui;

import ca.bradj.questown.jobs.IStatus;
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
    private final QuestsScreen questScreen;

    public InventoryAndStatusScreen(
            InventoryAndStatusMenu gathererInv,
            Inventory playerInv,
            Component title
    ) {
        super(gathererInv, playerInv, title);
        this.questScreen = new QuestsScreen(menu.questMenu, playerInv, title);
        Textures textures = Internal.getTextures();
        this.background = textures.getRecipeGuiBackground();
        this.slot = textures.getSlotDrawable();
        this.lockTex = new ResourceLocation("questown", "textures/menu/gatherer/locked.png");
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
        this.minecraft.setScreen(questScreen);
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
        RenderSystem.setShaderTexture(0, StatusArt.getTexture(menu.getStatus()));
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
                // TODO[ASAP]: Render root AND current job
                String jobId = menu.getRootJobId();
                TranslatableComponent jobName = new TranslatableComponent("jobs." + jobId);
                IStatus<?> status = menu.getStatus();
                @Nullable String cat = status.getCategoryId();
                if (cat == null) {
                    cat = jobId;
                }
                TranslatableComponent component = new TranslatableComponent(
                        String.format("tooltips.villagers.job.%s.status_1.%s", cat, status.name()),
                        jobName
                );
                TranslatableComponent component2 = new TranslatableComponent(
                        String.format("tooltips.villagers.job.%s.status_2.%s", cat, status.name()),
                        jobName
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