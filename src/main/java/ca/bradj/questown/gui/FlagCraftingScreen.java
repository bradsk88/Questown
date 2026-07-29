package ca.bradj.questown.gui;

import ca.bradj.questown.core.Coordinate;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.core.network.BeginTownRelocationMessage;
import ca.bradj.questown.core.network.FlagCraftMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.JEI;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class FlagCraftingScreen extends AbstractContainerScreen<FlagCraftingMenu> {
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
    private Button craftWandButton;
    private Button craftMatButton;

    // Flow layout, recomputed each init() from the actual wrapped-text heights so blocks can never
    // overlap regardless of string length / translation. All values are offsets from the panel's
    // top-left; the panel is centred using panelHeight.
    private int panelHeight = 166;
    private int titleY;
    private int row1Y;
    private int row2Y;
    private int moveDescY;
    private int moveButtonY;

    public FlagCraftingScreen(
            FlagCraftingMenu menu,
            Inventory playerInv,
            Component title
    ) {
        super(menu, playerInv, title);
        super.imageWidth = 256;
        super.imageHeight = 220;
        this.background = JEI.getRecipeBackground();
        this.tabs = FlagTabs.forMenu(menu);
        this.flagPos = menu.getFlagInfo().flagPos();
    }

    private Component title() {
        return Compat.translatable("menu.flag_crafting.title");
    }

    private Component wandDesc() {
        return Compat.translatable("menu.flag_crafting.wand_desc");
    }

    private Component matDesc() {
        return Compat.translatable("menu.flag_crafting.mat_desc");
    }

    private Component moveDesc() {
        return Compat.translatable("menu.flag_crafting.begin_moving_desc");
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

        row1Y = y;
        y += ICON_ROW_HEIGHT + BLOCK_PAD;
        row2Y = y;
        y += ICON_ROW_HEIGHT + BLOCK_PAD;

        moveDescY = y;
        y += wrappedHeight(moveDesc()) + BLOCK_PAD;
        moveButtonY = y;
        y += BTN_HEIGHT + 8;

        panelHeight = y;
    }

    @Override
    protected void init() {
        super.init();
        computeLayout();
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - panelHeight) / 2;

        this.craftWandButton = craftButton(bgX + CRAFT_BTN_X, bgY + row1Y, 0, Items.STICK, wandDesc());
        this.craftMatButton = craftButton(bgX + CRAFT_BTN_X, bgY + row2Y, 1, Items.OAK_PRESSURE_PLATE, matDesc());
        this.addRenderableWidget(this.craftWandButton);
        this.addRenderableWidget(this.craftMatButton);
        this.addRenderableWidget(new Button(
                bgX + MARGIN, bgY + moveButtonY, TEXT_WIDTH, BTN_HEIGHT,
                Compat.translatable("menu.flag_crafting.begin_moving"),
                btn -> {
                    QuestownNetwork.CHANNEL.sendToServer(new BeginTownRelocationMessage(flagPos));
                    this.onClose();
                }
        ));
    }

    private Button craftButton(int x, int y, int recipeIndex, Item required, Component desc) {
        return new Button(
                x, y, CRAFT_BTN_W, BTN_HEIGHT,
                Compat.translatable("menu.flag_crafting.craft"),
                btn -> {
                    QuestownNetwork.CHANNEL.sendToServer(new FlagCraftMessage(flagPos, recipeIndex));
                    this.onClose();
                },
                (btn, stack, mouseX, mouseY) -> renderCraftTooltip(stack, desc, required, mouseX, mouseY)
        );
    }

    private void renderCraftTooltip(PoseStack stack, Component desc, Item required, int mouseX, int mouseY) {
        int count = countInInventory(required);
        List<FormattedCharSequence> lines = new ArrayList<>(Compat.splitText(font, desc, TOOLTIP_WIDTH));
        Style style = Style.EMPTY.applyFormat(count == 0 ? ChatFormatting.RED : ChatFormatting.GRAY);
        lines.add(Compat.translatableStyled("menu.flag_crafting.in_inventory", style, count).getVisualOrderText());
        this.renderTooltip(stack, lines, mouseX, mouseY);
    }

    // Recipes consume their input from the player's inventory server-side (FlagCraftMessage), so
    // grey the button out when the local player has none — clicking it would be a no-op.
    private void updateCraftButtonStates() {
        if (this.craftWandButton != null) {
            this.craftWandButton.active = countInInventory(Items.STICK) > 0;
        }
        if (this.craftMatButton != null) {
            this.craftMatButton.active = countInInventory(Items.OAK_PRESSURE_PLATE) > 0;
        }
    }

    private int countInInventory(Item item) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return 0;
        }
        Inventory inv = player.getInventory();
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    @Override
    protected void renderLabels(PoseStack stack, int mouseX, int mouseY) {
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        updateCraftButtonStates();
        this.renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTicks);

        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - panelHeight) / 2;

        Compat.drawDarkTextWrap(font, poseStack, new Coordinate(bgX + MARGIN, bgY + titleY), TEXT_WIDTH, title());

        renderRecipeRow(poseStack, bgX, bgY + row1Y, Items.STICK.getDefaultInstance(), new ItemStack(ItemsInit.TOWN_WAND.get()));
        renderRecipeRow(poseStack, bgX, bgY + row2Y, Items.OAK_PRESSURE_PLATE.getDefaultInstance(), new ItemStack(ItemsInit.WELCOME_MAT_BLOCK.get()));

        Compat.drawDarkTextWrap(font, poseStack, new Coordinate(bgX + MARGIN, bgY + moveDescY), TEXT_WIDTH, moveDesc());
    }

    private void renderRecipeRow(PoseStack poseStack, int x, int iconY, ItemStack input, ItemStack output) {
        itemRenderer.renderAndDecorateItem(input, x + MARGIN, iconY + 2);
        font.draw(poseStack, "→", x + 36, iconY + 6, 0x404040);
        itemRenderer.renderAndDecorateItem(output, x + 50, iconY + 2);
    }

    @Override
    protected void renderBg(PoseStack stack, float partialTicks, int mouseX, int mouseY) {
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - panelHeight) / 2;
        this.background.draw(stack, bgX, bgY, backgroundWidth, panelHeight);
        this.tabs.draw(new RenderContext(itemRenderer, stack), bgX, bgY);
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
