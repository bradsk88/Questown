package ca.bradj.questown.gui;

import ca.bradj.questown.core.network.ChangeVillagerJobMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Jobs;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.JEI;
import com.google.common.collect.ImmutableList;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.UUID;

/** Asks before switching a villager to a job it already has unlocked. A villager away on a trip can switch now or after the trip. */
public class SwitchJobConfirmScreen extends CompatScreen {

    private static final int backgroundWidth = 176;
    private static final int backgroundHeight = 166;
    private static final int PAGE_PADDING = 10;

    private final JEI.NineNine background;
    private final BlockPos flagPos;
    private final UUID villagerUUID;
    private final JobID job;
    private final Component villagerName;
    private final boolean awayOnTrip;

    private int pageTopY;
    private int pageLeftX;

    public SwitchJobConfirmScreen(
            BlockPos flagPos,
            UUID villagerUUID,
            JobID job,
            Component villagerName,
            boolean awayOnTrip
    ) {
        super(Compat.translatable("menu.switch_job.title"));
        this.background = JEI.getRecipeGuiBackground();
        this.flagPos = flagPos;
        this.villagerUUID = villagerUUID;
        this.job = job;
        this.villagerName = villagerName;
        this.awayOnTrip = awayOnTrip;
    }

    public static void open(BlockPos flagPos, UUID villagerUUID, JobID job) {
        Minecraft mc = Minecraft.getInstance();
        VisitorMobEntity villager = null;
        if (mc.level != null) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof VisitorMobEntity v && villagerUUID.equals(v.getUUID())) {
                    villager = v;
                    break;
                }
            }
        }
        Component name = villager == null ? Compat.translatable("menu.switch_job.this_villager") : villager.getDisplayName();
        boolean away = villager != null && villager.isAwayOnTrip();
        mc.setScreen(new SwitchJobConfirmScreen(flagPos, villagerUUID, job, name, away));
    }

    @Override
    protected void init() {
        this.pageTopY = ((this.height - backgroundHeight) / 2) + PAGE_PADDING;
        this.pageLeftX = ((this.width - backgroundWidth) / 2);

        int buttonHeight = (2 * font.lineHeight) + 2;
        int buttonWidth = backgroundWidth - (2 * PAGE_PADDING);
        int buttonX = pageLeftX + PAGE_PADDING;
        List<SwitchJobChoice> choices = SwitchJobChoice.forTrip(awayOnTrip);
        int y = pageTopY + backgroundHeight - (2 * PAGE_PADDING) - (choices.size() * (buttonHeight + 4));
        for (SwitchJobChoice c : choices) {
            this.addRenderableWidget(Compat.button(
                    Compat.translatable(c.key()), (b) -> choose(c), buttonX, y, buttonWidth, buttonHeight
            ));
            y += buttonHeight + 4;
        }
    }

    private void choose(SwitchJobChoice c) {
        if (c != SwitchJobChoice.CANCEL) {
            QuestownNetwork.sendToServer(new ChangeVillagerJobMessage(
                    flagPos.getX(), flagPos.getY(), flagPos.getZ(), villagerUUID, job, true, c == SwitchJobChoice.AFTER_TRIP
            ));
        }
        this.onClose();
    }

    @Override
    protected void renderPrologue(RenderContext ctx, int mouseX, int mouseY, float partialTicks) {
        ctx.fillGradient(0, 0, this.width, this.height, -1072689136, -804253680);
    }

    @Override
    protected void renderScreenSurface(RenderContext ctx, int mouseX, int mouseY, float partialTicks) {
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        ctx.drawNineNine(this.background, x, y, backgroundWidth, backgroundHeight);
    }

    @Override
    protected void renderScreenContent(RenderContext ctx, int mouseX, int mouseY, float partialTicks) {
        int textX = pageLeftX + PAGE_PADDING;
        int textWidth = backgroundWidth - (2 * PAGE_PADDING);
        ctx.darkText(title, textX, pageTopY);
        int y = pageTopY + (2 * font.lineHeight);
        ImmutableList<Component> jc = Jobs.getComponentsForTooltip(job);
        Component jobName = Compat.translatable("menu.common.job_name", jc.get(0), jc.get(1));
        y = wrapped(ctx, Compat.translatable("menu.switch_job.body", villagerName, jobName), textX, y, textWidth);
        if (awayOnTrip) {
            wrapped(ctx, Compat.translatable("menu.switch_job.away", villagerName), textX, y + font.lineHeight, textWidth);
        }
    }

    private int wrapped(RenderContext ctx, Component text, int x, int y, int width) {
        for (FormattedCharSequence part : ctx.split(text, width)) {
            ctx.darkText(part, x, y);
            y += font.lineHeight;
        }
        return y;
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
