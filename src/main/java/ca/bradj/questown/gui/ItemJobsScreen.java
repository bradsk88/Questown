package ca.bradj.questown.gui;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.core.Coordinate;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.core.network.AddWorkFromUIMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.core.network.SpendFlagBopOnSkillMessage;
import ca.bradj.questown.core.network.UnlockJobMessage;
import ca.bradj.questown.jobs.Jobs;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import com.google.common.collect.ImmutableList;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.common.util.MathUtil;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import static ca.bradj.questown.core.network.AddWorkFromUIMessage.Action.CONFIRMED;
import static ca.bradj.questown.core.network.AddWorkFromUIMessage.Action.REJECTED;
import static ca.bradj.questown.gui.PagedCardScreen.*;

public class ItemJobsScreen extends CompatScreen {

    /** How many of a job's products the card shows before "…". */
    private static final int PRODUCES_SHOWN = 3;


    public static final int EXTRA_HEIGHT = 56;
    private final List<UIJob> jobs;
    private final PagedCardScreen<UIJob> delegate;
    private @NotNull Ingredient requestedItem;
    private final BlockPos flagPos;
    private Button addButton;
    // Skill-tree mode, a variant of the "who makes <item>" screen: one skill's card, with Unlock
    // (locked) or Switch (unlocked) instead of Add.
    private final @Nullable UUID skillVillager;
    private final boolean skillLocked;
    // Opened from the flag with Blocks of Progress there: Unlock spends one of the flag's.
    private final int flagBops;

    public ItemJobsScreen(
            Ingredient requestedItem,
            Collection<UIJob> jobs,
            BlockPos flagPos
    ) {
        this(requestedItem, jobs, flagPos, null, false, 0);
    }

    public static ItemJobsScreen forSkill(UIJob job, BlockPos flagPos, UUID villager, boolean locked, int flagBops) {
        return new ItemJobsScreen(Ingredient.EMPTY, List.of(job), flagPos, villager, locked, flagBops);
    }

    private ItemJobsScreen(
            Ingredient requestedItem,
            Collection<UIJob> jobs,
            BlockPos flagPos,
            @Nullable UUID skillVillager,
            boolean skillLocked,
            int flagBops
    ) {
        super(Compat.translatable(skillVillager == null ? "menu.work_add_confirm.title" : "menu.skill_details.title"));
        this.skillVillager = skillVillager;
        this.skillLocked = skillLocked;
        this.flagBops = flagBops;

        this.delegate = new PagedCardScreen<>(
                () -> height,
                () -> width,
                () -> ImmutableList.copyOf(jobs),
                UtilClean::noOpConsumer,
                this::renderCardContent,
                3,
                16,
                EXTRA_HEIGHT
        );
        this.jobs = ImmutableList.copyOf(jobs);
        this.requestedItem = requestedItem;
        this.flagPos = flagPos;

    }

    private static final int FLAG_BOP_ICON_SPACE = 20;

    private int flagBopIconX() {
        return addButton.x - FLAG_BOP_ICON_SPACE + 1;
    }

    private int flagBopIconY() {
        return addButton.y + 1;
    }

    private boolean isOverFlagBop(int mouseX, int mouseY) {
        return showsFlagBop() && UtilClean.isCoordInBox(mouseX, mouseY, flagBopIconX(), flagBopIconY(), 16, 16);
    }

    private boolean showsFlagBop() {
        return skillVillager != null && skillLocked && flagBops > 0;
    }

    private void act() {
        if (skillVillager == null) {
            send();
            return;
        }
        UIJob job = jobs.get(0);
        if (skillLocked && flagBops > 0) {
            QuestownNetwork.sendToServer(new SpendFlagBopOnSkillMessage(flagPos, skillVillager, job.jobId()));
            this.onClose();
            return;
        }
        if (skillLocked) {
            QuestownNetwork.sendToServer(new UnlockJobMessage(flagPos, skillVillager, job.jobId(), true));
            this.onClose();
            return;
        }
        SwitchJobConfirmScreen.open(flagPos, skillVillager, job.jobId());
    }

    private void send() {
        ItemStack first = getFirst();
        if (first.isEmpty()) {
            QT.GUI_LOGGER.error("Cannot add work request for job with empty result");
            return;
        }
        AddWorkFromUIMessage m = new AddWorkFromUIMessage(Ingredient.of(first), flagPos, CONFIRMED);
        QuestownNetwork.sendToServer(m);
    }

    private ItemStack getFirst() {
        // No producer known yet (a loot job's drops are listed only once the town has seen them):
        // the raw-food quest's icon on day 1 crashed the client here.
        if (jobs.isEmpty()) {
            return ItemStack.EMPTY;
        }
        UIJob j = jobs.get(delegate.getCurrentPageIndex());
        // The request is for what the player clicked, not the job's first loot item.
        List<ItemStack> r = ProducedMatches.of(j.result(), requestedItem);
        if (r.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack first = r.get(0);
        if (first.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return first;
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
    protected void init() {
        super.init();
        this.delegate.afterInit(this::addRenderableWidget);
        int pageTopY = ((this.height - delegate.backgroundHeight)) / 2;
        int pageLeftX = ((this.width - backgroundWidth) / 2);
        int buttonHeight = (2 * font.lineHeight) + 2;
        // The flag's Block of Progress is drawn left of Unlock (the "slot" as an item, not empty).
        int bopSpace = showsFlagBop() ? FLAG_BOP_ICON_SPACE : 0;
        this.addButton = this.addRenderableWidget(
                Compat.button(
                        Compat.translatable(skillVillager == null ? "menu.item_jobs.add_to_work_requests"
                                : !skillLocked ? "menu.skill_details.switch"
                                : flagBops > 0 ? "menu.skill_details.unlock_from_flag" : "menu.skill_details.unlock"),
                        (p_96776_) -> this.act(),
                        pageLeftX + BIG_PADDING + bopSpace,
                        pageTopY + delegate.backgroundHeight - buttonHeight - BIG_PADDING,
                        backgroundWidth - (2 * BIG_PADDING) - bopSpace,
                        buttonHeight
                )
        );
    }

    @Override
    protected void renderPrologue(
            RenderContext ctx,
            int mouseX,
            int mouseY,
            float partialTicks
    ) {
        this.addButton.visible = skillVillager != null || !getFirst().isEmpty();
    }

    @Override
    protected void renderScreenSurface(
            RenderContext ctx,
            int mouseX,
            int mouseY,
            float partialTicks
    ) {
        this.delegate.renderBg(ctx, partialTicks, mouseX, mouseY);
    }

    // 1.20.6 parity note: vanilla Screen.render here draws the flat blurred/gradient
    // surface for us (CompatScreen.renderBackground -> renderScreenSurface), so the
    // paged panel sits between the surface and the renderables - the same relative
    // order the 1.19.2 leaf's explicit renderBackground call produced.
    @Override
    protected void renderScreenContent(
            RenderContext ctx,
            int mouseX,
            int mouseY,
            float partialTicks
    ) {
        if (showsFlagBop()) {
            ItemStack bop = ca.bradj.questown.mc.Compat.copyWithCount(ItemsInit.BLOCK_OF_PROGRESS.get().getDefaultInstance(), Math.min(flagBops, 64));
            ctx.renderItemAndDecorations(bop, flagBopIconX(), flagBopIconY());
        }
        String renderString = skillVillager == null ? "Jobs:" : title.getString() + ":";
        ImmutableRect2i textArea = getTitleArea();
        ctx.lightText(renderString, textArea.getX(), textArea.getY());
        Ingredients.render(
                ctx,
                requestedItem,
                textArea.getX() + textArea.getWidth() - 16,
                textArea.getY() - 6
        );
        // The hovered item's name, or the "…" note. The returned tooltip was dropped (also in
        // 1.19.2), so no item on this card showed one.
        List<Component> cardTooltip = this.delegate.afterRender(ctx, mouseX, mouseY, partialTicks, jobs, false);
        if (jobs.isEmpty()) {
            // Below the page-navigation row, aligned with it (a fixed offset from the title would
            // overlap "Page 1 / 1").
            ImmutableRect2i nav = MathUtil.union(delegate.previousPage.getArea(), delegate.nextPage.getArea());
            ctx.darkTextWrap(
                    new Coordinate(nav.getX(), nav.getY() + nav.getHeight() + 6),
                    nav.getWidth(),
                    Compat.translatable("menu.item_jobs.none_known")
            );
        }
        renderTooltips(ctx, mouseX, mouseY, textArea.expandBy(4));
        if (cardTooltip != null && !cardTooltip.isEmpty()) {
            this.showTooltip(cardTooltip.stream().map(Compat::componentText).toList());
        }
        if (isOverFlagBop(mouseX, mouseY)) {
            // Say that this Block of Progress is the flag's.
            this.showTooltip(Compat.translatable("gui.flag_bop.tooltip", flagBops));
        }
    }

    @Override
    public boolean mouseClicked(
            double x,
            double y,
            int p_94697_
    ) {
        @NotNull ImmutableRect2i ta = getTitleArea();
        Supplier<Boolean> in = () -> UtilClean.isCoordInBox(x, y, ta.getX(), ta.getY(), ta.getWidth(), ta.getHeight());
        if (in.get() && !requestedItem.isEmpty()) {
            AddWorkFromUIMessage m = new AddWorkFromUIMessage(requestedItem, flagPos, REJECTED);
            QuestownNetwork.sendToServer(m);
        }
        return super.mouseClicked(x, y, p_94697_);
    }

    private @NotNull ImmutableRect2i getTitleArea() {
        String sizeString = "Jobs:XXX";
        ImmutableRect2i pageArea = MathUtil.union(delegate.previousPage.getArea(), delegate.nextPage.getArea());
        pageArea = pageArea.moveUp(16);
        ImmutableRect2i textArea = MathUtil.centerTextArea(pageArea, font, sizeString);
        return textArea;
    }

    private void renderTooltips(
            RenderContext ctx,
            int mouseX,
            int mouseY,
            ImmutableRect2i textArea
    ) {
        if (UtilClean.isCoordInBox(
                mouseX,
                mouseY,
                textArea.getX(),
                textArea.getY(),
                textArea.getWidth(),
                textArea.getHeight()
        )) {
            ctx.fill(
                    textArea.getX(),
                    textArea.getY(),
                    textArea.getX() + textArea.getWidth(),
                    textArea.getY() + textArea.getHeight(),
                    0x30000000
            );
            this.showTooltip(
                    List.of(
                            Compat.componentText(Compat.translatable("menu.item_jobs.title_tooltip")),
                            Compat.componentText(Ingredients.getName(requestedItem))
                    )
            );
        }
    }

    public boolean mouseScrolled(
            double p_94686_,
            double p_94687_,
            double p_94688_
    ) {
        return this.delegate.mouseScrolled(p_94686_, p_94687_, p_94688_, this::isMouseOver, this::mouseScrolled);
    }

    private List<Component> renderCardContent(
            RenderContext rc,
            Card<UIJob> card,
            Coordinate mouse
    ) {
        PagedCardScreen.CardCoordinates c = card.coords();
        UIJob d = card.data();

        int x = c.leftX();

        Function<PagedCardScreen.CardCoordinates, PagedCardScreen.CardCoordinates> scootch = (cc) -> cc.shiftedDown(12);
        Function<PagedCardScreen.CardCoordinates, PagedCardScreen.CardCoordinates> down = (cc) -> cc.shiftedDown(16);
        Function<PagedCardScreen.CardCoordinates, PagedCardScreen.CardCoordinates> down2 = (cc) -> cc.shiftedDown(24);
        Function<PagedCardScreen.CardCoordinates, PagedCardScreen.CardCoordinates> down3 = (cc) -> cc.shiftedDown(30);

        renderJobTitle(rc, d);

        TriFunction<CardCoordinates, List<Ingredient>, Boolean, List<Component>> renderStrip = (cc, ings, shiftRight) ->
                RenderUtil.stripOfRequestableItems(
                        (ing, coord) -> Ingredients.render(rc, ing, coord.x(), coord.y()),
                        (ing) -> ImmutableList.of(Ingredients.getName(ing)),
                        (text, coord) -> rc.darkText(text, coord.x(), coord.y()),
                        (topLeft, botRight) -> RenderUtil.highlight(rc.gui(), topLeft, botRight),
                        ings,
                        shiftRight ? cc.topLeft().shifted(64, 0) : cc.topLeft(),
                        cc.bottomRight(),
                        mouse
                );

        Component itemsText = Compat.translatable("menu.item_jobs.items_used");

        int labelOffset = 5;

        rc.darkText(itemsText, x, (c = down2.apply(c)).topY() + labelOffset);

        List<Component> tt1 = renderStrip.apply(c, d.ingredients(), true);
        c = c.shiftedDown(font.lineHeight);

        // A job with no tools to list (or only ones it also uses up) has no Tools row at all.
        List<Ingredient> tools = ToolsNotUsedUp.of(d.tools(), d.ingredients(), Ingredients::equal);
        List<Component> tt2 = null;
        if (!tools.isEmpty()) {
            Component toolsText = Compat.translatable("menu.item_jobs.tools_used");
            rc.darkText(toolsText, x, (c = down.apply(c)).topY() + labelOffset);
            tt2 = renderStrip.apply(c, tools, true);
            c = c.shiftedDown(font.lineHeight);
        }

        Component producesText = Compat.translatable("menu.item_jobs.produces");
        rc.darkText(producesText, x, (c = down.apply(c)).topY() + labelOffset);
        List<Ingredient> v = putRequestedItemFirst(Ingredients.fromItems(ImmutableList.copyOf(ProducedMatches.of(d.result(), requestedItem))));
        // The three most likely, then "…".
        List<Component> tt3 = renderStrip.apply(c, v.subList(0, Math.min(PRODUCES_SHOWN, v.size())), true);
        if (v.size() > PRODUCES_SHOWN) {
            List<Component> more = RenderUtil.renderEllipsesAndReturnTooltip(
                    new RenderUtil.EllipsesData(mouse, c.topLeft().shifted(64, 0), PRODUCES_SHOWN, v.size()),
                    Compat.translatable("menu.item_jobs.and_others"),
                    (text, coord) -> rc.darkText(text, coord.x(), coord.y()),
                    (topLeft, botRight) -> RenderUtil.highlight(rc.gui(), topLeft, botRight)
            );
            tt3 = tt3 != null ? tt3 : more;
        }
        c = c.shiftedDown(font.lineHeight);

        Component roomName = Compat.translatable("room." + d.roomNameTranslationKey().getPath());
        Component translatable = Compat.translatable("menu.item_jobs.room", roomName);
        rc.darkText(translatable, x, (c = down.apply(c)).topY());
        List<Component> tt4 = renderStrip.apply(scootch.apply(c), d.roomRecipe(), false);

        int bgX = (width - backgroundWidth) / 2;
        int bgY = (height - delegate.backgroundHeight) / 2;
        int stripHeight = Util.faceWidth * 3;
        int stripY = bgY + delegate.backgroundHeight - stripHeight - buttonHeight - 4 - (BIG_PADDING * 2);
        rc.fill(bgX, stripY, bgX + backgroundWidth, stripY + stripHeight - 2, RenderUtil.SHADOW);
        int i = 0;
        for (UUID uuid : d.villagersWhoCanDoJob()) {
            int stripOffset = (stripHeight - (Util.faceWidth * 2)) / 2;
            stripOffset -= 1;
            blitFace(rc, bgX + Util.faceWidth - 3, stripY + stripOffset, uuid, i++);
        }
        return UtilClean.lastNonNull(tt1, tt2, tt3, tt4);
    }

    private List<Ingredient> putRequestedItemFirst(List<Ingredient> v) {
        if (Ingredients.isTag(requestedItem)) {
            return v;
        }
        if (requestedItem.isEmpty()) {
            return v;
        }
        ImmutableList.Builder<Ingredient> b = ImmutableList.builder();
        b.add(requestedItem);
        v.stream().filter(z -> !Ingredients.equal(z, requestedItem)).forEach(b::add);
        return b.build();
    }

    private void renderJobTitle(
            RenderContext rc,
            UIJob d
    ) {
        ImmutableRect2i pageArea = MathUtil.union(delegate.previousPage.getArea(), delegate.nextPage.getArea());
        pageArea = pageArea.moveDown(20);
        Component jobName = Compat.translatable(
                "menu.common.job_name",
                Jobs.getRootNameComponent(d.jobId()),
                Jobs.getVariantNameComponent(d.jobId())
        );
        ImmutableRect2i textArea = MathUtil.centerTextArea(pageArea, font, jobName);
        rc.darkText(jobName, textArea.getX(), textArea.getY());
    }

    private static void blitFace(
            RenderContext rc,
            int x,
            int y,
            UUID uuid,
            int i
    ) {
        Util.blitFace(rc.gui(), uuid, x + (Util.faceWidth * 4 * i), y, 2);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public List<Rect2i> getExtraAreas() {
        return this.delegate.getExtraAreas();
    }
}
