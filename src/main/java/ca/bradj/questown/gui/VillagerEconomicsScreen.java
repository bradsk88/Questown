package ca.bradj.questown.gui;

import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.core.network.AddWorkFromUIMessage;
import ca.bradj.questown.core.network.EconomicsUpdate;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.mc.Compat;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.crafting.Ingredient;
import org.lwjgl.glfw.GLFW;

import java.util.List;

import static ca.bradj.questown.gui.PagedCardScreen.*;

public class VillagerEconomicsScreen extends AbstractPagedCardScreen<VillagerEconomicsMenu, ItemEconomicsData> {
    private final VillagerTabs tabs;

    public static EconomicsUpdate lastUpdate = new EconomicsUpdate(ImmutableList.of());

    IngredientRenderer ingredientRenderer = new IngredientRenderer();

    public VillagerEconomicsScreen(
            VillagerEconomicsMenu menu,
            Inventory playerInv,
            Component title
    ) {
        super(menu, playerInv, title);
        super.imageWidth = 256;
        super.imageHeight = 220;

        this.tabs = VillagerTabs.forMenu(menu);
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
    protected void renderScreenExtras(
            RenderContext ctx,
            int mouseX,
            int mouseY,
            float partialTicks
    ) {
        renderTooltips(ctx, mouseX, mouseY);
    }

    private void renderTooltips(
            RenderContext ctx,
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
                key -> this.showTooltip(Compat.translatable(key))
        )) {
            return;
        }

        for (Card<ItemEconomicsData> card : cards()) {
            int x = card.coords().leftX();
            int y = card.coords().topY();
            if (UtilClean.isCoordInBox(mouseX, mouseY, x, y, CARD_WIDTH, cardHeight)) {
                String key1 = "questown.menu.needs_in_period_1";
                String key2 = "questown.menu.needs_in_period_2";
                Ingredient ingr = Ingredients.fromString(card.data().ingredientKey());
                Component itemName = Ingredients.getName(ingr);
                int timesNeeded = card.data().timesNeeded();
                this.showTooltip(List.of(
                        Compat.componentText(Compat.translatable(key1, itemName, timesNeeded)),
                        Compat.componentText(Compat.translatable(key2, ItemEconomicsData.PERIOD_DAYS))
                ));
            }
        }
    }

    @Override
    protected List<Component> renderCardContent(
            RenderContext ctx,
            Card<ItemEconomicsData> card,
            int mouseX,
            int mouseY
    ) {
        renderDataAndIcons(ctx, card.coords(), card.data());
        return null;
    }

    private void renderDataAndIcons(
            RenderContext ctx,
            CardCoordinates coords,
            ItemEconomicsData data
    ) {
        int iconX = coords.leftX() + MED_PADDING;
        int iconY = coords.topY() + MED_PADDING;
        Ingredient item = Ingredients.fromString(data.ingredientKey());
        ingredientRenderer.render(ctx, item, iconX, iconY);
        int textX = iconX + ingredientRenderer.getSize() + SMALL_PADDING + SMALL_PADDING;
        int textY = coords.topYPadded();
        ctx.darkText(Ingredients.getName(item), textX, textY);
        String count = Integer.toString(data.timesNeeded());
        int countX = coords.rightXPadded() - font.width(count);
        ctx.darkText(Compat.literal(count), countX, textY);
    }

    @Override
    protected void renderBgContent(
            RenderContext ctx,
            float partialTicks,
            int mouseX,
            int mouseY
    ) {
        super.renderBgContent(ctx, partialTicks, mouseX, mouseY);
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - backgroundHeight()) / 2;
        this.tabs.draw(ctx, bgX, bgY);
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
        for (Card<ItemEconomicsData> card : cards()) {
            CardCoordinates c = card.coords();
            if (UtilClean.isCoordInBox(mouseX, mouseY, getIconX(c), getIconY(c), 16, 16)) {
                Ingredient ing = Ingredients.fromString(card.data().ingredientKey());
                AddWorkFromUIMessage.Action inquired = AddWorkFromUIMessage.Action.INQUIRED;
                AddWorkFromUIMessage msg = new AddWorkFromUIMessage(ing, menu.getFlagPos(), inquired);
                QuestownNetwork.sendToServer(msg);
                return true;
            }
        }
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight()) / 2;
        this.tabs.mouseClicked(x, y, mouseX, mouseY);
        return super.mouseClicked(mouseX, mouseY, p_97750_);
    }

    private static int getIconY(CardCoordinates coords) {
        return coords.topY() + MED_PADDING;
    }

    private static int getIconX(CardCoordinates coords) {
        return coords.leftX() + MED_PADDING;
    }

    @Override
    protected void setRenderColorForCard(ItemEconomicsData itemEconomicsData) {
    }
}