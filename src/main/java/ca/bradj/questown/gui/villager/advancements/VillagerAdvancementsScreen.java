package ca.bradj.questown.gui.villager.advancements;

import ca.bradj.questown.core.network.ChangeVillagerJobMessage;
import ca.bradj.questown.core.network.OpenVillagerMenuMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.core.network.UnlockJobMessage;
import ca.bradj.questown.gui.RenderContext;
import ca.bradj.questown.gui.VillagerTabs;
import ca.bradj.questown.gui.VillagerTabsEmbedding;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.mc.Compat;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.FrameType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

import java.util.Collection;
import java.util.UUID;

public class VillagerAdvancementsScreen extends Screen {
    private static final ResourceLocation WINDOW_LOCATION = new ResourceLocation("textures/gui/advancements/window.png");
    private static final Component VERY_SAD_LABEL = Compat.translatable("advancements.sad_label");
    private static final Component NO_ADVANCEMENTS_LABEL = Compat.translatable("advancements.empty");
    private static final Component TITLE = Compat.translatable("menu.jobs");
    public static final int backgroundWidth = 252;
    public static final int backgroundHeight = 140;
    private final BlockPos flagPos;
    private final UUID villagerUUID;
    private final VillagerAdvancementsContent content;
    private final VillagerTabs tabs;
    private boolean isScrolling;

    public VillagerAdvancementsScreen(
            BlockPos flagPos,
            UUID villagerUUID,
            Collection<JobID> unlockedJobs,
            JobID currentJob,
            boolean showBlockOfProgressTab
    ) {
        super(Compat.literal(""));
        DisplayInfo displayInfo = new DisplayInfo(
                Items.CREEPER_HEAD.getDefaultInstance(),
                Compat.literal("test"),
                Compat.literal("test2"),
                new ResourceLocation("textures/gui/advancements/backgrounds/stone.png"),
                FrameType.TASK,
                false,
                false,
                false
        );
        this.content = new VillagerAdvancementsContent(
                Minecraft.getInstance(),
                this,
                displayInfo,
                currentJob,
                unlockedJobs,
                currentJob == null ? VillagerAdvancements.all() : VillagerAdvancements.all().branch(currentJob.rootId())
        );
        this.flagPos = flagPos;
        this.villagerUUID = villagerUUID;
        this.tabs = VillagerTabs.forMenu(new VillagerTabsEmbedding() {
            @Override
            public Collection<String> getEnabledTabs() {
                return VillagerTabs.all();
            }

            @Override
            public BlockPos getFlagPos() {
                return flagPos;
            }

            @Override
            public UUID getVillagerUUID() {
                return villagerUUID;
            }

            @Override
            public boolean showBlockOfProgressTab() {
                return showBlockOfProgressTab;
            }
        });
    }

    protected void init() {
        // TODO: Listen for real-time updates
//        this.advancements.setListener(this);
    }

    public void render(
            PoseStack p_97361_,
            int p_97362_,
            int p_97363_,
            float p_97364_
    ) {
        int i = (this.width - 252) / 2;
        int j = (this.height - 140) / 2;
        this.renderBackground(p_97361_);
        this.renderInside(p_97361_, p_97362_, p_97363_, i, j);
        this.renderWindow(p_97361_, i, j);
        this.renderTooltips(p_97361_, p_97362_, p_97363_, i, j);
    }

    public boolean mouseDragged(
            double p_97347_,
            double p_97348_,
            int p_97349_,
            double p_97350_,
            double p_97351_
    ) {
        if (p_97349_ != 0) {
            this.isScrolling = false;
            return false;
        } else {
            if (!this.isScrolling) {
                this.isScrolling = true;
            } else if (this.content != null) {
                this.content.scroll(p_97350_, p_97351_);
            }

            return true;
        }
    }

    @Override
    public boolean mouseScrolled(
            double p_94686_,
            double p_94687_,
            double p_94688_
    ) {
        this.content.scroll(0, p_94688_ * 3); // TODO: Provide a client-side config option
        return super.mouseScrolled(p_94686_, p_94687_, p_94688_);
    }

    private void renderInside(
            PoseStack p_97374_,
            int p_97375_,
            int p_97376_,
            int p_97377_,
            int p_97378_
    ) {
        VillagerAdvancementsContent advancementtab = this.content;
        if (advancementtab == null) {
            fill(p_97374_, p_97377_ + 9, p_97378_ + 18, p_97377_ + 9 + 234, p_97378_ + 18 + 113, -16777216);
            int i = p_97377_ + 9 + 117;
            drawCenteredString(p_97374_, this.font, NO_ADVANCEMENTS_LABEL, i, p_97378_ + 18 + 56 - 4, -1);
            drawCenteredString(p_97374_, this.font, VERY_SAD_LABEL, i, p_97378_ + 18 + 113 - 9, -1);
        } else {
            PoseStack posestack = RenderSystem.getModelViewStack();
            posestack.pushPose();
            posestack.translate((double) (p_97377_ + 9), (double) (p_97378_ + 18), 0.0);
            RenderSystem.applyModelViewMatrix();
            advancementtab.drawContents(p_97374_);
            posestack.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.depthFunc(515);
            RenderSystem.disableDepthTest();
        }

    }

    public void renderWindow(
            PoseStack p_97357_,
            int p_97358_,
            int p_97359_
    ) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, WINDOW_LOCATION);
        this.blit(p_97357_, p_97358_, p_97359_, 0, 0, 252, 140);

        this.font.draw(p_97357_, TITLE, (float) (p_97358_ + 8), (float) (p_97359_ + 6), 4210752);

        int bgX = (this.width - 252) / 2;
        int bgY = (this.height - 140) / 2;
        this.tabs.draw(new RenderContext(itemRenderer, p_97357_), bgX, bgY);
    }

    private void renderTooltips(
            PoseStack p_97382_,
            int p_97383_,
            int p_97384_,
            int mouseX,
            int mouseY
    ) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        if (this.content != null) {
            PoseStack posestack = RenderSystem.getModelViewStack();
            posestack.pushPose();
            posestack.translate((double) (mouseX + 9), (double) (mouseY + 18), 400.0);
            RenderSystem.applyModelViewMatrix();
            RenderSystem.enableDepthTest();
            this.content.drawTooltips(p_97382_, p_97383_ - mouseX - 9, p_97384_ - mouseY - 18, mouseX, mouseY);
            RenderSystem.disableDepthTest();
            posestack.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }

    @Override
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int p_94697_
    ) {
        JobID id = this.content.getClickJob(mouseY, mouseX);

        if (id == null) {
            int bgX = (this.width - backgroundWidth) / 2;
            int bgY = (this.height - backgroundHeight) / 2;
            this.tabs.mouseClicked(bgX, bgY, mouseX, mouseY);
            return super.mouseClicked(mouseX, mouseY, p_94697_);
        }

        if (content.isLocked(id)) {
            QuestownNetwork.CHANNEL.sendToServer(new UnlockJobMessage(flagPos, villagerUUID, id, true));
            return true;
        }

        changeJobAndClose(id);
        return true;
    }

    private void changeJobAndClose(JobID id) {

        QuestownNetwork.CHANNEL.sendToServer(new ChangeVillagerJobMessage(
                flagPos.getX(),
                flagPos.getY(),
                flagPos.getZ(),
                villagerUUID,
                id,
                true
        ));

        this.minecraft.setScreen((Screen) null);
    }
}