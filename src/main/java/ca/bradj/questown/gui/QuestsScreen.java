package ca.bradj.questown.gui;

import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.logic.RoomRecipes;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.JEI;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.quests.Quest;
import ca.bradj.questown.town.special.SpecialQuests;
import ca.bradj.questown.gui.PagedCardScreen.Card;
import ca.bradj.questown.gui.PagedCardScreen.CardCoordinates;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PlayerHeadItem;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static ca.bradj.questown.gui.PagedCardScreen.buttonWidth;

public class QuestsScreen<C extends AbstractQuestsContainer> extends AbstractPagedCardScreen<C, UIQuest> {
    private static final int backgroundWidth = 176;
    private static final int backgroundHeight = 166;

    private static final int TEXT_COLOR = 0x404040;

    private final List<UIQuest> quests;
    private final JEI.NineNine background;
    private final List<ItemStack> heads;
    private final Map<Position, Runnable> removes = new HashMap<>();
    private final SubUI tabs;

    private ImmutableList.Builder<Slot> slotsBuilder = ImmutableList.builder();

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
        this.background = JEI.getRecipeGuiBackground();

        this.heads = quests.stream().map(v -> {
            if (v.villagerUUID() == null) {
                return null;
            }
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.getOrCreateTag().putString(PlayerHeadItem.TAG_SKULL_OWNER, v.villagerUUID());
            return head;
        }).toList();
        this.tabs = tabs;
    }

    @Override
    protected ImmutableList<UIQuest> cardsData() {
        return ImmutableList.copyOf(quests);
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
                bgX, bgY, mouseX, mouseY,
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
        slotsBuilder = ImmutableList.builder();
        super.render(poseStack, mouseX, mouseY, partialTicks);
        slots = slotsBuilder.build();
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - backgroundHeight) / 2;
        this.tabs.draw(new RenderContext(itemRenderer, poseStack), bgX, bgY);
    }

    @Override
    protected void renderCardContent(
            PoseStack poseStack,
            Card<UIQuest> card,
            int mouseX,
            int mouseY
    ) {
        UIQuest recipe = quests.get(card.index());
        if (recipe == null) {
            return;
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
        ImmutableList<Slot> slotz = renderRecipeCardIcons(
                poseStack,
                recipe,
                card.coords().leftXPadded(),
                iconY,
                mouseX,
                mouseY
        );
        slotsBuilder.addAll(slotz);

        int idX = card.coords().leftXPadded();
        int idY = card.coords().topYPadded();
        this.font.draw(poseStack, recipeName.getString(), idX, card.coords().topYPadded(), TEXT_COLOR);
        String vID = recipe.villagerUUID();
        String jobName = recipe.jobName();

        Component tooltip = Compat.translatable(
                "quests.job_owner",
                getVillagerName(vID)
        );

        CardCoordinates shiftedUp = card.coords().shiftedUp(5);
        if (recipe.getBatchUUID() != null) {
            renderRemovalButton(poseStack, mouseX, mouseY, shiftedUp, recipe.getBatchUUID());
        }

        if (vID.isEmpty()) {
            return;
        }

        boolean hasJob = jobName.isEmpty();
        if (!hasJob) {
            tooltip = Compat.translatable("quests.job_change", vID, jobName);
        }

        boolean showHead = !hasJob;
        if (mouseX >= card.coords().leftX() && mouseY >= card.coords().topY() && mouseX < card.coords()
                                                                                              .rightX() && mouseY < card.coords()
                                                                                                                        .bottomY()) {
            showHead = true;
        }

        if (showHead) {
            int headX = card.coords().rightX() - 19 - 20;
            int headY = shiftedUp.topYPadded() - 1;
            Util.blitFace(poseStack, UUID.fromString(vID), headX, headY + 1, 2);
            if (mouseX >= headX && mouseY >= headY && mouseX < headX + 16 && mouseY < headY + 17) {
                fill(poseStack, headX, headY + 1, headX + 16, headY + 17, 0x80FFFFFF);
                renderTooltip(poseStack, tooltip, mouseX, mouseY);
            }
        }
//        slots.clear();
//        slots.addAll(b.build());
//
    }

    private static @NotNull String getVillagerName(String vID) {
        return UtilClean.truncateMiddle(vID);
    }

    private void renderRemovalButton(
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
        highlightAndTooltip(
                poseStack,
                mouseX,
                mouseY,
                removeButtonX,
                removeButtonY,
                Compat.translatable("job_board.remove_work")
        );
        this.removes.put(new Position(removeButtonX, removeButtonY), () -> menu.sendRemoveRequest(index));
    }


    private void highlightAndTooltip(
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
                bgX, bgY, mouseX, mouseY,
                key -> super.renderTooltip(poseStack, Compat.translatable(key), mouseX, mouseY)
        )) {
            return;
        }
        if (mouseX >= iconX && mouseY >= iconY && mouseX < iconX + 16 && mouseY < iconY + 16) {
            // transparent white square behind hovered item slot
            fill(poseStack, iconX, iconY, iconX + 16, iconY + 16, 0x80FFFFFF);
            // render hovered item's name as a tooltip
            renderTooltip(poseStack, tooltipText, mouseX, mouseY);
        }
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
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - backgroundHeight) / 2;
        this.tabs.mouseClicked(bgX, bgY, x, y);
        return super.mouseClicked(x, y, p_97750_);
    }


    private List<Slot> slots = new ArrayList<>();

    private ImmutableList<Slot> renderRecipeCardIcons(
            PoseStack poseStack,
            UIQuest recipe,
            int x,
            int y,
            int mouseX,
            int mouseY
    ) {
        Inventory dummyInv = new Inventory(null);
        Collection<Ingredient> ingredients = recipe.getIngredients();
        ingredients = RoomRecipes.filterSpecialBlocks
                                         (ingredients);
        int j = 0;

        ImmutableList.Builder<Slot> b = ImmutableList.builder();

        for (Ingredient ing : ingredients) {
            int iconX = x + j * 18;

            @Nullable ItemStack is = Ingredients.render(itemRenderer, ing, iconX, y + 1);
            if (is != null) {
                if (UtilClean.isCoordInBox(mouseX, mouseY, iconX, y, 16, 17)) {
                    highlightAndTooltip(poseStack, y, mouseX, mouseY, iconX, is);
                }
                Slot element = new Slot(dummyInv, j, iconX, y + 1);
                element.set(is);
                b.add(element);
            }
            j++;
        }
        return b.build();
    }

    private void highlightAndTooltip(
            PoseStack poseStack,
            int y,
            int mouseX,
            int mouseY,
            int iconX,
            @NotNull ItemStack is
    ) {
        // transparent white square behind hovered item slot
        fill(poseStack, iconX, y + 1, iconX + 16, y + 17, 0x80FFFFFF);
        // render hovered item's name as a tooltip
        renderTooltip(poseStack, is.getItem().getName(is), mouseX, mouseY);
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

    private void renderSlot(
            PoseStack poseStack,
            Slot slot,
            int mouseX,
            int mouseY,
            float partialTicks
    ) {
        ItemStack stack = slot.getItem();
        if (!stack.isEmpty()) {
            this.minecraft.getItemRenderer().renderGuiItem(stack, slot.x, slot.y);
            this.minecraft.getItemRenderer().renderGuiItemDecorations(this.font, stack, slot.x, slot.y, "");
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public List<Rect2i> getExtraAreas() {
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        return ImmutableList.of(
                new Rect2i(x, y, backgroundWidth, backgroundHeight)
        );
    }

    public ItemStack getHoveredIngredient(
            int mouseX,
            int mouseY
    ) {
        Predicate<Slot> slotPredicate = s -> mouseX >= s.x && mouseX <= s.x + 16 && mouseY >= s.y + 1 && mouseY <= s.y + 17;
        Stream<Slot> matches = slots.stream().filter(slotPredicate);
        Optional<Slot> found = matches.findAny();
        return found.map(Slot::getItem).orElse(ItemStack.EMPTY);
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
        return new QuestsScreen<>(
                container, playerInv, title,
                FlagTabs.forMenu(container)
        );
    }

    public static QuestsScreen<VillagerQuestsContainer> forVillager(
            VillagerQuestsContainer menu,
            Inventory inventory,
            Component component
    ) {
        return new QuestsScreen<>(
                menu, inventory, component,
                VillagerTabs.forMenu(menu)
        );
    }

    @Override
    protected void setRenderColorForCard(UIQuest data) {
        if (Quest.QuestStatus.COMPLETED.equals(data.status)) {
            RenderSystem.setShaderColor(0.8f, 1.0f, 0.8f, 1.0f);
        }
        if (SpecialQuests.BROKEN.equals(data.getRecipeId())) {
            RenderSystem.setShaderColor(0.85f, 0.75f, 1.0f, 1.0f);
        }
    }
}