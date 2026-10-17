package ca.bradj.questown.gui;

import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.core.network.AddWorkFromUIMessage;
import ca.bradj.questown.core.network.ClaimFreeFoodMessage;
import ca.bradj.questown.core.network.EconomicsUpdate;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.mc.Compat;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.crafting.Ingredient;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static ca.bradj.questown.gui.PagedCardScreen.*;

public class TownEconomicsScreen extends AbstractPagedCardScreen<TownEconomicsMenu, ItemEconomicsData> {
    private final FlagTabs tabs;

    // The unmet-need card the one-time free-food claim rides on (the town's
    // food need is the near-certain day-1 card - see .scratch/day1-playtest-
    // 2026-10-11/free-food-economics-card.md).
    private static final String FOOD_NEED_KEY = "#questown:villager_food";

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

        this.tabs = FlagTabs.forMenu(menu, "tooltips.economics");
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

    private boolean bonusFoodAvailable() {
        return menu.getFlagInfo().bonusFoodAvailable();
    }

    private boolean isFoodCard(Card<ItemEconomicsData> card) {
        return FOOD_NEED_KEY.equals(card.data().ingredientKey());
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
                List<Component> lines = new ArrayList<>(List.of(
                        Compat.translatable(key1, itemName, timesNeeded),
                        Compat.translatable(key2, ItemEconomicsData.PERIOD_DAYS)
                ));
                if (bonusFoodAvailable() && isFoodCard(card)) {
                    lines.add(Compat.translatable("questown.menu.free_food_card.tooltip"));
                }
                // Wrapped like the other card screens' tooltips (playtest 2026-10-17: the
                // free-food line ran the full screen width).
                renderTooltipWithDynamicWrapping(ctx, mouseX, mouseY, lines);
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
        renderDataAndIcons(ctx, card.coords(), card.data(), mouseX, mouseY, card);
        return null;
    }

    private void renderDataAndIcons(
            RenderContext ctx,
            CardCoordinates c,
            ItemEconomicsData data,
            int mouseX,
            int mouseY,
            Card<ItemEconomicsData> card
    ) {
        int iconX = getIconX(c);
        int iconY = getIconY(c);
        Ingredient item = Ingredients.fromString(data.ingredientKey());
        if (UtilClean.isCoordInBox(mouseX, mouseY, iconX, iconY, 16, 16)) {
            ctx.fill(iconX, iconY, iconX + 16, iconY + 16, RenderUtil.SHADOW);
        }
        ingredientRenderer.render(ctx, item, iconX, iconY);
        int textX = iconX + ingredientRenderer.getSize() + SMALL_PADDING + SMALL_PADDING;
        int textY = c.topYPadded();
        ctx.darkText(Ingredients.getName(item), textX, textY);
        String count = Integer.toString(data.timesNeeded());
        int countX = c.rightXPadded() - font.width(count);
        ctx.darkText(Compat.literal(count), countX, textY);
        // The one-time free-food claim rides on the food card while available:
        // a short red affordance on the card, the full sentence in the tooltip.
        if (bonusFoodAvailable() && isFoodCard(card)) {
            int claimY = textY + font.lineHeight + 2;
            ctx.darkText(Compat.translatableStyled("questown.menu.free_food_card",
                    Style.EMPTY.applyFormat(net.minecraft.ChatFormatting.RED)), textX, claimY);
        }
    }

    private static int getIconY(CardCoordinates coords) {
        return coords.topY() + MED_PADDING;
    }

    private static int getIconX(CardCoordinates coords) {
        return coords.leftX() + MED_PADDING;
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
            // The whole food card is the claim affordance while the one-time
            // bonus is available (the icon sub-hit still sends the old INQUIRED
            // work-request ping, same as any card).
            if (bonusFoodAvailable() && isFoodCard(card)
                    && p_97750_ == 0
                    && UtilClean.isCoordInBox(mouseX, mouseY, c.leftX(), c.topY(), CARD_WIDTH, cardHeight)) {
                QuestownNetwork.sendToServer(new ClaimFreeFoodMessage(menu.getFlagInfo().flagPos()));
                return true;
            }
            if (UtilClean.isCoordInBox(mouseX, mouseY, getIconX(c), getIconY(c), 16, 16)) {
                Ingredient ing = Ingredients.fromString(card.data().ingredientKey());
                AddWorkFromUIMessage.Action inquired = AddWorkFromUIMessage.Action.INQUIRED;
                AddWorkFromUIMessage msg = new AddWorkFromUIMessage(ing, menu.getFlagInfo().flagPos(), inquired);
                QuestownNetwork.sendToServer(msg);
                return true;
            }
        }
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight()) / 2;
        this.tabs.mouseClicked(x, y, mouseX, mouseY);
        return super.mouseClicked(mouseX, mouseY, p_97750_);
    }

    @Override
    protected void setRenderColorForCard(ItemEconomicsData itemEconomicsData) {
    }
}