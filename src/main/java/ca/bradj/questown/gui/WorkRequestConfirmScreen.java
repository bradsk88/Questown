package ca.bradj.questown.gui;

import ca.bradj.questown.core.Coordinate;
import ca.bradj.questown.core.network.AddWorkFromUIMessage;
import ca.bradj.questown.core.network.OpenItemJobsMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Jobs;
import ca.bradj.questown.mc.Compat;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.Internal;
import mezz.jei.gui.elements.DrawableNineSliceTexture;
import mezz.jei.gui.textures.Textures;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static ca.bradj.questown.core.network.AddWorkFromUIMessage.Action.CONFIRMED;
import static ca.bradj.questown.core.network.AddWorkFromUIMessage.Action.REJECTED;

public class WorkRequestConfirmScreen extends Screen {
    private static final int backgroundWidth = 176;
    private static final int backgroundHeight = 166;

    private static final int PAGE_PADDING = 10;

    private final DrawableNineSliceTexture background;
    private final ImmutableList<Map.Entry<JobID, ResourceLocation>> iconsForJobsWhichProduceResult;
    private final Ingredient itemRequested;
    private final Runnable requestWork;
    private final Runnable backToRequestedJobs;
    private final Runnable openJobsInfoScreen;

    // Page coordinates
    private int pageTopY;
    private int mainItemIconY;
    private int mainItemTextY;
    private int mainItemTextY2;
    private int jobIconsY;
    private int pageLeftX;
    private int moreInfoButtonY;
    private int confirmTextY;
    private int confirmButtonY;

    public WorkRequestConfirmScreen(
            Ingredient requested,
            Map<JobID, ResourceLocation> iconsForJobsWhichProduceResult,
            BlockPos flagPos
    ) {
        super(Compat.translatable("menu.work_add_confirm.title", Compat.translatable(Ingredients.toString(requested))));

        Textures textures = Internal.getTextures();
        this.background = textures.getRecipeGuiBackground();
        this.itemRequested = requested;
        this.iconsForJobsWhichProduceResult = ImmutableList.copyOf(iconsForJobsWhichProduceResult.entrySet());
        this.requestWork = () -> send(flagPos, CONFIRMED);
        this.backToRequestedJobs = () -> this.send(flagPos, REJECTED);
        this.openJobsInfoScreen = () -> {
            OpenItemJobsMessage msg = new OpenItemJobsMessage(flagPos, this.itemRequested);
            QuestownNetwork.CHANNEL.sendToServer(msg);
        };
    }

    private void send(
            BlockPos p,
            AddWorkFromUIMessage.Action action
    ) {
        AddWorkFromUIMessage m = new AddWorkFromUIMessage(itemRequested, p, action);
        QuestownNetwork.CHANNEL.sendToServer(m);
    }

    @Override
    protected void init() {
        this.pageTopY = ((this.height - backgroundHeight) / 2) + PAGE_PADDING;
        this.pageLeftX = ((this.width - backgroundWidth) / 2);
        this.mainItemIconY = pageTopY + (2 * font.lineHeight);
        this.mainItemTextY = (int) (mainItemIconY + (0.5 * font.lineHeight));
        int tallLine = (int) (font.lineHeight * 1.5);
        this.mainItemTextY2 = mainItemTextY + tallLine;
        this.jobIconsY = mainItemTextY2 + tallLine;
        this.moreInfoButtonY = jobIconsY + 24;
        int buttonHeight = (2 * font.lineHeight) + 2;
        int buttonWidth = backgroundWidth - (2 * PAGE_PADDING);
        this.addRenderableWidget(new Button(
                pageLeftX + PAGE_PADDING,
                moreInfoButtonY,
                buttonWidth,
                buttonHeight,
                Compat.translatable("menu.common.learn_more"),
                (p_96776_) -> this.openJobsInfoScreen.run()
        ));

        // Bottom buttons are relative to bottom of page
        this.confirmTextY = pageTopY + backgroundHeight - (2 * PAGE_PADDING) - buttonHeight - tallLine;
        this.confirmButtonY = confirmTextY + tallLine;
        this.addRenderableWidget(new Button(
                pageLeftX + PAGE_PADDING,
                confirmButtonY,
                (int) (buttonWidth / 2f),
                buttonHeight,
                Compat.translatable("menu.common.yes"),
                (p_96776_) -> this.requestWork.run()
        ));
        this.addRenderableWidget(new Button(
                (int) (pageLeftX + PAGE_PADDING + (buttonWidth / 2f)),
                confirmButtonY,
                (int) (buttonWidth / 2f),
                buttonHeight,
                Compat.translatable("menu.common.no"),
                (p_96776_) -> this.backToRequestedJobs.run()
        ));
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
        this.fillGradient(stack, 0, 0, this.width, this.height, -1072689136, -804253680);
        this.renderBackground(stack);
        super.render(stack, mouseX, mouseY, partialTicks);
        int x = pageLeftX;
        x = x + PAGE_PADDING;

        int xRef = x;

        Compat.drawDarkText(this.font, stack, title, x, pageTopY);
        Ingredients.render(itemRenderer, itemRequested, x, mainItemIconY);
        x += 24;
        Compat.drawDarkText(
                this.font,
                stack,
                Compat.translatable(
                        "menu.work_add_confirm.there_are_n_known_jobs",
                        iconsForJobsWhichProduceResult.size()
                ),
                x,
                mainItemTextY
        );
        TranslatableComponent translatable = Compat.translatable("menu.work_add_confirm.which_produce_this_item");
        Compat.drawDarkText(this.font, stack, translatable, x, mainItemTextY2);
        renderJobIcons(stack, mouseX, mouseY, xRef, jobIconsY);
        TranslatableComponent confTxt = Compat.translatable("menu.work_add_confirm.confirm_text");
        Compat.drawDarkText(this.font, stack, confTxt, xRef, confirmTextY);
    }

    private void renderJobIcons(
            PoseStack stack,
            int mouseX,
            int mouseY,
            int xRef,
            int y
    ) {
        RenderUtil.stripOfRequestableItems(
                (ingr, coord) -> {
                    Ingredient ing = Ingredients.fromRL(ingr.getValue());
                    Ingredients.render(itemRenderer, ing, coord.x(), coord.y());
                },
                (ingr) -> Jobs.getComponentsForTooltip(ingr.getKey()),
                (text, coord) -> Compat.drawDarkText(font, stack, text, coord.x(), coord.y()),
                (text, coord) -> renderTooltip(stack, text, Optional.empty(), coord.x(), coord.y()),
                (topLeft, botRight) -> RenderUtil.highlight(stack, topLeft, botRight),
                iconsForJobsWhichProduceResult,
                new Coordinate(xRef, y),
                new Coordinate(xRef + backgroundWidth - (PAGE_PADDING * 2), y),
                new Coordinate(mouseX, mouseY)
        );
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