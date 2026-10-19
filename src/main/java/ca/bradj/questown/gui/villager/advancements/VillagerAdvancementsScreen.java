package ca.bradj.questown.gui.villager.advancements;

import ca.bradj.questown.core.network.ChangeVillagerJobMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.core.network.UnlockJobMessage;
import ca.bradj.questown.gui.CompatScreen;
import ca.bradj.questown.gui.RenderContext;
import ca.bradj.questown.gui.ScrollableScreen;
import ca.bradj.questown.gui.VillagerTabs;
import ca.bradj.questown.gui.VillagerTabsEmbedding;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.mc.Compat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

import java.util.Collection;
import java.util.Optional;
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
                Compat.literal("test"),
                Compat.literal("test2"),
                Optional.of(new ResourceLocation("textures/gui/advancements/backgrounds/stone.png")),
                AdvancementType.TASK,
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
        this.tabs = VillagerTabs.forMenu(new VillagerTabsEmbedding() {
            @Override
            public Collection<String> getEnabledTabs() {
                // Management-only sessions keep the inventory tab hidden here too
                // (every other tabbed villager menu filters on showInventoryTab;
                // this screen used to return VillagerTabs.all() and resurrected
                // the tab - 2026-10-15 playtest).
                return showInventoryTab
                        ? VillagerTabs.all()
                        : VillagerTabs.hideInventory(VillagerTabs.all());
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

    private static boolean isCreative() {
        return Minecraft.getInstance().player.isCreative();
    }

    protected void init() {
        // TODO: Listen for real-time updates
//        this.advancements.setListener(this);
    }

    // 1.19.2 parity: the 1.19.2 background here was the (blurred) world, not the opaque
    // 1.20.6 menu background; the HUD was drawn above it. See QuestownHudEvents for the
    // HUD redraw. The compat base routes Screen.renderBackground here, so the surface
    // hook keeps the blur where the golden's render drew it (before the content).
    @Override
    protected void renderScreenSurface(
            RenderContext ctx,
            int mouseX,
            int mouseY,
            float partialTicks
    ) {
        this.renderBlurredBackground(partialTicks);
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
            // 1.20.6: clip the stone-tile content to the pane so it does not bleed
            // past the window border. enableScissor(x, y, right, bottom) takes
            // absolute canvas edges (not width/height - confirmed from the
            // ScreenRectangle(x, y, p3 - x, p4 - y) ctor), so it wraps the
            // pose push/translate block.
            ctx.gui().enableScissor(p_97377_ + 9, p_97378_ + 18, p_97377_ + 9 + 234, p_97378_ + 18 + 113);
            // 1.20.6: GUI transforms live on guiGraphics.pose(); touching the global
            // modelview stack here corrupts the u_ModelView uniform that the
            // RenderType.gui() pipeline (fill/drawString) applies, clipping content.
            ctx.pose().pushPose();
            ctx.pose().translate(p_97377_ + 9, p_97378_ + 18, 0.0F);
            advancementtab.drawContents(ctx);
            ctx.pose().popPose();
            ctx.gui().disableScissor();
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
        ctx.gui().blit(WINDOW_LOCATION, p_97358_, p_97359_, 0, 0, 252, 140);

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
            // z +400, as the 1.19.2 golden (translate(..., 400.0)): drawTooltips shifts its content
            // to -200, so a 0 here put the hover box, the job name and the pane dim at z -200,
            // behind the pane background (2026-10-19 playtest: "the hover tooltips on the skills
            // UI do not show the job names").
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

        if (content.isLocked(id)) {
            QuestownNetwork.sendToServer(new UnlockJobMessage(flagPos, villagerUUID, id, true));
            return true;
        }

        changeJobAndClose(id);
        return true;
    }

    private void changeJobAndClose(JobID id) {

        QuestownNetwork.sendToServer(new ChangeVillagerJobMessage(
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