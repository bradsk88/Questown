package ca.bradj.questown.gui;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.Internal;
import mezz.jei.gui.elements.DrawableNineSliceTexture;
import mezz.jei.gui.textures.Textures;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.*;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Random;

public class VisitorDialogScreen extends AbstractContainerScreen<VisitorQuestsContainer> {
    private static final int backgroundWidth = 176;
    private static final int backgroundHeight = 166;

    private static final int PAGE_PADDING = 10;

    private final DrawableNineSliceTexture background;
    private final VisitorQuestsContainer container;
    private final Random random;
    private BaseComponent dialog;

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
        this.container = container;
        this.random = new Random();
    }

    @Override
    protected void init() {
        BaseComponent str;
        if (this.container.isFirstVisitor() && this.container.isNewVisitor()) {
            str = getFirstVisitorMessage();
        }
        else if (this.container.isNewVisitor()) {
            str = getNewVisitorMessage();
        }
        else {
            str = getGenericVisitorMessage(random);
        }

        str.setStyle(Style.EMPTY.withColor(ChatFormatting.BLACK));
        this.dialog = str;
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
        int y = ((this.height - backgroundHeight) / 2) + PAGE_PADDING;
        x = x + PAGE_PADDING;

        this.font.drawWordWrap(
                dialog, x, y,
                backgroundWidth - (2 * PAGE_PADDING),
                backgroundHeight - (2 * PAGE_PADDING)
        );
    }

    private BaseComponent getGenericVisitorMessage(Random random) {
        String intro = String.format("dialog.visitors.generic.%d", random.nextInt(45)+1);
        return new TranslatableComponent(intro);
    }

    private BaseComponent getNewVisitorMessage() {
        String intro = String.format("dialog.visitors.new_visitor_introduction.%d", random.nextInt(8)+1);
        String request = String.format("dialog.visitors.new_visitor_request.%d", random.nextInt(10)+1);
        String closing = String.format("dialog.visitors.new_visitor_closing.%d", random.nextInt(10)+1);
        String instruction = "dialog.visitors.instruction.flag_quests";
        return new TextComponent(String.format(
                "%s%n%n%s%n%n%s%n%n(%s)",
                new TranslatableComponent(intro).getString(),
                new TranslatableComponent(request).getString(),
                new TranslatableComponent(closing).getString(),
                new TranslatableComponent(instruction).getString()
        ));
    }

    @NotNull
    private static BaseComponent getFirstVisitorMessage() {
        TranslatableComponent intro = new TranslatableComponent(
                "dialog.visitors.first_contact.intro"
        );
        TranslatableComponent request = new TranslatableComponent(
                "dialog.visitors.first_contact.request"
        );
        TranslatableComponent page = new TranslatableComponent(
                "dialog.visitors.first_contact.page"
        );
        TranslatableComponent instruction = new TranslatableComponent(
                "dialog.visitors.instruction.flag_quests"
        );
        return new TextComponent(String.format(
                "%s%n%n%s%n%n%s%n%n(%s)",
                intro.getString(), request.getString(), page.getString(),
                instruction.getString()
        ));
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