package ca.bradj.questown.gui;

import ca.bradj.questown.Questown;
import ca.bradj.questown.town.special.SpecialQuests;
import ca.bradj.questown.core.Coordinate;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.gui.PagedCardScreen.Card;
import ca.bradj.questown.gui.PagedCardScreen.CardCoordinates;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.logic.RoomRecipes;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.JEI;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.quests.Quest;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import vazkii.patchouli.api.PatchouliAPI;

import java.util.*;

import static ca.bradj.questown.gui.PagedCardScreen.buttonWidth;

public class QuestsScreen<C extends AbstractQuestsContainer> extends AbstractPagedCardScreen<C, UIQuest> {
    private static final int backgroundWidth = 176;
    private static final int backgroundHeight = 166;

    private static final int TEXT_COLOR = 0x404040;
    private static final int TEXT_MARGIN = 12;
    private static final int BLOCK_GAP = 6;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_H_PADDING = 16;

    private final List<UIQuest> quests;
    private final JEI.NineNine background;
    private final Map<Position, Runnable> removes = new HashMap<>();
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

    @Override
    protected ImmutableList<UIQuest> cardsData() {
        if (allComplete && !showingCompleted) {
            return ImmutableList.of();
        }
        return ImmutableList.copyOf(quests);
    }

    @Override
    protected void init() {
        super.init();
        this.viewCompletedButton = null;
        if (!allComplete || showingCompleted) {
            return;
        }
        Component link = Compat.translatable("menu.quests.view_completed");
        int btnW = this.font.width(link) + BUTTON_H_PADDING;
        this.viewCompletedButton = new Button(
                (this.width - btnW) / 2, emptyStateButtonY(), btnW, BUTTON_HEIGHT, link,
                btn -> {
                    showingCompleted = true;
                    btn.visible = false;
                    btn.active = false;
                });
        this.addRenderableWidget(this.viewCompletedButton);
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
    protected void renderLabels(
            PoseStack p_97808_,
            int p_97809_,
            int p_97810_
    ) {
    }

    @Override
    protected void renderTooltip(
            PoseStack stack,
            int mouseX,
            int mouseY
    ) {
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - backgroundHeight) / 2;
        if (this.tabs.renderTooltip(
                bgX,
                bgY,
                mouseX,
                mouseY,
                key -> super.renderTooltip(stack, Compat.translatable(key), mouseX, mouseY)
        )) {
            return;
        }
        super.renderTooltip(stack, mouseX, mouseY);
    }

    @Override
    public void render(
            PoseStack poseStack,
            int mouseX,
            int mouseY,
            float partialTicks
    ) {
        super.render(poseStack, mouseX, mouseY, partialTicks);
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - backgroundHeight) / 2;
        this.tabs.draw(new RenderContext(itemRenderer, poseStack), bgX, bgY);
        if (allComplete && !showingCompleted) {
            renderAllDoneState(poseStack);
        }
    }

    private void renderAllDoneState(PoseStack poseStack) {
        // Draw the wrapped subtitle above the "View completed" button (which is a real widget added
        // in init() at emptyStateButtonY(), so it renders and hit-tests itself).
        int textWidth = backgroundWidth - 2 * TEXT_MARGIN;
        drawCenteredWrapped(poseStack, subtitleComponent(), this.width / 2, emptyStateTop(), textWidth, TEXT_COLOR);
    }

    private int wrappedHeight(Component text, int maxWidth) {
        return Compat.splitText(this.font, text, maxWidth).size() * this.font.lineHeight;
    }

    /** Draw {@code text} wrapped to {@code maxWidth}, each line centred on {@code centerX}. Returns height. */
    private int drawCenteredWrapped(
            PoseStack poseStack,
            Component text,
            int centerX,
            int y,
            int maxWidth,
            int color
    ) {
        int dy = 0;
        for (FormattedCharSequence line : Compat.splitText(this.font, text, maxWidth)) {
            int x = centerX - (this.font.width(line) / 2);
            this.font.draw(poseStack, line, x, y + dy, color);
            dy += this.font.lineHeight;
        }
        return dy;
    }


    @Override
    protected List<Component> renderCardContent(
            PoseStack poseStack,
            Card<UIQuest> card,
            int mouseX,
            int mouseY
    ) {
        UIQuest recipe = quests.get(card.index());
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
        this.font.draw(poseStack, recipeName.getString(), idX, card.coords().topYPadded(), TEXT_COLOR);
        String vID = recipe.villagerUUID();
        String jobName = recipe.jobName();

        Component tooltip = Compat.translatable("quests.job_owner", getVillagerName(vID));

        CardCoordinates shiftedUp = card.coords().shiftedUp(5);
        @Nullable Component removalTooltip = null;
        if (recipe.getBatchUUID() != null) {
            removalTooltip = renderRemovalButton(poseStack, mouseX, mouseY, shiftedUp, recipe.getBatchUUID());
        }

        Coordinate mouse = new Coordinate(mouseX, mouseY);
        @Nullable Component renderedHeadTooltip = null;
        if (!vID.isEmpty()) {
            renderedHeadTooltip = renderHead(poseStack, card, mouse, jobName, tooltip, vID, shiftedUp);
        }

        @Nullable List<Component> renderedItemTooltip = renderRecipeCardIcons(
                poseStack,
                recipe,
                new Coordinate(card.coords().leftXPadded(), iconY),
                card.coords().padded().bottomRight(),
                mouse
        );

        return renderCardTooltip(card, removalTooltip, renderedHeadTooltip, renderedItemTooltip, mouse, recipe);
    }

    private static @Nullable List<Component> renderCardTooltip(
            Card<UIQuest> card,
            @Nullable Component removalTooltip,
            @Nullable Component renderedHeadTooltip,
            @Nullable List<Component> renderedItemTooltip,
            Coordinate mouse,
            UIQuest recipe
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

        if (recipe.getFlavorText() != null && !recipe.getFlavorText().isEmpty()) {
            return ImmutableList.of(Compat.literal(recipe.getFlavorText()));
        }

        return switch (recipe.getType()) {
            case ITEM -> renderItemQuestTooltip(recipe);
            case ROOM -> {
                if (SpecialQuests.CAMPFIRE.equals(recipe.getWantedId())) {
                    yield ImmutableList.of(Compat.translatable("menu.quests.campfire_quest"));
                }
                yield ImmutableList.of(Compat.translatable("menu.quests.room_quest"));
            }
            case JOB_CHANGE -> renderJobQuestTooltip(recipe);
            case CONCURRENT_JOBS -> ImmutableList.of(Compat.literal("Have all listed jobs active at the same time"));
            case UNKNOWN -> null;
        };
    }

    private static ImmutableList<Component> renderItemQuestTooltip(UIQuest recipe) {
        int size = recipe.getIngredients().size();
        Component name = Compat.getItemName(recipe.getWantedId());
        return ImmutableList.of(Compat.translatable("menu.quests.item_quest", size, name));
    }

    private static ImmutableList<Component> renderJobQuestTooltip(UIQuest recipe) {
        JobID job = JobID.fromRL(recipe.getWantedId());
        return ImmutableList.of(
                Compat.translatable("menu.questown.quests.job_quest_1", job.jobId()),
                Compat.translatable("menu.questown.quests.job_quest_2", job.rootId()),
                Compat.literal("Click for more info")
        );
    }

    private @Nullable Component renderHead(
            PoseStack poseStack,
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
            Util.blitFace(poseStack, UUID.fromString(vID), headX, headY + 1, 2);
            if (UtilClean.isCoordInBox(
                    mouse,
                    new Coordinate(headX, headY + 1),
                    new Coordinate(headX + 16, headY + 17)
            )) {
                fill(poseStack, headX, headY + 1, headX + 16, headY + 17, 0x80FFFFFF);
                return tooltip;
            }
        }
        return null;
    }

    private static @NotNull String getVillagerName(String vID) {
        return UtilClean.truncateMiddle(vID);
    }

    private @Nullable Component renderRemovalButton(
            PoseStack poseStack,
            int mouseX,
            int mouseY,
            CardCoordinates coords,
            UUID index
    ) {
        int removeButtonX = coords.rightXPadded() - buttonWidth;
        int removeButtonY = coords.topYPadded();
        this.font.drawShadow(
                poseStack,
                Compat.literal("x"),
                removeButtonX + ((float) buttonWidth / 2) - 1,
                removeButtonY + ((float) buttonWidth / 2) - 3,
                0xFFFFFF
        );
        this.removes.put(new Position(removeButtonX, removeButtonY), () -> menu.sendRemoveRequest(index));
        return highlightAndTooltip(
                poseStack,
                mouseX,
                mouseY,
                removeButtonX,
                removeButtonY,
                Compat.translatable("job_board.remove_work")
        );
    }


    private @Nullable Component highlightAndTooltip(
            PoseStack poseStack,
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
                key -> super.renderTooltip(poseStack, Compat.translatable(key), mouseX, mouseY)
        )) {
            return null;
        }
        if (mouseX >= iconX && mouseY >= iconY && mouseX < iconX + 16 && mouseY < iconY + 16) {
            // transparent white square behind hovered item slot
            fill(poseStack, iconX, iconY, iconX + 16, iconY + 16, 0x80FFFFFF);
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
        if (openLessonForClickedCard(x, y)) {
            return true;
        }
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - backgroundHeight) / 2;
        this.tabs.mouseClicked(bgX, bgY, x, y);
        return super.mouseClicked(x, y, p_97750_);
    }

    private @Nullable List<Component> renderRecipeCardIcons(
            PoseStack poseStack,
            UIQuest recipe,
            Coordinate stripTopLeft,
            Coordinate cardBottomRight,
            Coordinate mouse
    ) {
        Collection<Ingredient> ingredients = recipe.getIngredients();
        ingredients = RoomRecipes.filterSpecialBlocks(ingredients);

        return RenderUtil.stripOfRequestableItems(
                (ing, coord) -> Ingredients.render(itemRenderer, ing, coord.x(), coord.y()),
                (ing) -> ImmutableList.of(Ingredients.getName(ing)),
                (text, coord) -> Compat.drawDarkText(font, poseStack, text, coord.x(), coord.y()),
                (topLeft, botRight) -> RenderUtil.highlight(poseStack, topLeft, botRight),
                ImmutableList.copyOf(ingredients),
                stripTopLeft,
                cardBottomRight,
                mouse
        );
    }

    @Override
    protected void renderBg(
            PoseStack poseStack,
            float partialTicks,
            int mouseX,
            int mouseY
    ) {
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        this.background.draw(poseStack, x, y, backgroundWidth, backgroundHeight);
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
            UIQuest quest = quests.get(card.index());
            if (quest == null) {
                continue;
            }
            ResourceLocation entry = lessonEntryForQuestType(quest.getType());
            if (entry == null) {
                continue;
            }
            PatchouliAPI.get().openBookEntry(BOOK_ID, entry, 0);
            return true;
        }
        return false;
    }

    private static @Nullable ResourceLocation lessonEntryForQuestType(Quest.QuestType type) {
        return switch (type) {
            case JOB_CHANGE -> new ResourceLocation(Questown.MODID, "lessons/lesson_bop");
            case ROOM -> new ResourceLocation(Questown.MODID, "lessons/lesson_room_recipes");
            case ITEM -> new ResourceLocation(Questown.MODID, "lessons/lesson_needs");
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
        return new QuestsScreen<>(container, playerInv, title, FlagTabs.forMenu(container));
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