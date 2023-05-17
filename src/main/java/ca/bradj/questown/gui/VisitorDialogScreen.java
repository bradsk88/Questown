package ca.bradj.questown.gui;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.Internal;
import mezz.jei.gui.elements.DrawableNineSliceTexture;
import mezz.jei.gui.textures.Textures;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class VisitorDialogScreen extends AbstractContainerScreen<VisitorQuestsContainer> {
    private static final int backgroundWidth = 176;
    private static final int backgroundHeight = 166;

    private static final int PAGE_PADDING = 10;

    private final DrawableNineSliceTexture background;

    public VisitorDialogScreen(
            VisitorQuestsContainer container,
            Inventory playerInv,
            Component title
    ) {
        super(container, playerInv, title);
        super.imageWidth = 256;
        super.imageHeight = 220;

        Textures textures = Internal.getTextures();
        this.background = textures.getRecipeGuiBackground();
    }

    @Override
    protected void init() {
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
            PoseStack poseStack,
            int mouseX,
            int mouseY,
            float partialTicks
    ) {
        this.renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTicks);

        int x = ((this.width - backgroundWidth) / 2);
        int y = (this.height - backgroundHeight) / 2;
        int pageStringY = y + PAGE_PADDING;
        y = pageStringY + PAGE_PADDING;
        x = x + PAGE_PADDING;

        TranslatableComponent intro = new TranslatableComponent(
                "dialog.visitors.first_contact.intro"
        );
        TranslatableComponent request = new TranslatableComponent(
                "dialog.visitors.first_contact.request"
        );
        TranslatableComponent instruction = new TranslatableComponent(
                "dialog.visitors.first_contact.instruction"
        );
        TextComponent str = new TextComponent(String.format(
                "%s%n%n%s%n%n(%s)",
                intro.getString(), request.getString(), instruction.getString()
        ));
        this.font.drawWordWrap(
                str, x, y,
                backgroundWidth - (2 * PAGE_PADDING),
                backgroundHeight - (2 * PAGE_PADDING)
        );
    }

    @Override
    protected void renderBg(
            PoseStack poseStack,
            float partialTicks,
            int mouseX,
            int mouseY
    ) {
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
        return ImmutableList.of(
                new Rect2i(x, y, backgroundWidth, backgroundHeight)
        );
    }
}