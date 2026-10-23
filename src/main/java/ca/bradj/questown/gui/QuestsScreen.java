package ca.bradj.questown.gui;

import ca.bradj.questown.Questown;
import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.RoomBlock;
import ca.bradj.questown.town.special.SpecialQuests;
import ca.bradj.questown.core.Coordinate;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.gui.PagedCardScreen.Card;
import ca.bradj.questown.gui.PagedCardScreen.CardCoordinates;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.logic.RoomRecipes;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Journal;
import ca.bradj.questown.mc.JEI;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.quests.Quest;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.*;

import static ca.bradj.questown.gui.PagedCardScreen.buttonWidth;

public class QuestsScreen<C extends AbstractQuestsContainer> extends AbstractPagedCardScreen<C, UIQuest> {
    private static final int backgroundWidth = 176;
    private static final int backgroundHeight = 166;
    private static final String VILLAGERS_TAB = "tooltips.villagers";

    private static final int TEXT_COLOR = 0xFF404040;
    private static final int TEXT_MARGIN = 12;
    private static final int BLOCK_GAP = 6;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_H_PADDING = 16;
    private static final int FOOTER_GAP = 2;

    private final List<UIQuest> quests;
    private final JEI.NineNine background;
    private final Map<Position, Runnable> removes = new HashMap<>();
    /** Per frame: the clickable item/tag icons of ITEM and LOOT cards (top-left → ingredient). */
    private final Map<Position, Ingredient> itemIconClicks = new HashMap<>();
    private final SubUI tabs;

    private final boolean allComplete;
    private final boolean morningRewardPending;
    private boolean showingCompleted = false;
    private @Nullable Button viewCompletedButton;

    public QuestsScreen(
            C container,
            Inventory playerInv,
            Component title,
            SubUI tabs
    ) {
        super(container, playerInv, title);
        super.imageWidth = 256;
        super.imageHeight = 220;

        this.quests = ImmutableList.copyOf(container.GetQuests());
        this.allComplete = container.isAllComplete();
        this.morningRewardPending = container.isMorningRewardPending();
        this.background = JEI.getRecipeGuiBackground();
        this.tabs = tabs;
    }

    /**
     * The cards on show: open quests only, until the player asks for the completed ones. Completed
     * cards used to share the list and padded it to dozens of pages (2026-10-22 playtest: 4 open
     * quests on "Page 1 / 7"). Card indices point into this list.
     */
    @Override
    protected ImmutableList<UIQuest> cardsData() {
        if (showingCompleted) {
            return ImmutableList.copyOf(quests);
        }
        return quests.stream()
                     .filter(q -> !Quest.QuestStatus.COMPLETED.equals(q.status))
                     .collect(ImmutableList.toImmutableList());
    }

    private boolean anyCompleted() {
        return quests.stream().anyMatch(q -> Quest.QuestStatus.COMPLETED.equals(q.status));
    }

    @Override
    protected void init() {
        super.init();
        this.viewCompletedButton = null;
        if (showingCompleted || !anyCompleted()) {
            return;
        }
        Component link = Compat.translatable("menu.quests.view_completed");
        int btnW = this.font.width(link) + BUTTON_H_PADDING;
        // All done: centred in the empty panel. Otherwise: just below the panel, under the open cards.
        int btnY = allComplete ? emptyStateButtonY() : footerButtonY();
        this.viewCompletedButton = Compat.button(
                link,
                btn -> {
                    showingCompleted = true;
                    btn.visible = false;
                    btn.active = false;
                },
                (this.width - btnW) / 2, btnY, btnW, BUTTON_HEIGHT
        );
        this.addRenderableWidget(this.viewCompletedButton);
    }

    private int footerButtonY() {
        return (this.height + backgroundHeight()) / 2 + FOOTER_GAP;
    }

    private Component subtitleComponent() {
        return morningRewardPending
                ? Compat.translatable("menu.quests.all_done_morning")
                : Compat.translatable("menu.quests.all_done_caught_up");
    }

    // Shared layout: the subtitle block + gap + button, vertically centred in the panel. init()
    // places the button here; render() draws the subtitle above it using the same math.
    private int emptyStateTop() {
        int bgY = (this.height - backgroundHeight) / 2;
        int textWidth = backgroundWidth - 2 * TEXT_MARGIN;
        int stackHeight = wrappedHeight(subtitleComponent(), textWidth) + BLOCK_GAP + BUTTON_HEIGHT;
        return bgY + (backgroundHeight - stackHeight) / 2;
    }

    private int emptyStateButtonY() {
        int textWidth = backgroundWidth - 2 * TEXT_MARGIN;
        return emptyStateTop() + wrappedHeight(subtitleComponent(), textWidth) + BLOCK_GAP;
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
    protected void renderScreenExtras(
            RenderContext ctx,
            int mouseX,
            int mouseY,
            float partialTicks
    ) {
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - backgroundHeight) / 2;
        this.tabs.draw(ctx, bgX, bgY);
        this.tabs.renderTooltip(bgX, bgY, mouseX, mouseY, key -> this.showTooltip(Compat.translatable(key)));
        if (allComplete && !showingCompleted) {
            renderAllDoneState(ctx);
            return;
        }
        if (cardsData().isEmpty()) {
            renderNothingToShow(ctx);
        }
    }

    /**
     * No cards and no all-done state (a villager's Quests tab: its container has no all-done
     * summary). The page used to be blank (2026-10-23 r10).
     */
    private void renderNothingToShow(RenderContext ctx) {
        int textWidth = backgroundWidth - 2 * TEXT_MARGIN;
        Component text = Compat.translatable("menu.quests.none_open");
        int bgY = (this.height - backgroundHeight) / 2;
        int top = bgY + (backgroundHeight - wrappedHeight(text, textWidth)) / 2;
        drawCenteredWrapped(ctx, text, this.width / 2, top, textWidth, TEXT_COLOR);
    }

    private void renderAllDoneState(RenderContext ctx) {
        // Draw the wrapped subtitle above the "View completed" button (which is a real widget added
        // in init() at emptyStateButtonY(), so it renders and hit-tests itself).
        int textWidth = backgroundWidth - 2 * TEXT_MARGIN;
        drawCenteredWrapped(ctx, subtitleComponent(), this.width / 2, emptyStateTop(), textWidth, TEXT_COLOR);
    }

    private int wrappedHeight(Component text, int maxWidth) {
        return Compat.splitText(this.font, text, maxWidth).size() * this.font.lineHeight;
    }

    /** Draw {@code text} wrapped to {@code maxWidth}, each line centred on {@code centerX}. Returns height. */
    private int drawCenteredWrapped(
            RenderContext ctx,
            Component text,
            int centerX,
            int y,
            int maxWidth,
            int color
    ) {
        int dy = 0;
        for (FormattedCharSequence line : Compat.splitText(this.font, text, maxWidth)) {
            int x = centerX - (this.font.width(line) / 2);
            ctx.text(line, x, y + dy, color, false);
            dy += this.font.lineHeight;
        }
        return dy;
    }


    @Override
    protected List<Component> renderCardContent(
            RenderContext ctx,
            Card<UIQuest> card,
            int mouseX,
            int mouseY
    ) {
        UIQuest recipe = cardsData().get(card.index());
        if (recipe == null) {
            return null;
        }
        Component recipeName = recipe.getName();
        if (recipe.fromRecipe != null) {
            Component fromName = RoomRecipes.getName(recipe.fromRecipe);
            recipeName = Compat.translatable("quests.upgrade", fromName, recipeName);
        }

        if (Quest.QuestStatus.COMPLETED.equals(recipe.status)) {
            recipeName = Compat.translatable("quests.completed_suffix", recipeName);
        }

        int iconY = card.coords().bottomYPadded() - 10;

        int idX = card.coords().leftXPadded();
        int idY = card.coords().topYPadded();
        // One line; a title that won't fit ends in "…" and shows whole on hover. It used to run
        // past the card's right border ("Small Storeroom -> Storeroom", r3 #6). Leave room for
        // the removal "x" when the card has one.
        int titleRight = card.coords().rightXPadded() - (recipe.getBatchUUID() != null ? buttonWidth + 2 : 0);
        String fullTitle = recipeName.getString();
        String shownTitle = Compat.fitToWidth(this.font, fullTitle, titleRight - idX);
        ctx.text(shownTitle, idX, card.coords().topYPadded(), TEXT_COLOR, false);
        String vID = recipe.villagerUUID();
        String jobName = recipe.jobName();

        // "Villager N" when the menu sync carries display names (flag quest cards),
        // otherwise the historical truncated UUID.
        Component tooltip = Compat.translatable("quests.job_owner", menu.villagerLabelFor(vID));

        CardCoordinates shiftedUp = card.coords().shiftedUp(5);
        @Nullable Component removalTooltip = null;
        if (recipe.getBatchUUID() != null) {
            removalTooltip = renderRemovalButton(ctx, mouseX, mouseY, shiftedUp, recipe.getBatchUUID());
        }

        Coordinate mouse = new Coordinate(mouseX, mouseY);
        @Nullable Component renderedHeadTooltip = null;
        if (!vID.isEmpty()) {
            renderedHeadTooltip = renderHead(ctx, card, mouse, jobName, tooltip, vID, shiftedUp);
        }

        @Nullable List<Component> renderedItemTooltip = renderRecipeCardIcons(
                ctx,
                recipe,
                new Coordinate(card.coords().leftXPadded(), iconY),
                card.coords().padded().bottomRight(),
                mouse
        );

        if (!shownTitle.equals(fullTitle) && UtilClean.isCoordInBox(
                mouse, new Coordinate(idX, idY), new Coordinate(titleRight, idY + this.font.lineHeight))) {
            return ImmutableList.of(Compat.literal(fullTitle));
        }
        return renderCardTooltip(card, removalTooltip, renderedHeadTooltip, renderedItemTooltip, mouse, recipe,
                this.tabs instanceof Tabs t && t.hasTab(VILLAGERS_TAB));
    }

    private static @Nullable List<Component> renderCardTooltip(
            Card<UIQuest> card,
            @Nullable Component removalTooltip,
            @Nullable Component renderedHeadTooltip,
            @Nullable List<Component> renderedItemTooltip,
            Coordinate mouse,
            UIQuest recipe,
            boolean villagersTabShown
    ) {
        if (removalTooltip != null) {
            return ImmutableList.of(removalTooltip);
        }

        if (renderedHeadTooltip != null) {
            return ImmutableList.of(renderedHeadTooltip);
        }

        if (renderedItemTooltip != null) {
            return renderedItemTooltip;
        }

        if (!UtilClean.isCoordInBox(mouse, card.coords().topLeft(), card.coords().bottomRight())) {
            return null;
        }

        // RoomBlock cards (welcome mat, empty room) get a fixed two-line tooltip -
        // the action copy plus a grey "Click to open crafting" line - REGARDLESS of
        // flavor text: the flavor is set server-side at post time and shadows the
        // switch below, which is why the empty-room card shipped showing the generic
        // room_quest line instead of its own copy (user report, 2026-10-14).
        // JOB_BOARD joined the mat/room format on the same day (playtest chat):
        // two-line tooltip + click-to-craft deep link, like the welcome mat.
        if (recipe.getType() == Quest.QuestType.ROOM
                && (SpecialQuests.TOWN_GATE.equals(recipe.getWantedId())
                        || SpecialQuests.EMPTY_ROOM.equals(recipe.getWantedId())
                        || SpecialQuests.JOB_BOARD.equals(recipe.getWantedId()))) {
            String key;
            if (SpecialQuests.TOWN_GATE.equals(recipe.getWantedId())) {
                key = "menu.quests.roomblock.welcome_mat";
            } else if (SpecialQuests.JOB_BOARD.equals(recipe.getWantedId())) {
                key = "menu.quests.roomblock.job_board";
            } else {
                key = "menu.quests.roomblock.empty_room";
            }
            return ImmutableList.of(
                    Compat.translatable(key),
                    Compat.translatableStyled(
                            "menu.quests.roomblock.click_crafting",
                            Style.EMPTY.applyFormat(ChatFormatting.GRAY)));
        }

        if (recipe.getType() == Quest.QuestType.JOB_CHANGE) {
            return jobChangeTooltip(recipe, villagersTabShown);
        }

        if (recipe.getFlavorText() != null && !recipe.getFlavorText().isEmpty()) {
            return ImmutableList.of(Compat.literal(recipe.getFlavorText()));
        }

        return switch (recipe.getType()) {
            case ITEM -> renderItemQuestTooltip(recipe);
            case LOOT -> renderLootQuestTooltip(recipe);
            case ROOM -> {
                if (SpecialQuests.CAMPFIRE.equals(recipe.getWantedId())) {
                    yield ImmutableList.of(Compat.translatable("menu.quests.campfire_quest"));
                }
                // RoomBlock quests (roomblock-quests-custom-place-tooltip.md): the quest is
                // about placing a functional block, not crafting/collecting an item to hold.
                // TOWN_GATE and EMPTY_ROOM are handled above the flavor check; the
                // remaining room blocks get the free-placement line.
                if (SpecialQuests.JOB_BOARD.equals(recipe.getWantedId())
                        || RoomBlock.isBlockRoom(recipe.getWantedId())) {
                    yield ImmutableList.of(Compat.translatable(
                            "menu.quests.roomblock.town", RoomRecipes.getName(recipe.getWantedId())));
                }
                yield ImmutableList.of(Compat.translatable(RoomQuestTooltipKeys.forQuest(recipe.fromRecipe)));
            }
            case JOB_CHANGE -> renderJobQuestTooltip(recipe);
            case JOB_UNLOCK -> renderJobUnlockTooltip(recipe);
            case CONCURRENT_JOBS -> ImmutableList.of(Compat.literal("Have all listed jobs active at the same time"));
            case FIRST_WORK_REQUEST -> ImmutableList.of();
            case UNKNOWN -> null;
        };
    }

    private static ImmutableList<Component> renderItemQuestTooltip(UIQuest recipe) {
        int size = recipe.getIngredients().size();
        Component name = Compat.getItemName(recipe.getWantedId());
        return ImmutableList.of(Compat.translatable("menu.quests.item_quest", size, name));
    }

    // A LOOT quest is satisfied by production (a townie extracting the loot), not by a chest
    // deposit, so the tooltip must not promise the storage path the ITEM tooltip describes.
    private static ImmutableList<Component> renderLootQuestTooltip(UIQuest recipe) {
        int size = recipe.getIngredients().size();
        Component name = recipe.getIngredients().isEmpty()
                ? Compat.getItemName(recipe.getWantedId())
                : Ingredients.getName(recipe.getIngredients().iterator().next());
        return ImmutableList.of(Compat.translatable("menu.quests.loot_quest", size, name));
    }

    /**
     * The card's own copy (flavor text, else the job line) plus a grey click hint while the
     * villagers tab exists - a click opens it (2026-10-17 playtest feature request). Before
     * the first villager the tab is hidden, the click opens the BOP lesson, and no hint shows.
     */
    private static List<Component> jobChangeTooltip(UIQuest recipe, boolean villagersTabShown) {
        List<Component> base = recipe.getFlavorText() != null && !recipe.getFlavorText().isEmpty()
                ? ImmutableList.of(Compat.literal(recipe.getFlavorText()))
                : renderJobQuestTooltip(recipe);
        if (!villagersTabShown) {
            return base;
        }
        return ImmutableList.<Component>builder()
                .addAll(base)
                .add(Compat.translatableStyled(
                        "menu.quests.job_change.click_villagers",
                        Style.EMPTY.applyFormat(ChatFormatting.GRAY)))
                .build();
    }

    private static ImmutableList<Component> renderJobQuestTooltip(UIQuest recipe) {
        JobID job = JobID.fromRL(recipe.getWantedId());
        return ImmutableList.of(
                Compat.translatable("menu.questown.quests.job_quest_1", job.jobId()),
                Compat.translatable("menu.questown.quests.job_quest_2", Compat.translatable("jobs." + job.rootId())),
                Compat.literal("Click for more info")
        );
    }

    private static ImmutableList<Component> renderJobUnlockTooltip(UIQuest recipe) {
        JobID job = JobID.fromRL(recipe.getWantedId());
        return ImmutableList.of(
                Compat.translatable("menu.questown.quests.job_unlock_1", Compat.translatable("jobs." + job.rootId())),
                Compat.literal("Click for more info")
        );
    }

    private @Nullable Component renderHead(
            RenderContext ctx,
            Card<UIQuest> card,
            Coordinate mouse,
            String jobName,
            Component tooltip,
            String vID,
            CardCoordinates shiftedUp
    ) {
        boolean hasJob = jobName.isEmpty();
        if (!hasJob) {
            tooltip = Compat.translatable("quests.job_change", vID, jobName);
        }

        boolean showHead = !hasJob;
        if (UtilClean.isCoordInBox(mouse, card.coords().topLeft(), card.coords().bottomRight())) {
            showHead = true;
        }

        if (showHead) {
            int headX = card.coords().rightX() - 19 - 20;
            int headY = shiftedUp.topYPadded() - 1;
            Util.blitFace(ctx.gui(), UUID.fromString(vID), headX, headY + 1, 2);
            if (UtilClean.isCoordInBox(
                    mouse,
                    new Coordinate(headX, headY + 1),
                    new Coordinate(headX + 16, headY + 17)
            )) {
                ctx.fill(headX, headY + 1, headX + 16, headY + 17, 0x80FFFFFF);
                return tooltip;
            }
        }
        return null;
    }

    private @Nullable Component renderRemovalButton(
            RenderContext ctx,
            int mouseX,
            int mouseY,
            CardCoordinates coords,
            UUID index
    ) {
        int removeButtonX = coords.rightXPadded() - buttonWidth;
        int removeButtonY = coords.topYPadded();
        ctx.lightText(
                "x",
                (int) (removeButtonX + ((float) buttonWidth / 2) - 1),
                (int) (removeButtonY + ((float) buttonWidth / 2) - 3)
        );
        this.removes.put(new Position(removeButtonX, removeButtonY), () -> menu.sendRemoveRequest(index));
        return highlightAndTooltip(
                ctx,
                mouseX,
                mouseY,
                removeButtonX,
                removeButtonY,
                Compat.translatable("job_board.remove_work")
        );
    }


    private @Nullable Component highlightAndTooltip(
            RenderContext ctx,
            int mouseX,
            int mouseY,
            int iconX,
            int iconY,
            Component tooltipText
    ) {
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - backgroundHeight) / 2;
        if (this.tabs.renderTooltip(
                bgX,
                bgY,
                mouseX,
                mouseY,
                key -> this.showTooltip(Compat.translatable(key))
        )) {
            return null;
        }
        if (mouseX >= iconX && mouseY >= iconY && mouseX < iconX + 16 && mouseY < iconY + 16) {
            // transparent white square behind hovered item slot
            ctx.fill(iconX, iconY, iconX + 16, iconY + 16, 0x80FFFFFF);
            // return hovered item's name as a tooltip to be rendered
            return tooltipText;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(
            double x,
            double y,
            int p_97750_
    ) {
        for (Map.Entry<Position, Runnable> p : removes.entrySet()) {
            int buttonX = p.getKey().x;
            int buttonY = p.getKey().z;
            if (x >= buttonX && y >= buttonY && x < buttonX + 16 && y < buttonY + 17) {
                p.getValue().run();
                return true;
            }
        }
        for (Map.Entry<Position, Ingredient> icon : itemIconClicks.entrySet()) {
            int iconX = icon.getKey().x;
            int iconY = icon.getKey().z;
            if (x >= iconX - 4 && y >= iconY && x < iconX + 20 && y < iconY + 16) {
                menu.sendOpenItemJobs(icon.getValue());
                return true;
            }
        }
        if (openLessonForClickedCard(x, y)) {
            return true;
        }
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - backgroundHeight) / 2;
        this.tabs.mouseClicked(bgX, bgY, x, y);
        return super.mouseClicked(x, y, p_97750_);
    }

    private @Nullable List<Component> renderRecipeCardIcons(
            RenderContext ctx,
            UIQuest recipe,
            Coordinate stripTopLeft,
            Coordinate cardBottomRight,
            Coordinate mouse
    ) {
        Collection<Ingredient> ingredients = recipe.getIngredients();
        ingredients = RoomRecipes.filterSpecialBlocks(ingredients);
        if (recipe.getType() == Quest.QuestType.JOB_CHANGE) {
            // Job-change quests ALWAYS require the Block of Progress, so its
            // icon is pure repetition - the card's item row is left empty
            // instead (user call, 2026-10-15 playtest: "We can just not render
            // the BOP").
            ingredients = ImmutableList.of();
        }
        if (ingredients.isEmpty() && SpecialQuests.EMPTY_ROOM.equals(recipe.getWantedId())) {
            // D#2: empty_room matches any room (zero required contents), so the card has
            // no ingredient strip. Show the oak door - the block you actually point the
            // Town Wand at - as the card's icon (user call, 2026-10-13 playtest).
            ingredients = ImmutableList.of(Ingredient.of(Items.OAK_DOOR));
        }

        // 2026-10-17 playtest: an ITEM/LOOT card's icon opens "jobs that make this item"
        // (for a tag icon, the jobs that make any member - the raw-food quest's case).
        boolean clickable = recipe.getType() == Quest.QuestType.ITEM || recipe.getType() == Quest.QuestType.LOOT;
        return RenderUtil.stripOfRequestableItems(
                (ing, coord) -> {
                    Ingredients.render(ctx, ing, coord.x(), coord.y());
                    if (clickable) {
                        itemIconClicks.put(new Position(coord.x(), coord.y()), ing);
                    }
                },
                (ing) -> clickable
                        ? ImmutableList.of(
                                Ingredients.getName(ing),
                                Compat.translatableStyled(
                                        "menu.quests.item.click_item_jobs",
                                        Style.EMPTY.applyFormat(ChatFormatting.GRAY)))
                        : ImmutableList.of(Ingredients.getName(ing)),
                (text, coord) -> ctx.darkText(text, coord.x(), coord.y()),
                (topLeft, botRight) -> RenderUtil.highlight(ctx.gui(), topLeft, botRight),
                ImmutableList.copyOf(ingredients),
                stripTopLeft,
                cardBottomRight,
                mouse
        );
    }

    @Override
    protected void renderBgContent(
            RenderContext ctx,
            float partialTicks,
            int mouseX,
            int mouseY
    ) {
        itemIconClicks.clear();
        super.renderBgContent(ctx, partialTicks, mouseX, mouseY);
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        ctx.drawNineNine(this.background, x, y, backgroundWidth, backgroundHeight);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final ResourceLocation BOOK_ID = new ResourceLocation(Questown.MODID, "intro");

    private boolean openLessonForClickedCard(double mouseX, double mouseY) {
        for (Card<UIQuest> card : cards()) {
            CardCoordinates c = card.coords();
            if (mouseX < c.leftX() || mouseX > c.rightX() || mouseY < c.topY() || mouseY > c.bottomY()) {
                continue;
            }
            UIQuest quest = cardsData().get(card.index());
            if (quest == null) {
                continue;
            }
            // Day-1 playtest: the welcome-mat quest is answered at the flag's crafting tab,
            // not in the book - deep-link there. Hardcoded for now (the player's ask):
            // "Clicking on the welcome mat quest should take me to the flag's crafting tab."
            // The empty-room card deep-links there too: its grey line says "Click to open
            // crafting" because the Town Wand - the one thing it needs - is crafted there.
            if ((SpecialQuests.TOWN_GATE.equals(quest.getWantedId())
                    || SpecialQuests.EMPTY_ROOM.equals(quest.getWantedId())
                    || SpecialQuests.JOB_BOARD.equals(quest.getWantedId()))
                    && this.tabs instanceof Tabs tabs && tabs.openTab("tooltips.crafting")) {
                return true;
            }
            if (quest.getType() == Quest.QuestType.JOB_CHANGE
                    && this.tabs instanceof Tabs tabs && tabs.openTab(VILLAGERS_TAB)) {
                return true;
            }
            ResourceLocation entry = lessonEntryForQuestType(quest.getType());
            if (entry == null) {
                continue;
            }
            // A wrong/unknown entry id used to NPE inside the journal call, crashing
            // the client. The journal facade never throws, but keep the guard in case
            // a future target restores strict entry lookup.
            try {
                Journal.openBookEntry(null, BOOK_ID, entry, 0);
            } catch (RuntimeException e) {
                QT.GUI_LOGGER.warn("Could not open book entry {} (quest type {})", entry, quest.getType(), e);
                return false;
            }
            return true;
        }
        return false;
    }

    // Entry ids are the file path under en_us/, and every entry file lives in entries/.
    private static @Nullable ResourceLocation lessonEntryForQuestType(Quest.QuestType type) {
        return switch (type) {
            case JOB_CHANGE -> new ResourceLocation(Questown.MODID, "entries/lesson_bop");
            case JOB_UNLOCK -> new ResourceLocation(Questown.MODID, "entries/lesson_bop");
            case ROOM -> new ResourceLocation(Questown.MODID, "entries/lesson_room_recipes");
            case ITEM, LOOT -> new ResourceLocation(Questown.MODID, "entries/lesson_needs");
            case CONCURRENT_JOBS -> new ResourceLocation(Questown.MODID, "entries/014-supply-chains");
            default -> null;
        };
    }

    public List<Rect2i> getExtraAreas() {
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        return ImmutableList.of(new Rect2i(x, y, backgroundWidth, backgroundHeight));
    }

    @Override
    public boolean isMouseOver(
            double mouseX,
            double mouseY
    ) {
        return true;
    }

    public static QuestsScreen<TownQuestsContainer> forTown(
            TownQuestsContainer container,
            Inventory playerInv,
            Component title
    ) {
        return new QuestsScreen<>(container, playerInv, title, FlagTabs.forMenu(container, "tooltips.quests"));
    }

    public static QuestsScreen<VillagerQuestsContainer> forVillager(
            VillagerQuestsContainer menu,
            Inventory inventory,
            Component component
    ) {
        return new QuestsScreen<>(menu, inventory, component, VillagerTabs.forMenu(menu));
    }

    @Override
    protected void setRenderColorForCard(UIQuest data) {
        if (Quest.QuestStatus.COMPLETED.equals(data.status)) {
            RenderSystem.setShaderColor(0.8f, 1.0f, 0.8f, 1.0f);
        }
        if (data.isBroken) {
            RenderSystem.setShaderColor(0.85f, 0.75f, 1.0f, 1.0f);
        }
    }
}