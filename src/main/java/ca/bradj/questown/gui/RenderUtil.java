package ca.bradj.questown.gui;

import ca.bradj.questown.core.Coordinate;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.mc.Compat;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.util.BiConsumer;

import java.util.List;
import java.util.function.Function;

public class RenderUtil {

    public static final int SHADOW = 0x30000000;
    public static final int HIGHLIGHT = 0x80FFFFFF;

    public static void renderEllipsesWithTooltip(
            EllipsesData data,
            BiConsumer<Component, Coordinate> renderDarkText,
            BiConsumer<Component, Coordinate> renderTooltip,
            BiConsumer<Coordinate, Coordinate> renderHighlight
    ) {
        int itemX = data.topLeft.x() + (24 * data.maxItemsBeforeEllipses);
        Coordinate squareTopLeft = new Coordinate(itemX, data.topLeft.y());
        renderDarkText.accept(Compat.literal("…"), squareTopLeft.shifted(4, 2));
        if (UtilClean.isCoordInBox(data.mouse, squareTopLeft, squareTopLeft.shifted(16, 16))) {
            renderEllipseTooltip(data, renderTooltip, renderHighlight, itemX);
        }
    }

    private static void renderEllipseTooltip(
            EllipsesData data,
            BiConsumer<Component, Coordinate> renderTooltip,
            BiConsumer<Coordinate, Coordinate> renderHighlight,
            int itemX
    ) {
        Coordinate itemTopLeft = new Coordinate(itemX, data.topLeft.y());
        renderHighlight.accept(itemTopLeft, itemTopLeft.shifted(16, 16));
        Component andMore = Compat.translatable(
                "menu.work_add_confirm.and_n_more",
                data.totalItemCount - data.maxItemsBeforeEllipses
        );
        renderTooltip.accept(andMore, itemTopLeft);
    }

    // TODO: Make this a widget so it can handle clicks
    public static <ITEM> void stripOfRequestableItems(
            BiConsumer<ITEM, Coordinate> renderIngredient,
            Function<ITEM, ImmutableList<Component>> tooltipText,
            BiConsumer<Component, Coordinate> renderDarkText,
            BiConsumer<List<Component>, Coordinate> renderTooltip,
            BiConsumer<Coordinate, Coordinate> renderHighlight,
            List<ITEM> d,
            Coordinate topLeft,
            Coordinate bottomRight,
            Coordinate mouse
    ) {
        int maxItemsOnX = (bottomRight.x() - topLeft.x()) / 24;
        for (int i = 0; i < Math.min(maxItemsOnX, d.size()); i++) {
            ITEM ing = d.get(i);
            Coordinate iTopLeft = new Coordinate(topLeft.x() + (i * 24), topLeft.y());
            renderIngredient.accept(ing, iTopLeft);
            Coordinate iBotRight = iTopLeft.shifted(16, 16);
            if (UtilClean.isCoordInBox(mouse, iTopLeft, iBotRight)) {
                renderHighlight.accept(iTopLeft, iBotRight);
                renderTooltip.accept(tooltipText.apply(ing), new Coordinate(iBotRight.x(), iTopLeft.y()));
            }
        }
        if (d.size() > maxItemsOnX) {
            RenderUtil.EllipsesData data = new RenderUtil.EllipsesData(mouse, topLeft, maxItemsOnX, d.size());
            RenderUtil.renderEllipsesWithTooltip(
                    data,
                    renderDarkText,
                    (text, coord) -> renderTooltip.accept(ImmutableList.of(text), coord),
                    renderHighlight
            );
        }
    }

    public static void highlight(
            PoseStack stack,
            Coordinate topLeft,
            Coordinate botRight
    ) {
        GuiComponent.fill(stack, topLeft.x(), topLeft.y(), botRight.x(), botRight.y(), HIGHLIGHT);
    }

    public record EllipsesData(
            Coordinate mouse,
            Coordinate topLeft,
            int maxItemsBeforeEllipses,
            int totalItemCount
    ) {
    }
}
