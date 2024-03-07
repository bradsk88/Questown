package ca.bradj.questown.gui.villager.advancements;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

public class VillagerAdvancementsScreen extends Screen {
    private static final ResourceLocation WINDOW_LOCATION = new ResourceLocation("textures/gui/advancements/window.png");
    private static final ResourceLocation TABS_LOCATION = new ResourceLocation("textures/gui/advancements/tabs.png");
    public static final int WINDOW_WIDTH = 252;
    public static final int WINDOW_HEIGHT = 140;
    private static final int WINDOW_INSIDE_X = 9;
    private static final int WINDOW_INSIDE_Y = 18;
    public static final int WINDOW_INSIDE_WIDTH = 234;
    public static final int WINDOW_INSIDE_HEIGHT = 113;
    private static final int WINDOW_TITLE_X = 8;
    private static final int WINDOW_TITLE_Y = 6;
    public static final int BACKGROUND_TILE_WIDTH = 16;
    public static final int BACKGROUND_TILE_HEIGHT = 16;
    public static final int BACKGROUND_TILE_COUNT_X = 14;
    public static final int BACKGROUND_TILE_COUNT_Y = 7;
    private static final Component VERY_SAD_LABEL = Component.translatable("advancements.sad_label");
    private static final Component NO_ADVANCEMENTS_LABEL = Component.translatable("advancements.empty");
    private static final Component TITLE = Component.translatable("gui.advancements");
    @Nullable
    private AdvancementTab selectedTab;
    private boolean isScrolling;
    private static int tabPage;
    private static int maxPages;

    public VillagerAdvancementsScreen() {
        super(GameNarrator.NO_TITLE);
    }

    protected void init() {
        this.selectedTab = null;
        // TODO: Listen for real-time updates
//        this.advancements.setListener(this);
    }

    public void render(PoseStack p_97361_, int p_97362_, int p_97363_, float p_97364_) {
        int i = (this.width - 252) / 2;
        int j = (this.height - 140) / 2;
        this.renderBackground(p_97361_);
        if (maxPages != 0) {
            Component page = Component.literal(String.format("%d / %d", tabPage + 1, maxPages + 1));
            int width = this.font.width(page);
            this.font.drawShadow(p_97361_, page.getVisualOrderText(), (float)(i + 126 - width / 2), (float)(j - 44), -1);
        }

        this.renderInside(p_97361_, p_97362_, p_97363_, i, j);
        this.renderWindow(p_97361_, i, j);
        this.renderTooltips(p_97361_, p_97362_, p_97363_, i, j);
    }

    public boolean mouseDragged(double p_97347_, double p_97348_, int p_97349_, double p_97350_, double p_97351_) {
        if (p_97349_ != 0) {
            this.isScrolling = false;
            return false;
        } else {
            if (!this.isScrolling) {
                this.isScrolling = true;
            } else if (this.selectedTab != null) {
                this.selectedTab.scroll(p_97350_, p_97351_);
            }

            return true;
        }
    }

    private void renderInside(PoseStack p_97374_, int p_97375_, int p_97376_, int p_97377_, int p_97378_) {
        AdvancementTab advancementtab = this.selectedTab;
        if (advancementtab == null) {
            fill(p_97374_, p_97377_ + 9, p_97378_ + 18, p_97377_ + 9 + 234, p_97378_ + 18 + 113, -16777216);
            int i = p_97377_ + 9 + 117;
            drawCenteredString(p_97374_, this.font, NO_ADVANCEMENTS_LABEL, i, p_97378_ + 18 + 56 - 4, -1);
            drawCenteredString(p_97374_, this.font, VERY_SAD_LABEL, i, p_97378_ + 18 + 113 - 9, -1);
        } else {
            PoseStack posestack = RenderSystem.getModelViewStack();
            posestack.pushPose();
            posestack.translate((double)(p_97377_ + 9), (double)(p_97378_ + 18), 0.0);
            RenderSystem.applyModelViewMatrix();
            advancementtab.drawContents(p_97374_);
            posestack.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.depthFunc(515);
            RenderSystem.disableDepthTest();
        }

    }

    public void renderWindow(PoseStack p_97357_, int p_97358_, int p_97359_) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, WINDOW_LOCATION);
        this.blit(p_97357_, p_97358_, p_97359_, 0, 0, 252, 140);

        this.font.draw(p_97357_, TITLE, (float)(p_97358_ + 8), (float)(p_97359_ + 6), 4210752);
    }

    private void renderTooltips(PoseStack p_97382_, int p_97383_, int p_97384_, int p_97385_, int p_97386_) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        if (this.selectedTab != null) {
            PoseStack posestack = RenderSystem.getModelViewStack();
            posestack.pushPose();
            posestack.translate((double)(p_97385_ + 9), (double)(p_97386_ + 18), 400.0);
            RenderSystem.applyModelViewMatrix();
            RenderSystem.enableDepthTest();
            this.selectedTab.drawTooltips(p_97382_, p_97383_ - p_97385_ - 9, p_97384_ - p_97386_ - 18, p_97385_, p_97386_);
            RenderSystem.disableDepthTest();
            posestack.popPose();
            RenderSystem.applyModelViewMatrix();
        }

    }
}