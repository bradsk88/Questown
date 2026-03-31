package ca.bradj.questown.gui;

import ca.bradj.questown.core.Coordinate;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.core.network.FlagCraftMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.JEI;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public class FlagCraftingScreen extends AbstractContainerScreen<FlagCraftingMenu> {
    private static final int backgroundWidth = 176;
    private static final int backgroundHeight = 166;
    private final FlagTabs tabs;
    private final JEI.NineNine background;
    private final BlockPos flagPos;

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

    @Override
    protected void init() {
        super.init();
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - backgroundHeight) / 2;

        this.addRenderableWidget(new Button(
                bgX + 100, bgY + 30, 60, 20,
                Compat.translatable("menu.flag_crafting.craft"),
                btn -> QuestownNetwork.CHANNEL.sendToServer(new FlagCraftMessage(flagPos, 0))
        ));
        this.addRenderableWidget(new Button(
                bgX + 100, bgY + 70, 60, 20,
                Compat.translatable("menu.flag_crafting.craft"),
                btn -> QuestownNetwork.CHANNEL.sendToServer(new FlagCraftMessage(flagPos, 1))
        ));
    }

    @Override
    protected void renderLabels(PoseStack stack, int mouseX, int mouseY) {
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTicks);

        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - backgroundHeight) / 2;

        Coordinate topLeft = new Coordinate(bgX + 12, bgY + 10);
        int tWidth = backgroundWidth - 16;
        Compat.drawDarkTextWrap(font, poseStack, topLeft, tWidth, Compat.translatable("menu.flag_crafting.title"));

        renderRecipeRow(poseStack, bgX, bgY + 28, Items.STICK.getDefaultInstance(),
                new ItemStack(ItemsInit.TOWN_WAND.get()), "menu.flag_crafting.wand_desc");
        renderRecipeRow(poseStack, bgX, bgY + 68, Items.OAK_PRESSURE_PLATE.getDefaultInstance(),
                new ItemStack(ItemsInit.WELCOME_MAT_BLOCK.get()), "menu.flag_crafting.mat_desc");
    }

    private void renderRecipeRow(PoseStack poseStack, int x, int y, ItemStack input, ItemStack output, String descKey) {
        itemRenderer.renderAndDecorateItem(input, x + 12, y + 2);
        font.draw(poseStack, "\u2192", x + 36, y + 6, 0x404040);
        itemRenderer.renderAndDecorateItem(output, x + 50, y + 2);
        Compat.drawDarkTextWrap(font, poseStack, new Coordinate(x + 12, y + 22), backgroundWidth - 120,
                Compat.translatable(descKey));
    }

    @Override
    protected void renderBg(PoseStack stack, float partialTicks, int mouseX, int mouseY) {
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - backgroundHeight) / 2;
        this.background.draw(stack, bgX, bgY, backgroundWidth, backgroundHeight);
        this.tabs.draw(new RenderContext(itemRenderer, stack), bgX, bgY);
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
    public boolean isMouseOver(double mouseX, double mouseY) {
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        this.tabs.mouseClicked(x, y, mouseX, mouseY);
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
