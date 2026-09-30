package ca.bradj.questown.gui;

import ca.bradj.questown.core.Coordinate;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.core.network.EconomicsUpdate;
import ca.bradj.questown.core.network.GiveBOPMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.JEI;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class TownBlockofProgressScreen extends CompatContainerScreen<TownBlockofProgressMenu> {
    private static final int backgroundWidth = 176;
    private static final int backgroundHeight = 166;
    private final FlagTabs tabs;

    public static EconomicsUpdate lastUpdate = new EconomicsUpdate(ImmutableList.of());
    private final JEI.NineNine background;
    private Button unlockButton;

    IngredientRenderer ingredientRenderer = new IngredientRenderer();
    private int buttonY;
    private int buttonX;

    public TownBlockofProgressScreen(
            TownBlockofProgressMenu menu,
            Inventory playerInv,
            Component title
    ) {
        super(menu, playerInv, title);
        super.imageWidth = 256;
        super.imageHeight = 220;

        this.background = JEI.getRecipeBackground();
        this.tabs = FlagTabs.forMenu(menu);
    }

    @Override
    protected void init() {
        super.init();
        this.unlockButton = this.addRenderableWidget(Compat.button(
                Compat.translatable("menu.block_of_progress.take"), (p_96776_) -> {
            QuestownNetwork.sendToServer(new GiveBOPMessage(menu.getFlagInfo().flagPos()));
            Minecraft.getInstance().setScreen(null);
        }, 0, 0, backgroundWidth - 8, 20));
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
    protected void renderPrologue(RenderContext ctx, int mouseX, int mouseY, float partialTicks) {
        unlockButton.setX(buttonX + 4);
        unlockButton.setY(buttonY);
    }

    @Override
    protected void renderScreenContent(RenderContext ctx, int mouseX, int mouseY, float partialTicks) {
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - backgroundHeight) / 2;
        bgY += 8;
        int ct = Math.min(menu.blocksOfProgressCount, 7);
        int iconsX = (width / 2) - (8 * ct);
        for (int i = 0; i < ct; i++) {
            ItemStack defaultInstance = ItemsInit.BLOCK_OF_PROGRESS.get().getDefaultInstance();
            RenderUtil.renderItemScaled(Compat.itemRenderer(), 3, defaultInstance, iconsX + (16 * i), bgY + 2);
        }
        bgY += 24;
        renderText(ctx, bgX, bgY);
    }

    private void renderText(
            RenderContext ctx,
            int bgX,
            int bgY
    ) {
        Coordinate topLeft = new Coordinate(bgX + 12, bgY);
        int tWidth = backgroundWidth - 16;
        MutableComponent text = Compat.translatable(
                "menu.block_of_progress.town_has",
                menu.blocksOfProgressCount
        );
        if (menu.blocksOfProgressCount == 1) {
            text = Compat.translatable(
                    "menu.block_of_progress.town_has_1",
                    menu.blocksOfProgressCount
            );
        }
        bgY += ctx.darkTextWrap(
                topLeft.withY(bgY),
                tWidth,
                text
        );
        bgY += 8;
        // A full flag silently loses further BOPs, so warn the player in red (captured by gui-lint).
        if (menu.getFlagInfo().bopFull()) {
            bgY += ctx.redTextWrap(
                    topLeft.withY(bgY),
                    tWidth,
                    Compat.translatable("menu.block_of_progress.full")
            );
            bgY += 8;
        }
        bgY += ctx.darkTextWrap(
                topLeft.withY(bgY),
                tWidth,
                Compat.translatable("menu.block_of_progress.many_uses")
        );
        this.buttonY = bgY;
        this.buttonX = bgX;
    }

    @Override
    protected void renderBgContent(RenderContext ctx, float partialTicks, int mouseX, int mouseY) {
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - backgroundHeight) / 2;
        ctx.drawNineNine(this.background, bgX, bgY, backgroundWidth, backgroundHeight);
        this.tabs.draw(ctx, bgX, bgY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
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

    @Override
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int p_97750_
    ) {
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        this.tabs.mouseClicked(x, y, mouseX, mouseY);
        return super.mouseClicked(mouseX, mouseY, p_97750_);
    }
}
