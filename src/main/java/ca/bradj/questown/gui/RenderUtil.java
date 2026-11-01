package ca.bradj.questown.gui;

import ca.bradj.questown.core.Coordinate;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.mc.Compat;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.util.BiConsumer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

public class RenderUtil {

    public static final int SHADOW = 0x30000000;
    public static final int HIGHLIGHT = 0x80FFFFFF;

    public static @Nullable List<Component> renderEllipsesAndReturnTooltip(
            EllipsesData data,
            BiConsumer<Component, Coordinate> renderDarkText,
            BiConsumer<Coordinate, Coordinate> renderHighlight
    ) {
        MutableComponent andMore = Compat.translatable(
                "menu.work_add_confirm.and_n_more",
                data.totalItemCount - data.maxItemsBeforeEllipses
        );
        return renderEllipsesAndReturnTooltip(data, andMore, renderDarkText, renderHighlight);
    }

    /** The "…" after a strip's last item, and {@code tooltip} while the mouse is over it. */
    public static @Nullable List<Component> renderEllipsesAndReturnTooltip(
            EllipsesData data,
            Component tooltip,
            BiConsumer<Component, Coordinate> renderDarkText,
            BiConsumer<Coordinate, Coordinate> renderHighlight
    ) {
        int itemX = data.topLeft.x() + (24 * data.maxItemsBeforeEllipses);
        Coordinate squareTopLeft = new Coordinate(itemX, data.topLeft.y());
        renderDarkText.accept(Compat.literal("…"), squareTopLeft.shifted(4, 2));
        if (!UtilClean.isCoordInBox(data.mouse, squareTopLeft.shifted(-4, 0), squareTopLeft.shifted(20, 16))) {
            return null;
        }
        renderHighlight.accept(squareTopLeft, squareTopLeft.shifted(16, 16));
        return ImmutableList.of(tooltip);
    }

    // TODO: Make this a widget so it can handle clicks and send an OpenItemJobsMessage
    public static <ITEM> @Nullable List<Component> stripOfRequestableItems(
            BiConsumer<ITEM, Coordinate> renderIngredient,
            Function<ITEM, ImmutableList<Component>> tooltipText,
            BiConsumer<Component, Coordinate> renderDarkText,
            BiConsumer<Coordinate, Coordinate> renderHighlight,
            List<ITEM> d,
            Coordinate topLeft,
            Coordinate bottomRight,
            Coordinate mouse
    ) {
        ImmutableList<Component> tooltips = null;
        int maxItemsOnX = (bottomRight.x() - topLeft.x()) / 24;
        for (int i = 0; i < Math.min(maxItemsOnX, d.size()); i++) {
            ITEM ing = d.get(i);
            Coordinate iTopLeft = new Coordinate(topLeft.x() + (i * 24), topLeft.y());
            renderIngredient.accept(ing, iTopLeft);
            Coordinate iBotRight = iTopLeft.shifted(16, 16);
            if (UtilClean.isCoordInBox(mouse, iTopLeft.shifted(-4, 0), iBotRight.shifted(4, 0))) {
                renderHighlight.accept(iTopLeft, iBotRight);
                tooltips = tooltipText.apply(ing);
            }
        }
        @Nullable List<Component> ellipseTooltips = null;
        if (d.size() > maxItemsOnX) {
            RenderUtil.EllipsesData data = new RenderUtil.EllipsesData(mouse, topLeft, maxItemsOnX, d.size());
            ellipseTooltips = RenderUtil.renderEllipsesAndReturnTooltip(
                    data,
                    renderDarkText,
                    renderHighlight
            );
        }
        if (tooltips != null) {
            return tooltips;
        }
        return ellipseTooltips;
    }

    public static void highlight(
            PoseStack gui,
            Coordinate topLeft,
            Coordinate botRight
    ) {
        GuiComponent.fill(gui, topLeft.x(), topLeft.y(), botRight.x(), botRight.y(), HIGHLIGHT);
    }

    /**
     * The BOP's model shrinks in its gui display (scale 1.25 on an 8 px cube, so a slot shows it at
     * vanilla block size); these big-icon spots scale from that.
     */
    public static void renderItemScaled(
            ItemRenderer itemRenderer,
            float scale,
            ItemStack defaultInstance,
            int x,
            int y
    ) {
        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        PoseStack posestack = RenderSystem.getModelViewStack();
        posestack.pushPose();
        posestack.translate(x, y, 100.0F + itemRenderer.blitOffset);
        posestack.translate(8.0F, 8.0F, 0.0F);
        posestack.scale(1.0F, -1.0F, 1.0F);
        float size = 16.0F * scale;
        posestack.scale(size, size, size);
        RenderSystem.applyModelViewMatrix();
        PoseStack posestack1 = new PoseStack();
        MultiBufferSource.BufferSource multibuffersource$buffersource = Minecraft.getInstance().renderBuffers()
                                                                                 .bufferSource();
        itemRenderer.render(
                defaultInstance,
                net.minecraft.client.renderer.block.model.ItemTransforms.TransformType.GUI,
                false,
                posestack1,
                multibuffersource$buffersource,
                15728880,
                OverlayTexture.NO_OVERLAY,
                itemRenderer.getModel(defaultInstance, null, null, 0)
        );
        multibuffersource$buffersource.endBatch();
        RenderSystem.enableDepthTest();
        posestack.popPose();
        RenderSystem.applyModelViewMatrix();
    }

    public record EllipsesData(Coordinate mouse, Coordinate topLeft, int maxItemsBeforeEllipses, int totalItemCount) {
    }
}
