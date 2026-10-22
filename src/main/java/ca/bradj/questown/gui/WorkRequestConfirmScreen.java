package ca.bradj.questown.gui;

import ca.bradj.questown.core.Coordinate;
import ca.bradj.questown.core.network.AddWorkFromUIMessage;
import ca.bradj.questown.core.network.OpenItemJobsMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Jobs;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.JEI;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Map;

import static ca.bradj.questown.core.network.AddWorkFromUIMessage.Action.CONFIRMED;
import static ca.bradj.questown.core.network.AddWorkFromUIMessage.Action.REJECTED;

public class WorkRequestConfirmScreen extends CompatScreen {
    private static final int backgroundWidth = 176;
    private static final int backgroundHeight = 166;

    private static final int PAGE_PADDING = 10;

    private final JEI.NineNine background;
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
        super(Compat.translatable("menu.work_add_confirm.title", Ingredients.getName(requested)));

        this.background = JEI.getRecipeGuiBackground();
        this.itemRequested = requested;
        this.iconsForJobsWhichProduceResult = ImmutableList.copyOf(iconsForJobsWhichProduceResult.entrySet());
        this.requestWork = () -> send(flagPos, CONFIRMED);
        this.backToRequestedJobs = () -> this.send(flagPos, REJECTED);
        this.openJobsInfoScreen = () -> {
            OpenItemJobsMessage msg = new OpenItemJobsMessage(flagPos, this.itemRequested);
            QuestownNetwork.sendToServer(msg);
        };
    }

    private void send(
            BlockPos p,
            AddWorkFromUIMessage.Action action
    ) {
        AddWorkFromUIMessage m = new AddWorkFromUIMessage(itemRequested, p, action);
        QuestownNetwork.sendToServer(m);
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
        this.addRenderableWidget(Compat.button(
                Compat.translatable("menu.common.learn_more"),
                (p_96776_) -> this.openJobsInfoScreen.run(),
                pageLeftX + PAGE_PADDING, moreInfoButtonY, buttonWidth, buttonHeight));

        // Bottom buttons are relative to bottom of page
        this.confirmTextY = pageTopY + backgroundHeight - (2 * PAGE_PADDING) - buttonHeight - tallLine;
        this.confirmButtonY = confirmTextY + tallLine;
        this.addRenderableWidget(Compat.button(
                Compat.translatable("menu.common.yes"),
                (p_96776_) -> this.requestWork.run(),
                pageLeftX + PAGE_PADDING, confirmButtonY, (int) (buttonWidth / 2f), buttonHeight));
        this.addRenderableWidget(Compat.button(
                Compat.translatable("menu.common.no"),
                (p_96776_) -> this.backToRequestedJobs.run(),
                (int) (pageLeftX + PAGE_PADDING + (buttonWidth / 2f)), confirmButtonY, (int) (buttonWidth / 2f), buttonHeight));
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
        int x = pageLeftX;
        x = x + PAGE_PADDING;

        int xRef = x;

        ctx.darkText(title, x, pageTopY);
        Ingredients.render(ctx, itemRequested, x, mainItemIconY);
        x += 24;
        ctx.darkText(
                Compat.translatable(
                        "menu.work_add_confirm.there_are_n_known_jobs",
                        iconsForJobsWhichProduceResult.size()
                ),
                x,
                mainItemTextY
        );
        Component translatable = Compat.translatable("menu.work_add_confirm.which_produce_this_item");
        ctx.darkText(translatable, x, mainItemTextY2);
        renderJobIcons(ctx, mouseX, mouseY, xRef, jobIconsY);
        Component confTxt = Compat.translatable("menu.work_add_confirm.confirm_text");
        ctx.darkText(confTxt, xRef, confirmTextY);
    }

    private void renderJobIcons(
            RenderContext ctx,
            int mouseX,
            int mouseY,
            int xRef,
            int y
    ) {
        RenderUtil.stripOfRequestableItems(
                (ingr, coord) -> {
                    Ingredient ing = Ingredients.fromRL(ingr.getValue());
                    Ingredients.render(ctx, ing, coord.x(), coord.y());
                },
                (ingr) -> Jobs.getComponentsForTooltip(ingr.getKey()),
                (text, coord) -> ctx.darkText(text, coord.x(), coord.y()),
                (topLeft, botRight) -> RenderUtil.highlight(ctx.gui(), topLeft, botRight),
                iconsForJobsWhichProduceResult,
                new Coordinate(xRef, y),
                new Coordinate(xRef + backgroundWidth - (PAGE_PADDING * 2), y),
                new Coordinate(mouseX, mouseY)
        );
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