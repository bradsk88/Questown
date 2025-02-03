package ca.bradj.questown.gui;

import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.core.network.EconomicsUpdate;
import ca.bradj.questown.mc.Compat;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.crafting.Ingredient;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Optional;

import static ca.bradj.questown.gui.PagedCardScreen.*;

public class TownEconomicsScreen extends AbstractPagedCardScreen<TownEconomicsMenu, ItemEconomicsData> {
    private final FlagTabs tabs;

    public static EconomicsUpdate lastUpdate = new EconomicsUpdate(ImmutableList.of());

    IngredientRenderer ingredientRenderer = new IngredientRenderer();

    public TownEconomicsScreen(
            TownEconomicsMenu menu,
            Inventory playerInv,
            Component title
    ) {
        super(menu, playerInv, title);
        super.imageWidth = 256;
        super.imageHeight = 220;

        this.tabs = FlagTabs.forMenu(menu);
    }

    @Override
    protected ImmutableList<ItemEconomicsData> cardsData() {
        return lastUpdate.data();
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
        int bgY = (this.height - backgroundHeight()) / 2;
        if (this.tabs.renderTooltip(
                bgX,
                bgY,
                mouseX,
                mouseY,
                key -> super.renderTooltip(stack, Compat.translatable(key), mouseX, mouseY)
        )) {
            return;
        }

        for (Card<ItemEconomicsData> card : cards()) {
            int x = card.coords().leftX();
            int y = card.coords().topY();
            if (UtilClean.coordInBox(mouseX, mouseY, x, y, CARD_WIDTH, cardHeight)) {
                String key1 = "questown.menu.needs_in_period_1";
                String key2 = "questown.menu.needs_in_period_2";
                Ingredient ingr = Ingredients.fromString(card.data().ingredientKey());
                Component itemName = Ingredients.getName(ingr);
                int timesNeeded = card.data().timesNeeded();
                List<Component> es = ImmutableList.of(
                        Compat.translatable(key1, itemName, timesNeeded),
                        Compat.translatable(key2, ItemEconomicsData.PERIOD_DAYS)
                );
                super.renderTooltip(stack, es, Optional.empty(), mouseX, mouseY);
            }
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
    }

    @Override
    protected void renderCardContent(
            PoseStack poseStack,
            PagedCardScreen.Card<ItemEconomicsData> card,
            int mouseX,
            int mouseY
    ) {
        renderDataAndIcons(poseStack, card.coords(), card.data());
    }

    private void renderDataAndIcons(
            PoseStack poseStack,
            CardCoordinates coords,
            ItemEconomicsData data
    ) {
        int iconX = coords.leftX() + MED_PADDING;
        int iconY = coords.topY() + MED_PADDING;
        Ingredient item = Ingredients.fromString(data.ingredientKey());
        ingredientRenderer.render(itemRenderer, item, iconX, iconY);
        int textX = iconX + ingredientRenderer.getSize() + SMALL_PADDING + SMALL_PADDING;
        int textY = coords.topYPadded();
        Compat.drawDarkText(font, poseStack, Ingredients.getName(item), textX, textY);
        String count = Integer.toString(data.timesNeeded());
        int countX = coords.rightXPadded() - font.width(count);
        Compat.drawDarkText(font, poseStack, Compat.literal(count), countX, textY);
    }

    protected void renderBg(
            PoseStack stack,
            float partialTicks,
            int mouseX,
            int mouseY
    ) {
        super.renderBg(stack, partialTicks, mouseX, mouseY);
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - backgroundHeight()) / 2;
        this.tabs.draw(new RenderContext(itemRenderer, stack), bgX, bgY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public List<Rect2i> getExtraAreas() {
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight()) / 2;
        return ImmutableList.of(new Rect2i(x, y, backgroundWidth, backgroundHeight()));
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
        int y = (this.height - backgroundHeight()) / 2;
        this.tabs.mouseClicked(x, y, mouseX, mouseY);
        return super.mouseClicked(mouseX, mouseY, p_97750_);
    }

    @Override
    protected void setRenderColorForCard(ItemEconomicsData itemEconomicsData) {
    }
}