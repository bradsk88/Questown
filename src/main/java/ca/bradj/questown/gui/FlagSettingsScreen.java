package ca.bradj.questown.gui;

import ca.bradj.questown.blocks.FlagPhase;
import ca.bradj.questown.blocks.TownFlagBlock;
import ca.bradj.questown.core.Coordinate;
import ca.bradj.questown.core.network.BeginTownRelocationMessage;
import ca.bradj.questown.core.network.CancelTownShutdownMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.mc.Compat;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** The flag's Settings tab: "Begin moving this town" and "Cancel moving". */
public class FlagSettingsScreen extends CompatContainerScreen<FlagSettingsMenu> {
    private static final int backgroundWidth = 176;
    private static final int MARGIN = 12;
    private static final int TEXT_WIDTH = backgroundWidth - 2 * MARGIN;
    private static final int BLOCK_PAD = 6;
    private static final int BTN_HEIGHT = 20;
    private static final int TOOLTIP_WIDTH = 200;

    private final FlagTabs tabs;
    private final NineSlice background;
    private final BlockPos flagPos;
    private final boolean showMove;
    private final boolean shuttingDown;

    private int panelHeight;
    private int titleY;
    private int moveButtonY;
    private int noSettingsY;
    private boolean moveArmed;

    public FlagSettingsScreen(
            FlagSettingsMenu menu,
            Inventory playerInv,
            Component title
    ) {
        super(menu, playerInv, title);
        super.imageWidth = 256;
        super.imageHeight = 220;
        this.background = GuiArt.getRecipeGuiBackground();
        this.tabs = FlagTabs.forMenu(menu, "tooltips.settings");
        this.flagPos = menu.getFlagInfo().flagPos();
        // Moving needs at least one villager. While the town is packing, offer to cancel.
        this.shuttingDown = isShuttingDown(flagPos);
        this.showMove = menu.getFlagInfo().showVillagersTab() || shuttingDown;
    }

    private Component title() {
        return Compat.translatable("menu.flag_settings.title");
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

    private int wrappedHeight(Component text) {
        return Compat.splitText(font, text, TEXT_WIDTH).size() * (int) (font.lineHeight * 1.5);
    }

    private void computeLayout() {
        int y = 8;
        titleY = y;
        y += wrappedHeight(title()) + BLOCK_PAD;
        if (showMove) {
            moveButtonY = y;
            y += BTN_HEIGHT + 8;
        } else {
            noSettingsY = y;
            y += wrappedHeight(Compat.translatable("menu.flag_settings.none_yet")) + 8;
        }
        panelHeight = y;
    }

    @Override
    protected void init() {
        super.init();
        computeLayout();
        if (!showMove) {
            return;
        }
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - panelHeight) / 2;
        moveArmed = false;
        this.addRenderableWidget(Compat.button(
                Compat.translatable(shuttingDown ? "menu.flag_crafting.cancel_moving" : "menu.flag_crafting.begin_moving"),
                shuttingDown ? this::onCancelMoving : this::onBeginMoving,
                bgX + MARGIN, bgY + moveButtonY, TEXT_WIDTH, BTN_HEIGHT
        ));
    }

    // Moving the town can't be undone, so the first click only asks.
    private void onBeginMoving(Button btn) {
        if (!moveArmed) {
            moveArmed = true;
            btn.setMessage(Compat.translatable("menu.flag_crafting.begin_moving.confirm"));
            return;
        }
        QuestownNetwork.sendToServer(new BeginTownRelocationMessage(flagPos));
        this.onClose();
    }

    // Cancelling is safe, so one click.
    private void onCancelMoving(Button btn) {
        btn.active = false;
        QuestownNetwork.sendToServer(new CancelTownShutdownMessage(flagPos));
        this.onClose();
    }

    @Override
    protected void renderScreenContent(RenderContext ctx, int mouseX, int mouseY, float partialTicks) {
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - panelHeight) / 2;
        ctx.darkTextWrap(new Coordinate(bgX + MARGIN, bgY + titleY), TEXT_WIDTH, title());
        if (!showMove) {
            ctx.darkTextWrap(new Coordinate(bgX + MARGIN, bgY + noSettingsY), TEXT_WIDTH,
                    Compat.translatable("menu.flag_settings.none_yet"));
            return;
        }
        int x = bgX + MARGIN;
        int y = bgY + moveButtonY;
        if (mouseX >= x && mouseX < x + TEXT_WIDTH && mouseY >= y && mouseY < y + BTN_HEIGHT) {
            showTooltip(Compat.splitText(font, moveDesc(), TOOLTIP_WIDTH));
        }
    }

    @Override
    protected void renderBgContent(RenderContext ctx, float partialTicks, int mouseX, int mouseY) {
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - panelHeight) / 2;
        this.background.draw(ctx, bgX, bgY, backgroundWidth, panelHeight);
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
