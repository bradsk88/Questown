package ca.bradj.questown.gui;

import ca.bradj.questown.logic.RoomRecipes;
import ca.bradj.questown.town.quests.Quest;
import ca.bradj.questown.town.special.SpecialQuests;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.Internal;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.common.util.MathUtil;
import mezz.jei.gui.elements.DrawableNineSliceTexture;
import mezz.jei.gui.elements.GuiIconButtonSmall;
import mezz.jei.gui.textures.Textures;
import mezz.jei.input.MouseUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PlayerHeadItem;
import net.minecraft.world.item.crafting.Ingredient;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class QuestsScreen extends AbstractContainerScreen<TownQuestsContainer> {
    private static final int backgroundWidth = 176;
    private static final int backgroundHeight = 166;
    private static final int borderPadding = 6;

    private static final int buttonWidth = 13;
    private static final int buttonHeight = 13;

    private static final int TEXT_COLOR = 0x404040;

    private static final int CARD_PADDING = 1;
    private static final int PAGE_PADDING = 10;
    private static final int CARD_WIDTH = (backgroundWidth) - (PAGE_PADDING * 2);
    private static final int CARD_HEIGHT = 42;

    private static final int MAX_CARDS_PER_PAGE = (backgroundHeight - PAGE_PADDING) / (CARD_HEIGHT + CARD_PADDING);

    private final List<UIQuest> quests;
    private final DrawableNineSliceTexture background;
    private final DrawableNineSliceTexture cardBackground;
    private final GuiIconButtonSmall nextPage;
    private final GuiIconButtonSmall previousPage;
    private final List<ItemStack> heads;
    private final Map<Position, Runnable> removes = new HashMap<>();
    private final SubUI tabs;
    private int currentPage = 0;

    public QuestsScreen(
            TownQuestsContainer container,
            Inventory playerInv,
            Component title,
            Runnable inventoryAndStatusScreen,
            Runnable statsScreen
    ) {
        this(
                container, playerInv, title,
                new VillagerTabs(
                        inventoryAndStatusScreen, null, statsScreen
                )
        );
    }

    public QuestsScreen(TownQuestsContainer container, Inventory playerInv, Component title, SubUI villagerTabs) {
        super(container, playerInv, title);
        super.imageWidth = 256;
        super.imageHeight = 220;

        this.quests = ImmutableList.copyOf(container.GetQuests());
        Textures textures = Internal.getTextures();
        this.background = textures.getRecipeGuiBackground();
        this.cardBackground = textures.getRecipeBackground();

        IDrawableStatic arrowNext = textures.getArrowNext();
        IDrawableStatic arrowPrevious = textures.getArrowPrevious();

        this.nextPage = new GuiIconButtonSmall(
                0, 0, buttonWidth, buttonHeight, arrowNext, b -> nextPage()
        );
        this.previousPage = new GuiIconButtonSmall(
                0, 0, buttonWidth, buttonHeight, arrowPrevious, b -> previousPage()
        );
        this.heads = quests.stream().map(v -> {
            if (v.villagerUUID() == null) {
                return null;
            }
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.getOrCreateTag().putString(PlayerHeadItem.TAG_SKULL_OWNER, v.villagerUUID());
            return head;
        }).toList();
        this.tabs = villagerTabs;
    }

    @Override
    protected void init() {
        int y = (this.height - backgroundHeight) / 2;
        int pageStringY = y + borderPadding;
        int x = ((this.width - backgroundWidth) / 2);
        this.previousPage.x = x + borderPadding;
        this.previousPage.y = pageStringY;
        this.nextPage.x = x + backgroundWidth - buttonWidth - borderPadding;
        this.nextPage.y = pageStringY;
        this.addRenderableWidget(this.previousPage);
        this.addRenderableWidget(this.nextPage);
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
    protected void renderLabels(PoseStack p_97808_, int p_97809_, int p_97810_) {
    }

    @Override
    protected void renderTooltip(PoseStack stack, int mouseX, int mouseY) {
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - backgroundHeight) / 2;
        if (this.tabs.renderTooltip(
                bgX, bgY, mouseX, mouseY,
                key -> super.renderTooltip(stack, new TranslatableComponent(key), mouseX, mouseY)
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
        this.renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTicks);

        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - backgroundHeight) / 2;
        int x = bgX;
        int y = bgY;
        int pageStringY = y + PAGE_PADDING;

        renderPageNum(poseStack, x);

        y = pageStringY + PAGE_PADDING;

        int startIndex = currentPage * MAX_CARDS_PER_PAGE;
        int endIndex = Math.min(startIndex + MAX_CARDS_PER_PAGE, quests.size());

        x = x + PAGE_PADDING;
        y = y + PAGE_PADDING;

        ImmutableList.Builder<Slot> b = ImmutableList.builder();
        for (int i = startIndex; i < endIndex; i++) {
            final int index = i;
            int row = i - startIndex;
            int cardY = y + row * (CARD_HEIGHT + CARD_PADDING);

            UIQuest recipe = quests.get(i);
            if (recipe == null) {
                continue;
            }
            Component recipeName = recipe.getName();
            if (recipe.fromRecipe != null) {
                Component fromName = RoomRecipes.getName(recipe.fromRecipe);
                recipeName = new TranslatableComponent("quests.upgrade", fromName, recipeName);
            }

            if (Quest.QuestStatus.COMPLETED.equals(recipe.status)) {
                RenderSystem.setShaderColor(0.8f, 1.0f, 0.8f, 1.0f);
                recipeName = new TranslatableComponent("quests.completed_suffix", recipeName);
            }
            if (SpecialQuests.BROKEN.equals(recipe.getRecipeId())) {
                RenderSystem.setShaderColor(0.85f, 0.75f, 1.0f, 1.0f);
            }
            this.cardBackground.draw(poseStack, x, cardY, CARD_WIDTH, CARD_HEIGHT);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

            int iconY = cardY + CARD_HEIGHT - 24;
            ImmutableList<Slot> slotz = renderRecipeCardIcons(poseStack, recipe, x, iconY, mouseX, mouseY);
            b.addAll(slotz);

            int idX = x + PAGE_PADDING;
            int idY = iconY - 10;
            this.font.draw(poseStack, recipeName.getString(), idX, idY, TEXT_COLOR);
            String vID = recipe.villagerUUID();
            String jobName = recipe.jobName();

            Component tooltip = new TranslatableComponent("quests.job_owner", vID);

            if (recipe.getBatchUUID() != null) {
                renderRemovalButton(poseStack, mouseX, mouseY, idX, idY, recipe.getBatchUUID());
            }

            if (vID.isEmpty()) {
                continue;
            }

            boolean hasJob = jobName.isEmpty();
            if (!hasJob) {
                tooltip = new TranslatableComponent("quests.job_change", vID, jobName);
            }

            boolean showHead = !hasJob;
            if (mouseX >= x && mouseY >= cardY && mouseX < x + CARD_WIDTH && mouseY < cardY + CARD_HEIGHT) {
                showHead = true;
            }

            if (showHead) {
                int headX = x + CARD_WIDTH - 19 - 19;
                int headY = idY - 6;
                this.itemRenderer.renderAndDecorateItem(heads.get(i), headX, headY);
                if (mouseX >= headX && mouseY >= headY && mouseX < headX + 16 && mouseY < headY + 17) {
                    fill(poseStack, headX, headY + 1, headX + 16, headY + 17, 0x80FFFFFF);
                    renderTooltip(poseStack, tooltip, mouseX, mouseY);
                }
            }
        }
        slots.clear();
        slots.addAll(b.build());

        // Render the page buttons
        this.previousPage.render(poseStack, mouseX, mouseY, partialTicks);
        this.nextPage.render(poseStack, mouseX, mouseY, partialTicks);

        this.tabs.draw(poseStack, bgX, bgY);
    }

    private void renderRemovalButton(
            PoseStack poseStack,
            int mouseX,
            int mouseY,
            int idX,
            int idY,
            UUID index
    ) {
        int removeX = idX + CARD_WIDTH - (PAGE_PADDING * 2) - buttonWidth;
        this.font.drawShadow(
                poseStack,
                new TextComponent("x"),
                removeX + borderPadding - 1,
                idY + borderPadding - 1,
                0xFFFFFF
        );
        highlightAndTooltip(
                poseStack,
                mouseX,
                mouseY,
                removeX,
                idY,
                new TranslatableComponent("job_board.remove_work")
        );
        this.removes.put(new Position(removeX, idY), () -> menu.sendRemoveRequest(index));
    }


    private void highlightAndTooltip(
            PoseStack poseStack,
            int mouseX,
            int mouseY,
            int iconX,
            int iconY,
            Component tooltipText
    ) {
        if (mouseX >= iconX && mouseY >= iconY && mouseX < iconX + 16 && mouseY < iconY + 17) {
            // transparent white square behind hovered item slot
            fill(poseStack, iconX, iconY + 1, iconX + 16, iconY + 17, 0x80FFFFFF);
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
            int iconX = x + 8 + j * 18;

            ItemStack[] matchingStacks = ing.getItems();
            if (matchingStacks.length > 0) {
                int curSeconds = (int) (System.currentTimeMillis() / 1000);
                ItemStack itemStack = matchingStacks[curSeconds % matchingStacks.length];
                this.itemRenderer.renderAndDecorateItem(itemStack, iconX, y + 1);
                if (mouseX >= iconX && mouseY >= y && mouseX < iconX + 16 && mouseY < y + 17) {
                    fill(
                            poseStack,
                            iconX,
                            y + 1,
                            iconX + 16,
                            y + 17,
                            0x80FFFFFF
                    ); // transparent white square behind hovered item slot
                    renderTooltip(
                            poseStack,
                            itemStack.getItem().getName(itemStack),
                            mouseX,
                            mouseY
                    ); // render hovered item's name as a tooltip
                }
                Slot element = new Slot(dummyInv, j, iconX, y + 1);
                element.set(itemStack);
                b.add(element);
            }
            j++;
        }
        return b.build();
    }

    private void renderPageNum(
            PoseStack poseStack,
            int x
    ) {
        // Draw page numbers
        fill(
                poseStack,
                x + borderPadding + buttonWidth,
                nextPage.y,
                x + backgroundWidth - borderPadding - buttonWidth,
                nextPage.y + buttonHeight,
                0x30000000
        );
        int totalPages = (int) Math.ceil((double) quests.size() / MAX_CARDS_PER_PAGE);
        String pageString = "Page " + (currentPage + 1) + " / " + totalPages;

        ImmutableRect2i pageArea = MathUtil.union(previousPage.getArea(), nextPage.getArea());
        ImmutableRect2i textArea = MathUtil.centerTextArea(pageArea, font, pageString);
        font.drawShadow(poseStack, pageString, textArea.getX(), textArea.getY(), 0xFFFFFFFF);
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

    private void nextPage() {
        int totalPages = (int) Math.ceil((double) quests.size() / MAX_CARDS_PER_PAGE);
        if (currentPage < totalPages - 1) {
            currentPage++;
        }
    }

    private void previousPage() {
        if (currentPage > 0) {
            currentPage--;
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
    public boolean mouseScrolled(
            double scrollX,
            double scrollY,
            double scrollDelta
    ) {
        final double x = MouseUtil.getX();
        final double y = MouseUtil.getY();
        if (isMouseOver(x, y)) {
            if (scrollDelta < 0) {
                this.nextPage();
                return true;
            } else if (scrollDelta > 0) {
                this.previousPage();
                return true;
            }
        }
        return super.mouseScrolled(scrollX, scrollY, scrollDelta);
    }

    @Override
    public boolean isMouseOver(
            double mouseX,
            double mouseY
    ) {
        return true;
    }

    public static QuestsScreen forTown(TownQuestsContainer container, Inventory playerInv, Component title) {
        return new QuestsScreen(container, playerInv, title, new SubUI() {
            @Override
            public void draw(PoseStack poseStack, int bgX, int bgY) {
            }

            @Override
            public void mouseClicked(
                    int bgX,
                    int bgY,
                    double x,
                    double y
            ) {
            }

            @Override
            public boolean renderTooltip(int bgX, int bgY, int mouseX, int mouseY, Consumer<String> o) {
                return false;
            }
        });
    }
}