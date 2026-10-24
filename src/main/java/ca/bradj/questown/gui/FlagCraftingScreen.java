package ca.bradj.questown.gui;

import ca.bradj.questown.core.Coordinate;
import ca.bradj.questown.blocks.FlagPhase;
import ca.bradj.questown.blocks.TownFlagBlock;
import ca.bradj.questown.core.network.BeginTownRelocationMessage;
import ca.bradj.questown.core.network.CancelTownShutdownMessage;
import ca.bradj.questown.core.network.FlagCraftMessage;
import ca.bradj.questown.core.network.FlagCraftRecipes;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.JEI;
import com.google.common.collect.ImmutableList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
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
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class FlagCraftingScreen extends CompatContainerScreen<FlagCraftingMenu> {
    private static final int backgroundWidth = 176;
    private static final int MARGIN = 12;
    private static final int TEXT_WIDTH = backgroundWidth - 2 * MARGIN; // full row width for descriptions
    private static final int BLOCK_PAD = 6;                             // vertical gap between blocks
    private static final int ICON_ROW_HEIGHT = 24;                      // icon/[Craft] row footprint
    private static final int TOOLTIP_WIDTH = 200;
    private static final int CRAFT_BTN_X = 100;
    private static final int CRAFT_BTN_W = 60;
    private static final int BTN_HEIGHT = 20;

    private final FlagTabs tabs;
    private final JEI.NineNine background;
    private final BlockPos flagPos;
    private final boolean showMove;
    private final boolean shuttingDown;
    // One button and one row per FlagCraftRecipes.ALL entry, same index.
    private final List<Button> craftButtons = new ArrayList<>();

    // Flow layout, recomputed each init() from the actual wrapped-text heights so blocks can never
    // overlap regardless of string length / translation. All values are offsets from the panel's
    // top-left; the panel is centred using panelHeight.
    private int panelHeight = 166;
    private int titleY;
    private int[] rowY = new int[0];
    private int moveButtonY;
    private boolean moveArmed;

    public FlagCraftingScreen(
            FlagCraftingMenu menu,
            Inventory playerInv,
            Component title
    ) {
        super(menu, playerInv, title);
        super.imageWidth = 256;
        super.imageHeight = 220;
        // The crafting tab shares the flag UI's border treatment (the lighter recipe-GUI
        // nine-nine the quests tab uses); the darker recipe-background variant read as a
        // faded border mismatch (flag-crafting-ui-tooltips-and-border.md).
        this.background = JEI.getRecipeGuiBackground();
        this.tabs = FlagTabs.forMenu(menu, "tooltips.crafting");
        this.flagPos = menu.getFlagInfo().flagPos();
        // "Begin moving" recalls the townsfolk and relocates the town - meaningless with
        // zero villagers, so hide it until one has joined (same gate as the villagers tab).
        // While the shutdown ritual runs the same section offers "Cancel moving" (dev ruling
        // 2026-10-23), read from the synced PHASE blockstate so the menu payload is unchanged.
        this.shuttingDown = isShuttingDown(flagPos);
        this.showMove = menu.getFlagInfo().showVillagersTab() || shuttingDown;
    }

    private Component title() {
        return Compat.translatable("menu.flag_crafting.title");
    }

    private Component moveDesc() {
        if (shuttingDown) {
            return Compat.translatable("menu.flag_crafting.cancel_moving_desc");
        }
        return Compat.translatable("menu.flag_crafting.begin_moving_desc");
    }

    private static boolean isShuttingDown(BlockPos flagPos) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return false;
        }
        BlockState state = level.getBlockState(flagPos);
        return state.hasProperty(TownFlagBlock.PHASE) && state.getValue(TownFlagBlock.PHASE) == FlagPhase.SHUTTING_DOWN;
    }

    private int lineHeight() {
        return (int) (font.lineHeight * 1.5);
    }

    private int wrappedHeight(Component text) {
        return Compat.splitText(font, text, TEXT_WIDTH).size() * lineHeight();
    }

    /** Stack the blocks top-to-bottom, advancing past each block's real height. Sets panelHeight. */
    private void computeLayout() {
        int y = 8;
        titleY = y;
        y += wrappedHeight(title()) + BLOCK_PAD;

        rowY = new int[FlagCraftRecipes.ALL.size()];
        for (int i = 0; i < rowY.length; i++) {
            rowY[i] = y;
            y += ICON_ROW_HEIGHT + BLOCK_PAD;
        }

        // The "Begin moving" section (recall + relocate) only makes sense with villagers,
        // so collapse it entirely when none have joined.
        // The description is the button's hover tooltip, not a paragraph: with five recipe rows the
        // panel outgrew a 240-px-tall GUI (r17 screenshot 00:39: title and button cut off).
        if (showMove) {
            moveButtonY = y;
            y += BTN_HEIGHT + 8;
        }

        panelHeight = y;
    }

    @Override
    protected void init() {
        super.init();
        computeLayout();
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - panelHeight) / 2;

        craftButtons.clear();
        for (int i = 0; i < rowY.length; i++) {
            Button b = craftButton(bgX + CRAFT_BTN_X, bgY + rowY[i], i);
            craftButtons.add(b);
            this.addRenderableWidget(b);
        }
        if (showMove) {
            moveArmed = false;
            this.addRenderableWidget(Compat.button(
                    Compat.translatable(shuttingDown ? "menu.flag_crafting.cancel_moving" : "menu.flag_crafting.begin_moving"),
                    shuttingDown ? this::onCancelMoving : this::onBeginMoving,
                    bgX + MARGIN, bgY + moveButtonY, TEXT_WIDTH, BTN_HEIGHT
            ));
        }
    }

    // Packing the town recalls every townsfolk and has no in-game undo yet, and the button sits just
    // under the Craft column: the first click only arms it (docs/ui-principles.md 10).
    private void onBeginMoving(Button btn) {
        if (!moveArmed) {
            moveArmed = true;
            btn.setMessage(Compat.translatable("menu.flag_crafting.begin_moving.confirm"));
            return;
        }
        QuestownNetwork.sendToServer(new BeginTownRelocationMessage(flagPos));
        this.onClose();
    }

    // Cancelling is the safe direction (the townsfolk come back), so one click; the button is
    // deactivated so a double-click does not send twice (docs/ui-principles.md 10).
    private void onCancelMoving(Button btn) {
        btn.active = false;
        QuestownNetwork.sendToServer(new CancelTownShutdownMessage(flagPos));
        this.onClose();
    }

    private Button craftButton(int x, int y, int recipeIndex) {
        return Compat.button(
                Compat.translatable("menu.flag_crafting.craft"),
                btn -> {
                    // A fast double-click can fire the second send before the onClose from the
                    // first click lands (2026-10-12 playtest: "Created X" printed twice for one
                    // click - the server consumes input and grants the item per message, so the
                    // guard has to be here). The screen closes immediately; deactivate so a
                    // second press in the same frame is a no-op.
                    // 1.20.6: AbstractWidget lost setActive(); the active flag is a public field.
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

    // Recipes consume their input from the player's inventory server-side (FlagCraftMessage), so
    // grey the button out when the local player has none — clicking it would be a no-op.
    private void updateCraftButtonStates() {
        for (int i = 0; i < craftButtons.size(); i++) {
            craftButtons.get(i).active = countMatching(FlagCraftRecipes.ALL.get(i).input().get()) > 0;
        }
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

        for (int i = 0; i < rowY.length; i++) {
            FlagCraftRecipes r = FlagCraftRecipes.ALL.get(i);
            renderRecipeRow(ctx, bgX, bgY + rowY[i], r.icon().get(), new ItemStack(r.output().get()), mouseX, mouseY);
        }

        if (showMove && isOverMoveButton(mouseX, mouseY, bgX, bgY)) {
            showTooltip(Compat.splitText(font, moveDesc(), TOOLTIP_WIDTH));
        }

        for (int i = 0; i < craftButtons.size(); i++) {
            if (craftButtons.get(i).isHovered()) {
                FlagCraftRecipes r = FlagCraftRecipes.ALL.get(i);
                renderCraftTooltip(Compat.translatable(r.descKey()), r.input().get(), mouseX, mouseY);
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
        // 1.20.6: item tooltips come free only for container slots - the rendered icons
        // need their own (flag-crafting-ui-tooltips-and-border.md): what the input is, what
        // you get.
        if (mouseInside(mouseX, mouseY, x + MARGIN, iconY + 2)) {
            showTooltip(Compat.getHoverName(input));
        } else if (mouseInside(mouseX, mouseY, x + 50, iconY + 2)) {
            showTooltip(Compat.getHoverName(output));
        }
    }

    private boolean isOverMoveButton(int mouseX, int mouseY, int bgX, int bgY) {
        int x = bgX + MARGIN;
        int y = bgY + moveButtonY;
        return mouseX >= x && mouseX < x + TEXT_WIDTH && mouseY >= y && mouseY < y + BTN_HEIGHT;
    }

    /** The 16x16 icon box at (x, y). */
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
