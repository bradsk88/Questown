package ca.bradj.questown.gui;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.elements.DrawableNineSliceTexture;
import mezz.jei.common.gui.textures.Textures;
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
    private Component dialog;

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
        Component str;
        if (this.container.finishedQuests() > 0 && this.container.unfinishedQuests() > 0) {
            str = getVisitorMidQuestsMessage();
        }
        else if (this.container.finishedQuests() > 0 && this.container.unfinishedQuests() == 0) {
            str = getVisitorDoneQuestsMessage();
        }
        else if (this.container.isFirstVisitor() && this.container.isNewVisitor()) {
            str = getFirstVisitorMessage();
        }
        else if (this.container.isNewVisitor()) {
            str = getNewVisitorMessage();
        }
        else {
            str = getGenericVisitorMessage(random);
        }

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

    private Component getGenericVisitorMessage(Random random) {
        String intro = String.format("dialog.visitors.generic.%d", random.nextInt(45)+1);
        return Component.translatable(intro);
    }

    private Component getNewVisitorMessage() {
        String intro = String.format("dialog.visitors.new_visitor_introduction.%d", random.nextInt(8)+1);
        String request = String.format("dialog.visitors.new_visitor_request.%d", random.nextInt(10)+1);
        String closing = String.format("dialog.visitors.new_visitor_closing.%d", random.nextInt(10)+1);
        String instruction = "dialog.visitors.instruction.flag_quests";
        return Component.literal(String.format(
                "%s%n%n%s%n%n%s%n%n(%s)",
                Component.translatable(intro).getString(),
                Component.translatable(request).getString(),
                Component.translatable(closing).getString(),
                Component.translatable(instruction).getString()
        ));
    }

    @NotNull
    private static Component getVisitorMidQuestsMessage() {
        Component intro = Component.translatable(
                "dialog.visitors.some_quests_done.1"
        );
        Component instruction = Component.translatable(
                "dialog.visitors.instruction.flag_quests"
        );
        return Component.literal(String.format(
                "%s%n%n(%s)",
                intro.getString(),
                instruction.getString()
        ));
    }

    @NotNull
    private static Component getVisitorDoneQuestsMessage() {
        Component intro = Component.translatable(
                "dialog.visitors.all_quests_done.thanks.1"
        );
        Component outcome = Component.translatable(
                "dialog.visitors.all_quests_done.outcome.1"
        );
        Component instruction = Component.translatable(
                "dialog.visitors.instruction.sleep_visitors"
        );
        return Component.literal(String.format(
                "%s%n%n%s%n%n(%s)",
                intro.getString(),
                outcome.getString(),
                instruction.getString()
        ));
    }

    @NotNull
    private static Component getFirstVisitorMessage() {
        Component intro = Component.translatable(
                "dialog.visitors.first_contact.intro"
        );
        Component request = Component.translatable(
                "dialog.visitors.first_contact.request"
        );
        Component page = Component.translatable(
                "dialog.visitors.first_contact.page"
        );
        Component instruction = Component.translatable(
                "dialog.visitors.instruction.flag_quests"
        );
        return Component.literal(String.format(
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