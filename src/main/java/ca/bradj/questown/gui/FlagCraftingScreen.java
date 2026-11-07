package ca.bradj.questown.gui;

import ca.bradj.questown.core.Coordinate;
import ca.bradj.questown.core.network.FlagCraftMessage;
import ca.bradj.questown.core.network.FlagCraftRecipes;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.mc.Compat;
import mezz.jei.gui.elements.GuiIconButtonSmall;
import ca.bradj.questown.mc.JEI;
import com.google.common.collect.ImmutableList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;

import static ca.bradj.questown.gui.PagedCardScreen.buttonHeight;
import static ca.bradj.questown.gui.PagedCardScreen.buttonWidth;

public class FlagCraftingScreen extends CompatContainerScreen<FlagCraftingMenu> {
    private static final int backgroundWidth = 176;
    private static final int MARGIN = 12;
    private static final int TEXT_WIDTH = backgroundWidth - 2 * MARGIN;
    private static final int BLOCK_PAD = 6;
    private static final int ICON_ROW_HEIGHT = 24;
    private static final int TOOLTIP_WIDTH = 200;
    private static final int CRAFT_BTN_X = 100;
    private static final int CRAFT_BTN_W = 60;
    private static final int BTN_HEIGHT = 20;
    static final int ROWS_PER_PAGE = 5;

    private final FlagTabs tabs;
    private final JEI.NineNine background;
    private final BlockPos flagPos;
    // One button per visible recipe; visibleRecipes maps a row to its recipe index.
    private final List<Button> craftButtons = new ArrayList<>();
    private List<Integer> visibleRecipes = new ArrayList<>();
    private int page = 0;
    private int pageCount = 1;
    private int navY;
    private final GuiIconButtonSmall previousPage = JEI.guiIconButtonSmall(
            0, 0, buttonWidth, buttonHeight, JEI.getArrowPrevious(), b -> turnPage(-1));
    private final GuiIconButtonSmall nextPage = JEI.guiIconButtonSmall(
            0, 0, buttonWidth, buttonHeight, JEI.getArrowNext(), b -> turnPage(1));
    // A Block of Progress recipe asks for a second click to confirm.
    private int armedRecipe = -1;

    // Laid out from the real wrapped-text heights, so rows never overlap.
    private int panelHeight = 166;
    private int titleY;
    private int[] rowY = new int[0];

    public FlagCraftingScreen(
            FlagCraftingMenu menu,
            Inventory playerInv,
            Component title
    ) {
        super(menu, playerInv, title);
        super.imageWidth = 256;
        super.imageHeight = 220;
        this.background = JEI.getRecipeGuiBackground();
        this.tabs = FlagTabs.forMenu(menu, "tooltips.crafting");
        this.flagPos = menu.getFlagInfo().flagPos();
    }

    private Component title() {
        return Compat.translatable("menu.flag_crafting.title");
    }

    private int lineHeight() {
        return (int) (font.lineHeight * 1.5);
    }

    private int wrappedHeight(Component text) {
        return Compat.splitText(font, text, TEXT_WIDTH).size() * lineHeight();
    }

    /** Stacks the rows top to bottom by their real heights. */
    private void computeLayout() {
        int y = 8;
        titleY = y;
        y += wrappedHeight(title()) + BLOCK_PAD;

        List<Integer> unlocked = new ArrayList<>();
        for (int i = 0; i < FlagCraftRecipes.ALL.size(); i++) {
            if (FlagCraftGates.visible(FlagCraftRecipes.ALL.get(i).unlockedByQuest(), menu.getPostedQuests())) {
                unlocked.add(i);
            }
        }
        pageCount = Math.max(1, Pages.count(unlocked.size(), ROWS_PER_PAGE));
        page = Math.min(page, pageCount - 1);
        if (pageCount > 1) {
            navY = y;
            y += buttonHeight + BLOCK_PAD;
        }
        int from = page * ROWS_PER_PAGE;
        visibleRecipes = new ArrayList<>(unlocked.subList(from, Math.min(unlocked.size(), from + ROWS_PER_PAGE)));
        rowY = new int[visibleRecipes.size()];
        for (int i = 0; i < rowY.length; i++) {
            rowY[i] = y;
            y += ICON_ROW_HEIGHT + BLOCK_PAD;
        }

        panelHeight = y;
    }

    @Override
    protected void init() {
        super.init();
        computeLayout();
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - panelHeight) / 2;

        if (pageCount > 1) {
            previousPage.x = bgX + MARGIN;
            previousPage.y = bgY + navY;
            nextPage.x = bgX + backgroundWidth - MARGIN - buttonWidth;
            nextPage.y = bgY + navY;
            this.addRenderableWidget(previousPage);
            this.addRenderableWidget(nextPage);
        }
        armedRecipe = -1;
        craftButtons.clear();
        for (int i = 0; i < rowY.length; i++) {
            Button b = craftButton(bgX + CRAFT_BTN_X, bgY + rowY[i], visibleRecipes.get(i));
            craftButtons.add(b);
            this.addRenderableWidget(b);
        }
    }

    private void turnPage(int delta) {
        page = Math.max(0, Math.min(pageCount - 1, page + delta));
        rebuildWidgets();
    }

    private Button craftButton(int x, int y, int recipeIndex) {
        FlagCraftRecipes recipe = FlagCraftRecipes.ALL.get(recipeIndex);
        return Compat.button(
                Compat.translatable("menu.flag_crafting.craft"),
                btn -> {
                    if (recipe.costsBops() && armedRecipe != recipeIndex) {
                        armedRecipe = recipeIndex;
                        btn.setMessage(Compat.translatable("menu.flag_crafting.craft_bops_confirm", recipe.bopCost()));
                        return;
                    }
                    // Off after one click, so a double-click does not craft twice.
                    btn.active = false;
                    QuestownNetwork.sendToServer(new FlagCraftMessage(flagPos, recipeIndex));
                    this.onClose();
                },
                x, y, CRAFT_BTN_W, BTN_HEIGHT
        );
    }

    private void renderCraftTooltip(Component desc, Ingredient required, int mouseX, int mouseY) {
        int count = countMatching(required);
        List<FormattedCharSequence> lines = new ArrayList<>(Compat.splitText(font, desc, TOOLTIP_WIDTH));
        Style style = Style.EMPTY.applyFormat(count == 0 ? ChatFormatting.RED : ChatFormatting.GRAY);
        lines.add(Compat.componentText(Compat.translatableStyled("menu.flag_crafting.in_inventory", style, count)));
        this.showTooltip(lines);
    }

    // Greyed out when the player has none of the input.
    private void updateCraftButtonStates() {
        for (int i = 0; i < craftButtons.size(); i++) {
            FlagCraftRecipes r = FlagCraftRecipes.ALL.get(visibleRecipes.get(i));
            craftButtons.get(i).active = r.costsBops()
                    ? menu.getFlagBops() >= r.bopCost()
                    : countMatching(r.input().get()) > 0;
        }
    }

    // Counts the flag's Blocks of Progress, not the player's.
    private void renderBopCraftTooltip(Component desc, int cost, int mouseX, int mouseY) {
        int have = menu.getFlagBops();
        List<FormattedCharSequence> lines = new ArrayList<>(Compat.splitText(font, desc, TOOLTIP_WIDTH));
        Style style = Style.EMPTY.applyFormat(have < cost ? ChatFormatting.RED : ChatFormatting.GRAY);
        lines.add(Compat.componentText(Compat.translatableStyled("menu.flag_crafting.on_flag", style, have, cost)));
        this.showTooltip(lines);
    }

    private int countMatching(Ingredient ingredient) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return 0;
        }
        Inventory inv = player.getInventory();
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (ingredient.test(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    @Override
    protected void renderPrologue(RenderContext ctx, int mouseX, int mouseY, float partialTicks) {
        updateCraftButtonStates();
    }

    @Override
    protected void renderScreenContent(RenderContext ctx, int mouseX, int mouseY, float partialTicks) {
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - panelHeight) / 2;

        ctx.darkTextWrap(new Coordinate(bgX + MARGIN, bgY + titleY), TEXT_WIDTH, title());
        if (pageCount > 1) {
            PagedCardScreen.drawSelectorStrip(ctx, previousPage, nextPage, nextPage.x + buttonWidth,
                    "Page " + (page + 1) + " / " + pageCount);
        }

        for (int i = 0; i < rowY.length; i++) {
            FlagCraftRecipes r = FlagCraftRecipes.ALL.get(visibleRecipes.get(i));
            renderRecipeRow(ctx, bgX, bgY + rowY[i], r.icon().get(), new ItemStack(r.output().get()), mouseX, mouseY);
        }

        for (int i = 0; i < craftButtons.size(); i++) {
            if (craftButtons.get(i).isHoveredOrFocused()) {
                FlagCraftRecipes r = FlagCraftRecipes.ALL.get(visibleRecipes.get(i));
                if (r.costsBops()) {
                    renderBopCraftTooltip(Compat.translatable(r.descKey()), r.bopCost(), mouseX, mouseY);
                } else {
                    renderCraftTooltip(Compat.translatable(r.descKey()), r.input().get(), mouseX, mouseY);
                }
            }
        }
    }

    private void renderRecipeRow(
            RenderContext ctx,
            int x,
            int iconY,
            ItemStack input,
            ItemStack output,
            int mouseX,
            int mouseY
    ) {
        ctx.renderItemAndDecorations(input, x + MARGIN, iconY + 2);
        ctx.darkText("→", x + 36, iconY + 6);
        ctx.renderItemAndDecorations(output, x + 50, iconY + 2);
        // The icons are not slots, so they need their own tooltips.
        if (mouseInside(mouseX, mouseY, x + MARGIN, iconY + 2)) {
            showTooltip(Compat.getHoverName(input));
        } else if (mouseInside(mouseX, mouseY, x + 50, iconY + 2)) {
            showTooltip(Compat.getHoverName(output));
        }
    }

    private static boolean mouseInside(int mouseX, int mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16;
    }

    @Override
    protected void renderBgContent(RenderContext ctx, float partialTicks, int mouseX, int mouseY) {
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - panelHeight) / 2;
        ctx.drawNineNine(this.background, bgX, bgY, backgroundWidth, panelHeight);
        this.tabs.draw(ctx, bgX, bgY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public List<Rect2i> getExtraAreas() {
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - panelHeight) / 2;
        return ImmutableList.of(new Rect2i(x, y, backgroundWidth, panelHeight));
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - panelHeight) / 2;
        this.tabs.mouseClicked(x, y, mouseX, mouseY);
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
