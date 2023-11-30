package ca.bradj.questown.gui;

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
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class WorkScreen extends AbstractContainerScreen<TownWorkContainer> {
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

    private final List<UIWork> work;
    private final DrawableNineSliceTexture background;
    private final DrawableNineSliceTexture cardBackground;
    private final GuiIconButtonSmall nextPage;
    private final GuiIconButtonSmall previousPage;
    private final GuiIconButtonSmall addMoreBtn;

    private final AddWorkScreen addWorkScreen;

    private int currentPage = 0;
    private Map<Position, Runnable> removes = new HashMap<>();

    public WorkScreen(
            TownWorkContainer container,
            Inventory playerInv,
            Component title
    ) {
        super(container, playerInv, title);
        super.imageWidth = 256;
        super.imageHeight = 220;

        this.work = ImmutableList.copyOf(container.getWork());
        Textures textures = Internal.getTextures();
        this.background = textures.getRecipeGuiBackground();
        this.cardBackground = textures.getRecipeBackground();

        IDrawableStatic plusIcon = textures.getRecipeTransfer();

        int btnX = backgroundWidth - (buttonWidth + borderPadding);

        IDrawableStatic arrowNext = textures.getArrowNext();
        IDrawableStatic arrowPrevious = textures.getArrowPrevious();
        this.nextPage = new GuiIconButtonSmall(
                0, 0, buttonWidth, buttonHeight, arrowNext, b -> nextPage()
        );
        this.previousPage = new GuiIconButtonSmall(
                0, 0, buttonWidth, buttonHeight, arrowPrevious, b -> previousPage()
        );

        addWorkScreen = new AddWorkScreen(menu.addWorkContainer, playerInv, title);
        this.addMoreBtn = new GuiIconButtonSmall(
                btnX, 0, buttonWidth, buttonHeight, plusIcon, b -> addMoreWork()
        );
    }

    @Override
    protected void init() {
        int y = (this.height - backgroundHeight) / 2;
        int pageStringY = y + borderPadding;
        int x = ((this.width - backgroundWidth) / 2);
        this.addMoreBtn.x = x + backgroundWidth - buttonWidth - borderPadding;
        this.addMoreBtn.y = pageStringY;
        this.addRenderableWidget(this.addMoreBtn);
        this.previousPage.x = x + borderPadding;
        this.previousPage.y = pageStringY;
        this.nextPage.x = x + backgroundWidth - (2 * buttonWidth) - borderPadding;
        this.nextPage.y = pageStringY;
        this.addRenderableWidget(this.previousPage);
        this.addRenderableWidget(this.nextPage);
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
    public void render(
            PoseStack poseStack,
            int mouseX,
            int mouseY,
            float partialTicks
    ) {
        this.renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTicks);

        int x = ((this.width - backgroundWidth) / 2);
        int y = (this.height - backgroundHeight) / 2;
        int pageStringY = y + PAGE_PADDING;

        renderPageNum(poseStack, x);

        y = pageStringY + PAGE_PADDING;

        int startIndex = currentPage * MAX_CARDS_PER_PAGE;
        int endIndex = Math.min(startIndex + MAX_CARDS_PER_PAGE, work.size());

        x = x + PAGE_PADDING;
        y = y + PAGE_PADDING;

        ImmutableList.Builder<Slot> b = ImmutableList.builder();
        for (int i = startIndex; i < endIndex; i++) {
            int row = i - startIndex;
            int cardY = y + row * (CARD_HEIGHT + CARD_PADDING);

            UIWork jobPosting = work.get(i);
            if (jobPosting == null) {
                continue;
            }
            this.cardBackground.draw(poseStack, x, cardY, CARD_WIDTH, CARD_HEIGHT);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

            int iconY = cardY + CARD_HEIGHT - 24;
            ImmutableList<Slot> slotz = renderJobCardIcons(poseStack, jobPosting, x, iconY, mouseX, mouseY);
            b.addAll(slotz);

            int idX = x + PAGE_PADDING;
            int idY = iconY - 10;
            this.font.draw(poseStack, new TranslatableComponent("job_board.default_name"), idX, idY, TEXT_COLOR);

            int removeX = idX + CARD_WIDTH - (PAGE_PADDING * 2) - buttonWidth;
            this.font.drawShadow(poseStack, new TextComponent("x"), removeX + borderPadding - 1, iconY + borderPadding - 1, 0xFFFFFF);
            highlightAndTooltip(poseStack, mouseX, mouseY, removeX, iconY, new TranslatableComponent("job_board.remove_work"));
            this.removes.put(new Position(removeX, iconY), () -> menu.sendRemoveRequest(jobPosting));
        }
        slots.clear();
        slots.addAll(b.build());

        // Render the page buttons
        this.addMoreBtn.render(poseStack, mouseX, mouseY, partialTicks);
        this.previousPage.render(poseStack, mouseX, mouseY, partialTicks);
        this.nextPage.render(poseStack, mouseX, mouseY, partialTicks);
    }

    private List<Slot> slots = new ArrayList<>();

    private ImmutableList<Slot> renderJobCardIcons(
            PoseStack poseStack,
            UIWork recipe,
            int x,
            int y,
            int mouseX,
            int mouseY
    ) {
        Inventory dummyInv = new Inventory(null);
        Ingredient ing = recipe.getResultWanted();
        ImmutableList.Builder<Slot> b = ImmutableList.builder();

        int iconX = x + 8;

        ItemStack[] matchingStacks = ing.getItems();
        if (matchingStacks.length > 0) {
            int curSeconds = (int) (System.currentTimeMillis() / 1000);
            ItemStack itemStack = matchingStacks[curSeconds % matchingStacks.length];
            this.itemRenderer.renderAndDecorateItem(itemStack, iconX, y + 1);

            highlightAndTooltip(poseStack, mouseX, mouseY, iconX, y, itemStack.getItem().getName(itemStack));
            Slot element = new Slot(dummyInv, 0, iconX, y + 1);
            element.set(itemStack);
            b.add(element);
        }
        return b.build();
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
        return super.mouseClicked(x, y, p_97750_);
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
        int totalPages = (int) Math.ceil((double) work.size() / MAX_CARDS_PER_PAGE);
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
        int totalPages = (int) Math.ceil((double) work.size() / MAX_CARDS_PER_PAGE);
        if (currentPage < totalPages - 1) {
            currentPage++;
        }
    }

    private void previousPage() {
        if (currentPage > 0) {
            currentPage--;
        }
    }

    private void addMoreWork() {
        this.minecraft.setScreen(addWorkScreen);
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
}