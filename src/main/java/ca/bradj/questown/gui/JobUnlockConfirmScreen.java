package ca.bradj.questown.gui;

import ca.bradj.questown.core.Pair;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.core.network.UnlockJobMessage;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.JEI;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.UUID;

public class JobUnlockConfirmScreen extends Screen {
    private static final int backgroundWidth = 176;
    private static final int backgroundHeight = 166;

    private static final int PAGE_PADDING = 10;

    private final JEI.NineNine background;
    private final BlockPos flagPos;
    private final UUID uuid;
    private final JobID jobRequested;
    private final Item icon;

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

    public JobUnlockConfirmScreen(
            Pair<JobID, Item> requested,
            BlockPos flagPos,
            UUID uuid
    ) {
        super(Compat.translatable("menu.job_unlock_confirm.title"));

        this.background = JEI.getRecipeGuiBackground();
        this.jobRequested = requested.a();
        this.icon = requested.b();
        this.flagPos = flagPos;
        this.uuid = uuid;
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

        // Bottom buttons are relative to bottom of page
        this.confirmTextY = pageTopY + backgroundHeight - (2 * PAGE_PADDING) - buttonHeight - tallLine;
        this.confirmButtonY = confirmTextY + tallLine;
        this.addRenderableWidget(new Button(
                pageLeftX + PAGE_PADDING,
                confirmButtonY,
                (int) (buttonWidth / 2f),
                buttonHeight,
                Compat.translatable("menu.common.unlock"),
                (p_96776_) -> {
                    QuestownNetwork.CHANNEL.sendToServer(new UnlockJobMessage(flagPos, uuid, jobRequested));
                    Minecraft.getInstance().setScreen(null);
                }
        ));
        this.addRenderableWidget(new Button(
                (int) (pageLeftX + PAGE_PADDING + (buttonWidth / 2f)),
                confirmButtonY,
                (int) (buttonWidth / 2f),
                buttonHeight,
                Compat.translatable("menu.common.close"),
                (p_96776_) -> Minecraft.getInstance().setScreen(null)
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
        this.renderBackground(stack);
        super.render(stack, mouseX, mouseY, partialTicks);
        // TODO: Render
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