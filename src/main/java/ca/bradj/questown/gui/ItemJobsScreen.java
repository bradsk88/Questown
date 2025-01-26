package ca.bradj.questown.gui;

import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.Internal;
import mezz.jei.gui.elements.DrawableNineSliceTexture;
import mezz.jei.gui.textures.Textures;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class ItemJobsScreen extends Screen {
    private static final int backgroundWidth = 176;
    private static final int backgroundHeight = 166;

    private static final int PAGE_PADDING = 10;

    private final DrawableNineSliceTexture background;
    private final List<UIJob> jobs;

    public ItemJobsScreen(
            Collection<UIJob> jobs
    ) {
        super(Compat.translatable("menu.work_add_confirm.title"));

        Textures textures = Internal.getTextures();
        this.background = textures.getRecipeGuiBackground();
        this.jobs = ImmutableList.copyOf(jobs);
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

    @Override
    public void render(
            PoseStack stack,
            int mouseX,
            int mouseY,
            float partialTicks
    ) {
        this.renderBackground(stack);
        super.render(stack, mouseX, mouseY, partialTicks);

        @NotNull ImmutableList<AbstractPagedCardScreen.Card<UIJob>> cards = AbstractPagedCardScreen.getCardLayout(
                width, height, backgroundWidth, backgroundHeight, 0, // TODO: Add Paginator
                () -> jobs
        );


        for (AbstractPagedCardScreen.Card<UIJob> card : cards) {
            AbstractPagedCardScreen.CardCoordinates c = card.coords();
            // TODO: Translate
            int x = c.leftX();
            int i = 0;
            UIJob d = card.data();
            Compat.drawDarkText(font, stack, Compat.literal("Items"), x, c.topYPadded());
            for (Ingredient ing : d.ingredients()) {
                Ingredients.render(itemRenderer, ing, c.leftXPadded() + (i++ * 24), (c = c.shiftedDown()).topYPadded());
                // TODO: Hihlight and tooltip
            }
            Compat.drawDarkText(font, stack, Compat.literal("Tools"), x, (c = c.shiftedDown()).topYPadded());
            i = 0;
            for (Ingredient ing : d.tools()) {
                Ingredients.render(itemRenderer, ing, c.leftXPadded() + (i++ * 24), (c = c.shiftedDown()).topYPadded());
                // TODO: Hihlight and tooltip
            }
            Compat.drawDarkText(font, stack, Compat.literal("Output"), x, (c = c.shiftedDown()).topYPadded());
            this.itemRenderer.renderAndDecorateItem(d.output(), c.leftXPadded(), (c = c.shiftedDown()).topYPadded());
            Compat.drawDarkText(font, stack, Compat.literal("Villagers"), x, (c = c.shiftedDown()).topYPadded());
            i = 0;
            for (UUID uuid : d.villagersWhoCanDoJob()) {
                blitFace(stack, c.shiftedDown(), uuid, i++);
            }
            // TODO: Render each card
        }
    }

    private static void blitFace(
            PoseStack stack,
            AbstractPagedCardScreen.CardCoordinates card,
            UUID uuid,
            int i
    ) {
        Util.blitFace(stack, uuid, card.leftXPadded() + (Util.faceWidth * i), card.shiftedDown().topYPadded());
    }

    @Override
    public void renderBackground(PoseStack poseStack) {
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        this.background.draw(poseStack, x, y, backgroundWidth, backgroundHeight);
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
}