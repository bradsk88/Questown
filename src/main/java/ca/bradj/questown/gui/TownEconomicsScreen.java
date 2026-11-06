package ca.bradj.questown.gui;

import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.core.network.AddWorkFromUIMessage;
import ca.bradj.questown.core.network.EconomicsUpdate;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.JEI;
import com.google.common.collect.ImmutableList;
import mezz.jei.gui.elements.GuiIconButtonSmall;
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

        this.tabs = FlagTabs.forMenu(menu, "tooltips.economics");
    }

    @Override
    protected ImmutableList<ItemEconomicsData> cardsData() {
        return EconomicsCardOrder.order(showRecent ? lastUpdate.recent() : lastUpdate.data(), bonusFoodAvailable(), FOOD_NEED_KEY);
    }

    private final GuiIconButtonSmall previousView = JEI.guiIconButtonSmall(
            0, 0, buttonWidth, buttonHeight, JEI.getArrowPrevious(), b -> showRecent = !showRecent
    );
    private final GuiIconButtonSmall nextView = JEI.guiIconButtonSmall(
            0, 0, buttonWidth, buttonHeight, JEI.getArrowNext(), b -> showRecent = !showRecent
    );

    @Override
    protected void init() {
        super.init();
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight()) / 2 + backgroundHeight() - borderPadding - buttonHeight;
        previousView.x = x + borderPadding;
        previousView.y = y;
        nextView.x = x + backgroundWidth - buttonWidth - borderPadding;
        nextView.y = y;
        addRenderableWidget(previousView);
        addRenderableWidget(nextView);
    }

    private boolean isOverViewToggle(int mouseX, int mouseY) {
        int x = previousView.x;
        return UtilClean.isCoordInBox(mouseX, mouseY, x, previousView.y,
                nextView.x + buttonWidth - x, buttonHeight);
    }

    private Component viewToggleLabel() {
        return Compat.translatable(showRecent ? "questown.menu.economics.view.recent" : "questown.menu.economics.view.all");
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
        drawSelectorStrip(ctx, previousView, nextView, (this.width - backgroundWidth) / 2 + backgroundWidth,
                viewToggleLabel().getString());
        ctx.renderWidget(previousView, mouseX, mouseY, partialTicks);
        ctx.renderWidget(nextView, mouseX, mouseY, partialTicks);
    }

    private void renderTooltips(
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
            if (UtilClean.isCoordInBox(mouseX, mouseY, x, y, CARD_WIDTH, cardHeight)) {
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
        this.renderTooltips(poseStack, mouseX, mouseY);
    }

    @Override
    protected List<Component> renderCardContent(
            PoseStack poseStack,
            Card<ItemEconomicsData> card,
            int mouseX,
            int mouseY
    ) {
        renderDataAndIcons(poseStack, card.coords(), card.data(), mouseX, mouseY);
        return null;
    }

    private void renderDataAndIcons(
            PoseStack poseStack,
            CardCoordinates c,
            ItemEconomicsData data,
            int mouseX,
            int mouseY
    ) {
        int iconX = getIconX(c);
        int iconY = getIconY(c);
        Ingredient item = Ingredients.fromString(data.ingredientKey());
        if (UtilClean.isCoordInBox(mouseX, mouseY, iconX, iconY, 16, 16)) {
            fill(poseStack, iconX, iconY, iconX + 16, iconY + 16,RenderUtil.SHADOW);
        }
        ingredientRenderer.render(itemRenderer, item, iconX, iconY);
        int textX = iconX + ingredientRenderer.getSize() + SMALL_PADDING + SMALL_PADDING;
        int textY = c.topYPadded();
        Compat.drawDarkText(font, poseStack, Ingredients.getName(item), textX, textY);
        String count = Integer.toString(data.timesNeeded());
        int countX = c.rightXPadded() - font.width(count);
        Compat.drawDarkText(font, poseStack, Compat.literal(count), countX, textY);
    }

    private static int getIconY(CardCoordinates coords) {
        return coords.topY() + MED_PADDING;
    }

    private static int getIconX(CardCoordinates coords) {
        return coords.leftX() + MED_PADDING;
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
        for (Card<ItemEconomicsData> card : cards()) {
            CardCoordinates c = card.coords();
            if (UtilClean.isCoordInBox(mouseX, mouseY, getIconX(c), getIconY(c), 16, 16)) {
                Ingredient ing = Ingredients.fromString(card.data().ingredientKey());
                AddWorkFromUIMessage.Action inquired = AddWorkFromUIMessage.Action.INQUIRED;
                AddWorkFromUIMessage msg = new AddWorkFromUIMessage(ing, menu.getFlagInfo().flagPos(), inquired);
                QuestownNetwork.CHANNEL.sendToServer(msg);
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