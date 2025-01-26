package ca.bradj.questown.gui;

import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.core.network.AddWorkFromUIMessage;
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
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
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
    private final ItemStack itemRequested;
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
            ResourceLocation itemRequested,
            Map<JobID, ResourceLocation> iconsForJobsWhichProduceResult,
            BlockPos flagPos
    ) {
        super(Compat.translatable("menu.work_add_confirm.title", itemRequested.getPath()));

        Textures textures = Internal.getTextures();
        this.background = textures.getRecipeGuiBackground();
        this.itemRequested = ForgeRegistries.ITEMS.getValue(itemRequested).getDefaultInstance();
        this.iconsForJobsWhichProduceResult = ImmutableList.copyOf(iconsForJobsWhichProduceResult.entrySet());
        this.requestWork = () -> this.send(flagPos, CONFIRMED);
        this.backToRequestedJobs = () -> this.send(flagPos, REJECTED);
        this.openJobsInfoScreen = () -> {
        }; // TODO[ASAP]: Open New UI
    }

    private void send(
            BlockPos p,
            AddWorkFromUIMessage.Action action
    ) {
        AddWorkFromUIMessage m = new AddWorkFromUIMessage(itemRequested, p.getX(), p.getY(), p.getZ(), action);
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
        this.addRenderableWidget(
                new Button(
                        pageLeftX + PAGE_PADDING, moreInfoButtonY,
                        buttonWidth,
                        buttonHeight,
                        Compat.translatable("menu.common.learn_more"),
                        (p_96776_) -> this.openJobsInfoScreen.run()
                )
        );

        // Bottom buttons are relative to bottom of page
        this.confirmTextY = pageTopY + backgroundHeight - (2 * PAGE_PADDING) - buttonHeight - tallLine;
        this.confirmButtonY = confirmTextY + tallLine;
        this.addRenderableWidget(
                new Button(
                        pageLeftX + PAGE_PADDING, confirmButtonY,
                        (int) (buttonWidth / 2f),
                        buttonHeight,
                        Compat.translatable("menu.common.yes"),
                        (p_96776_) -> this.requestWork.run()
                )
        );
        this.addRenderableWidget(
                new Button(
                        (int) (pageLeftX + PAGE_PADDING + (buttonWidth / 2f)), confirmButtonY,
                        (int) (buttonWidth / 2f),
                        buttonHeight,
                        Compat.translatable("menu.common.no"),
                        (p_96776_) -> this.backToRequestedJobs.run()
                )
        );
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
        int x = pageLeftX;
        x = x + PAGE_PADDING;

        int xRef = x;

        Compat.drawDarkText(this.font, stack, title, x, pageTopY);
        itemRenderer.renderAndDecorateItem(itemRequested, x, mainItemIconY);
        x += 24;
        Compat.drawDarkText(
                this.font, stack, Compat.translatable(
                            "menu.work_add_confirm.there_are_n_known_jobs",
                        iconsForJobsWhichProduceResult.size()
                ), x, mainItemTextY
        );
        String id = itemRequested.getItem().getRegistryName().getPath();
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
        int maxItemsOnX = 6;
        int lim = Math.min(iconsForJobsWhichProduceResult.size(), maxItemsOnX);
        for (int i = 0; i < lim; i++) {
            Map.Entry<JobID, ResourceLocation> ii = iconsForJobsWhichProduceResult.get(i);
            ResourceLocation item = ii.getValue();
            ItemStack v = ForgeRegistries.ITEMS.getValue(item).getDefaultInstance();
            int itemX = xRef + (24 * i);
            itemRenderer.renderAndDecorateItem(v, itemX, y);
            if (UtilClean.mouseInBox(mouseX, mouseY, itemX, y, 16, 16)) {
                fill(stack, itemX, y, itemX + 16, y + 16, 0x80FFFFFF);
                renderTooltip(stack, Jobs.getComponentsForTooltip(ii.getKey()), Optional.empty(), itemX + 8, y - 6);
            }
        }
        if (iconsForJobsWhichProduceResult.size() > maxItemsOnX) {
            int itemX = xRef + (24 * maxItemsOnX);
            Compat.drawDarkText(font, stack, Compat.literal("…"), itemX + 4, y + 2);
            if (UtilClean.mouseInBox(mouseX, mouseY, itemX, y, 16, 16)) {
                fill(stack, itemX, y, itemX + 16, y + 16, 0x80FFFFFF);
                TranslatableComponent andMore = Compat.translatable(
                        "menu.work_add_confirm.and_n_more",
                        iconsForJobsWhichProduceResult.size() - maxItemsOnX
                );
                renderTooltip(stack, andMore, itemX + 8, y);
            }
        }
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
        return ImmutableList.of(
                new Rect2i(x, y, backgroundWidth, backgroundHeight)
        );
    }
}