package ca.bradj.questown.gui;

import ca.bradj.questown.core.network.FlagCraftRecipes;
import ca.bradj.questown.blocks.RoomBlock;
import ca.bradj.questown.town.special.SpecialQuests;
import ca.bradj.questown.core.Coordinate;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.gui.PagedCardScreen.Card;
import ca.bradj.questown.gui.PagedCardScreen.CardCoordinates;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.logic.RoomRecipes;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.quests.MCQuest;
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
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_H_PADDING = 16;
    private static final int FOOTER_GAP = 2;
    private static final int FOOTER_PADDING = 6;

    private final List<UIQuest> quests;
    private final NineSlice background;
    private final Map<Position, Runnable> removes = new HashMap<>();
    /** This frame's clickable item icons on ITEM and LOOT cards. */
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
        this.background = GuiArt.getRecipeGuiBackground();
        this.tabs = tabs;
    }

    /** Open quests, or the completed ones once the player asks for them. */
    @Override
    protected ImmutableList<UIQuest> cardsData() {
        if (showingCompleted) {
            return quests.stream()
                         .filter(q -> Quest.QuestStatus.COMPLETED.equals(q.status))
                         .collect(ImmutableList.toImmutableList());
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
        if (!anyCompleted()) {
            return;
        }
        Component link = Compat.translatable(showingCompleted ? "menu.quests.view_open" : "menu.quests.view_completed");
        int btnW = this.font.width(link) + BUTTON_H_PADDING;
        int btnY = footerButtonY();
        this.viewCompletedButton = Compat.button(
                link,
                btn -> {
                    showingCompleted = !showingCompleted;
                    firstPage();
                    rebuildWidgets();
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

    private int emptyStateTop() {
        int bgY = (this.height - backgroundHeight) / 2;
        int textWidth = backgroundWidth - 2 * TEXT_MARGIN;
        return bgY + (backgroundHeight - wrappedHeight(subtitleComponent(), textWidth)) / 2;
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

    /** Shown when there are no cards and no all-done summary (a villager's quests tab). */
    private void renderNothingToShow(RenderContext ctx) {
        int textWidth = backgroundWidth - 2 * TEXT_MARGIN;
        Component text = Compat.translatable("menu.quests.none_open");
        int bgY = (this.height - backgroundHeight) / 2;
        int top = bgY + (backgroundHeight - wrappedHeight(text, textWidth)) / 2;
        drawCenteredWrapped(ctx, text, this.width / 2, top, textWidth, TEXT_COLOR);
    }

    private void renderAllDoneState(RenderContext ctx) {
        int textWidth = backgroundWidth - 2 * TEXT_MARGIN;
        drawCenteredWrapped(ctx, subtitleComponent(), this.width / 2, emptyStateTop(), textWidth, TEXT_COLOR);
    }

    private int wrappedHeight(Component text, int maxWidth) {
        return Compat.splitText(this.font, text, maxWidth).size() * this.font.lineHeight;
    }

    /** Draws {@code text} wrapped and centred. Returns its height. */
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
        // One line; a title that doesn't fit ends in "…" and shows whole on hover.
        int titleRight = card.coords().rightXPadded() - (recipe.getBatchUUID() != null ? buttonWidth + 2 : 0);
        String fullTitle = recipeName.getString();
        String shownTitle = Compat.fitToWidth(this.font, fullTitle, titleRight - idX);
        ctx.text(shownTitle, idX, card.coords().topYPadded(), TEXT_COLOR, false);
        String vID = recipe.villagerUUID();
        String jobName = recipe.jobName();

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

        // Room-block cards (welcome mat, empty room, job board, mineshaft) always say how to place them
        // and that a click opens crafting.
        if (recipe.getType() == Quest.QuestType.ROOM && opensCrafting(recipe.getWantedId())) {
            String key;
            if (SpecialQuests.TOWN_GATE.equals(recipe.getWantedId())) {
                key = "menu.quests.roomblock.welcome_mat";
            } else if (SpecialQuests.JOB_BOARD.equals(recipe.getWantedId())) {
                key = "menu.quests.roomblock.job_board";
            } else if (FlagCraftRecipes.MINESHAFT_QUEST.equals(recipe.getWantedId())) {
                key = "menu.quests.roomblock.mineshaft";
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

        if (recipe.getType() == Quest.QuestType.SKILL_UNLOCK) {
            return withVillagersHint(skillUnlockTooltip(recipe), villagersTabShown);
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
                // Other room blocks: the quest is about placing the block.
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
            case FIRST_WORK_REQUEST, SKILL_UNLOCK, VILLAGER_LEVEL -> ImmutableList.of();
            case UNKNOWN -> null;
        };
    }

    private static ImmutableList<Component> renderItemQuestTooltip(UIQuest recipe) {
        int size = recipe.getIngredients().size();
        Component name = Compat.getItemName(recipe.getWantedId());
        return ImmutableList.of(Compat.translatable("menu.quests.item_quest", size, name));
    }

    // A LOOT quest is done by a villager bringing the item back, not by putting it in storage.
    private static ImmutableList<Component> renderLootQuestTooltip(UIQuest recipe) {
        int size = recipe.getIngredients().size();
        Component name = recipe.getIngredients().isEmpty()
                ? Compat.getItemName(recipe.getWantedId())
                : Ingredients.getName(recipe.getIngredients().iterator().next());
        return ImmutableList.of(Compat.translatable("menu.quests.loot_quest", size, name));
    }

    /** The card's text, plus a grey click hint once the villagers tab exists. */
    private static List<Component> jobChangeTooltip(UIQuest recipe, boolean villagersTabShown) {
        List<Component> base = recipe.getFlavorText() != null && !recipe.getFlavorText().isEmpty()
                ? ImmutableList.of(Compat.literal(recipe.getFlavorText()))
                : renderJobQuestTooltip(recipe);
        return withVillagersHint(base, villagersTabShown);
    }

    /** A skill card opens the villager's skills tab, like a job-change card. */
    private static List<Component> skillUnlockTooltip(UIQuest recipe) {
        if (recipe.getLockedUntilLevel() != null) {
            // A blank skill card says how far off it is, not what it is.
            return ImmutableList.of(Compat.translatable(
                    "menu.quests.skill_unlock.locked",
                    Compat.translatable("jobs." + MCQuest.skillTreeRoot(recipe.getWantedId())),
                    recipe.getLockedUntilLevel()
            ));
        }
        if (recipe.getFlavorText() != null && !recipe.getFlavorText().isEmpty()) {
            return ImmutableList.of(Compat.literal(recipe.getFlavorText()));
        }
        return ImmutableList.of();
    }

    private static boolean opensVillagersTab(Quest.QuestType type) {
        return type == Quest.QuestType.JOB_CHANGE || type == Quest.QuestType.SKILL_UNLOCK;
    }

    private static List<Component> withVillagersHint(List<Component> base, boolean villagersTabShown) {
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
        if (openTabForClickedCard(x, y)) {
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
            // Job-change quests always need a Block of Progress, so the card leaves it out.
            ingredients = ImmutableList.of();
        }
        if (ingredients.isEmpty() && SpecialQuests.EMPTY_ROOM.equals(recipe.getWantedId())) {
            // Any room counts, so the card shows the door the wand is used on.
            ingredients = ImmutableList.of(Ingredient.of(Items.OAK_DOOR));
        }

        // An item icon opens the jobs that make it (for a tag, any of its items).
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
        this.background.draw(ctx, x, y, backgroundWidth, panelHeight());
    }

    /** The panel grows to hold the "View completed quests" button while it shows. */
    private int panelHeight() {
        if (!footerButtonShowing()) {
            return backgroundHeight;
        }
        return backgroundHeight + FOOTER_GAP + BUTTON_HEIGHT + FOOTER_PADDING;
    }

    private boolean footerButtonShowing() {
        return viewCompletedButton != null && viewCompletedButton.visible && (!allComplete || showingCompleted);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Quests done at the flag's crafting tab: the card opens it. */
    static boolean opensCrafting(ResourceLocation wantedId) {
        return SpecialQuests.TOWN_GATE.equals(wantedId)
                || SpecialQuests.EMPTY_ROOM.equals(wantedId)
                || SpecialQuests.JOB_BOARD.equals(wantedId)
                || FlagCraftRecipes.MINESHAFT_QUEST.equals(wantedId);
    }

    private boolean openTabForClickedCard(double mouseX, double mouseY) {
        for (Card<UIQuest> card : cards()) {
            CardCoordinates c = card.coords();
            if (mouseX < c.leftX() || mouseX > c.rightX() || mouseY < c.topY() || mouseY > c.bottomY()) {
                continue;
            }
            UIQuest quest = cardsData().get(card.index());
            if (quest == null) {
                continue;
            }
            if (opensCrafting(quest.getWantedId())
                    && this.tabs instanceof Tabs tabs && tabs.openTab("tooltips.crafting")) {
                return true;
            }
            if (opensVillagersTab(quest.getType())
                    && this.tabs instanceof Tabs tabs && tabs.openTab(VILLAGERS_TAB)) {
                return true;
            }
        }
        return false;
    }

    public List<Rect2i> getExtraAreas() {
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        return ImmutableList.of(new Rect2i(x, y, backgroundWidth, panelHeight()));
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