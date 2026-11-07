package ca.bradj.questown.gui.villager.advancements;

import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.core.network.OpenSkillDetailsMessage;
import ca.bradj.questown.gui.CompatScreen;
import ca.bradj.questown.gui.RenderContext;
import ca.bradj.questown.gui.ScrollableScreen;
import ca.bradj.questown.gui.VillagerTabs;
import ca.bradj.questown.gui.VillagerTabsEmbedding;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.mc.Compat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.advancements.FrameType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

import java.util.Collection;
import java.util.UUID;

public class VillagerAdvancementsScreen extends CompatScreen implements ScrollableScreen {
    private static final ResourceLocation WINDOW_LOCATION = new ResourceLocation("textures/gui/advancements/window.png");
    private static final Component VERY_SAD_LABEL = Compat.translatable("advancements.sad_label");
    private static final Component NO_ADVANCEMENTS_LABEL = Compat.translatable("advancements.empty");
    private static final Component TITLE = Compat.translatable("menu.jobs");
    public static final int backgroundWidth = 252;
    public static final int backgroundHeight = 140;
    private final BlockPos flagPos;
    private final UUID villagerUUID;
    private final boolean fromFlag;
    private final VillagerAdvancementsContent content;
    private final VillagerTabs tabs;
    private boolean isScrolling;

    public VillagerAdvancementsScreen(
            BlockPos flagPos,
            UUID villagerUUID,
            Collection<JobID> unlockedJobs,
            Collection<JobID> unlockableJobs,
            JobID currentJob,
            boolean showBlockOfProgressTab,
            boolean showInventoryTab
    ) {
        super(Compat.literal(""));
        DisplayInfo displayInfo = new DisplayInfo(
                Items.CREEPER_HEAD.getDefaultInstance(),
                Compat.translatable("menu.jobs.root.title"),
                Compat.translatable("menu.jobs.root.description"),
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
                unlockableJobs,
                currentJob == null || isCreative() ? VillagerAdvancements.all() : VillagerAdvancements.all().branch(currentJob.rootId())
        );
        this.flagPos = flagPos;
        this.villagerUUID = villagerUUID;
        this.fromFlag = !showInventoryTab;
        this.tabs = VillagerTabs.forMenu(new VillagerTabsEmbedding() {
            @Override
            public Collection<String> getEnabledTabs() {
                return VillagerTabs.skillsPaneTabs(showInventoryTab);
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

            @Override
            public boolean showInventoryTab() {
                return showInventoryTab;
            }
        });
    }

    private static boolean isCreative() {
        return Minecraft.getInstance().player.isCreative();
    }

    protected void init() {
        // TODO: Listen for real-time updates
//        this.advancements.setListener(this);
    }

    @Override
    protected void renderScreenSurface(
            RenderContext ctx,
            int mouseX,
            int mouseY,
            float partialTicks
    ) {
        this.renderBackground(ctx.pose());
    }

    @Override
    protected void renderScreenContent(
            RenderContext ctx,
            int mouseX,
            int mouseY,
            float partialTicks
    ) {
        int i = (this.width - 252) / 2;
        int j = (this.height - 140) / 2;
        this.renderInside(ctx, i, j);
        this.renderWindow(ctx, i, j);
        this.renderTooltips(ctx, mouseX, mouseY, i, j);
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

        public boolean mouseScrolled(
            double p_94686_,
            double p_94687_,
            double p_94688_
    ) {
        this.content.scroll(0, p_94688_ * 3); // TODO: Provide a client-side config option
        return false;
    }

    private void renderInside(
            RenderContext ctx,
            int p_97377_,
            int p_97378_
    ) {
        VillagerAdvancementsContent advancementtab = this.content;
        if (advancementtab == null) {
            ctx.fill(p_97377_ + 9, p_97378_ + 18, p_97377_ + 9 + 234, p_97378_ + 18 + 113, -16777216);
            int i = p_97377_ + 9 + 117;
            ctx.centered(NO_ADVANCEMENTS_LABEL, i, p_97378_ + 18 + 56 - 4, -1);
            ctx.centered(VERY_SAD_LABEL, i, p_97378_ + 18 + 113 - 9, -1);
        } else {
            // Keep the tiles inside the pane.
            ctx.enableScissor(p_97377_ + 9, p_97378_ + 18, p_97377_ + 9 + 234, p_97378_ + 18 + 113);
            ctx.pose().pushPose();
            ctx.pose().translate(p_97377_ + 9, p_97378_ + 18, 0.0F);
            advancementtab.drawContents(ctx);
            ctx.pose().popPose();
            ctx.disableScissor();
        }

    }

    public void renderWindow(
            RenderContext ctx,
            int p_97358_,
            int p_97359_
    ) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, WINDOW_LOCATION);
        ctx.blit(WINDOW_LOCATION, p_97358_, p_97359_, 0, 0, 252, 140);

        ctx.text(TITLE, p_97358_ + 8, p_97359_ + 6, 4210752, false);

        int bgX = (this.width - 252) / 2;
        int bgY = (this.height - 140) / 2;
        this.tabs.draw(ctx, bgX, bgY);
    }

    private void renderTooltips(
            RenderContext ctx,
            int mouseX,
            int mouseY,
            int paneX,
            int paneY
    ) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        if (this.content != null) {
            ctx.pose().pushPose();
            // z 400: the tooltips draw at -200 relative to this.
            ctx.pose().translate(paneX + 9, paneY + 18, 400.0F);
            this.content.drawTooltips(ctx, mouseX - paneX - 9, mouseY - paneY - 18, paneX, paneY);
            ctx.pose().popPose();
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

        // A click opens the skill's details, where Unlock and Switch are.
        QuestownNetwork.sendToServer(new OpenSkillDetailsMessage(flagPos, villagerUUID, id, content.isLocked(id), fromFlag));
        return true;
    }
}