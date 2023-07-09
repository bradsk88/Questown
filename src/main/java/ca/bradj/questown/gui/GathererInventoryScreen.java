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
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class GathererInventoryScreen extends AbstractContainerScreen<GathererInventoryMenu> {

    private static final int backgroundWidth = 176;
    private static final int backgroundHeight = 166;

    private final DrawableNineSliceTexture background;
    private final IDrawableStatic slot;

    public GathererInventoryScreen(GathererInventoryMenu gathererInv, Inventory playerInv, Component title) {
        super(gathererInv, playerInv, title);
        Textures textures = Internal.getTextures();
        this.background = textures.getRecipeGuiBackground();
        this.slot = textures.getSlotDrawable();
    }

    @NotNull
    private static ResourceLocation getStatusTexture(GathererJournal.Status status) {
        switch(status) {
            case UNSET -> {
                return new ResourceLocation("questown", "textures/error.png");
            }
            case IDLE -> {
                return new ResourceLocation("questown", "textures/menu/gatherer/idle.png");
            }
            case NO_SPACE -> {
                return new ResourceLocation("questown", "textures/menu/gatherer/no_space.png");
            }
            case NO_FOOD -> {
                return new ResourceLocation("questown", "textures/menu/gatherer/no_food.png");
            }
            case STAYING -> {
                // TODO: Icon for this
                return new ResourceLocation("questown", "textures/menu/gatherer/idle.png");
            }
            case GATHERING -> {
                return new ResourceLocation("questown", "textures/menu/gatherer/leaving.png");
            }
            case RETURNED_SUCCESS -> {
                return new ResourceLocation("questown", "textures/menu/gatherer/returned_success.png");
            }
            case RETURNED_FAILURE -> {
                // TODO: Icon for this
                return new ResourceLocation("questown", "textures/error.png");
            }
            case RETURNING -> {
                // TODO: Icon for this
                return new ResourceLocation("questown", "textures/error.png");
            }
            case CAPTURED -> {
                // TODO: Icon for this
                return new ResourceLocation("questown", "textures/error.png");
            }
            case RELAXING -> {
                // TODO: Icon for this
                return new ResourceLocation("questown", "textures/menu/gatherer/relaxing.png");
            }
        }
        return new ResourceLocation("questown", "textures/error.png");
    }

    @Override
    public void render(PoseStack stack, int mouseX, int mouseY, float partialTicks) {
        super.renderBackground(stack);
        super.render(stack, mouseX, mouseY, partialTicks);
        this.renderTooltip(stack, mouseX, mouseY);
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
    }

    @Override
    protected void renderBg(PoseStack stack, float p_230450_2_, int p_230450_3_, int p_230450_4_) {
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        this.background.draw(stack, x, y, backgroundWidth, backgroundHeight);
        renderStatus(stack);
        for (Slot s : menu.slots) {
            this.slot.draw(stack, x - 1 + s.x, y - 1 + s.y);
        }
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
                        "tooltips.villagers.job.gatherer.status_1." + menu.getStatus().name()
                );
                TranslatableComponent component2 = new TranslatableComponent(
                        "tooltips.villagers.job.gatherer.status_2." + menu.getStatus().name()
                );
                super.renderTooltip(stack, ImmutableList.of(component, component2), Optional.empty(), mouseX, mouseY);
                return;
            }
        }
        super.renderTooltip(stack, mouseX, mouseY);
    }
}