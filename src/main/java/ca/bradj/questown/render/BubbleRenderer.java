package ca.bradj.questown.render;

import ca.bradj.questown.mc.Compat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the speech bubbles over villagers and dead doors, facing the camera.
 * Hidden beyond 16 blocks unless drawn through walls; two icons alternate each second; always fully lit.
 */
public final class BubbleRenderer {

    private static final int FULLBRIGHT = 0xF000F0;
    private static final double VISIBILITY_RANGE_SQR = 16.0 * 16.0;
    private static final int ALTERNATION_TICKS = 20;

    private static final float HEAD_LIFT_BLOCKS = 0.5f;

    /** Extra lift above a nameplate. */
    private static final float NAMEPLATE_CLEARANCE_BLOCKS = 0.3f;

    private static final float ASSEMBLY_SCALE = 0.5f;

    /** Where the bubble and its icon are centred, so items and blocks both sit inside it. */
    private static final float ICON_CENTRE_Y_INNER = 13.0f / 16.0f;

    private BubbleRenderer() {
    }

    private static double bubbleLift(Entity entity) {
        double lift = entity.getBbHeight() + HEAD_LIFT_BLOCKS;
        if (entity.shouldShowName()) {
            return lift + NAMEPLATE_CLEARANCE_BLOCKS;
        }
        return lift;
    }

    /** Draws a bubble with a given icon over any entity. Same pose rules as {@link #renderBubbleFor}. */
    public static void renderTextureBubbleFor(
            Entity entity,
            net.minecraft.resources.ResourceLocation texture,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            EntityRenderDispatcher dispatcher
    ) {
        if (dispatcher.distanceToSqr(entity) > VISIBILITY_RANGE_SQR) {
            return;
        }
        renderTextureBubble(poseStack, bufferSource, entity, texture, false, dispatcher);
    }

    public static void renderIconBubbleFor(
            Entity entity,
            ItemStack icon,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            EntityRenderDispatcher dispatcher
    ) {
        if (icon.isEmpty() || dispatcher.distanceToSqr(entity) > VISIBILITY_RANGE_SQR) {
            return;
        }
        renderBubble(poseStack, bufferSource, entity, icon, false, dispatcher);
    }

    /** Draws a bubble whose icon is a texture rather than an item. */
    private static void renderTextureBubble(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Entity entity,
            net.minecraft.resources.ResourceLocation texture,
            boolean throughWalls,
            EntityRenderDispatcher dispatcher
    ) {
        poseStack.pushPose();
        poseStack.translate(0.0, bubbleLift(entity), 0.0);
        poseStack.scale(ASSEMBLY_SCALE, ASSEMBLY_SCALE, ASSEMBLY_SCALE);
        poseStack.mulPose(dispatcher.cameraOrientation());

        poseStack.pushPose();
        poseStack.translate(0.0, ICON_CENTRE_Y_INNER, 0.0);
        renderBubbleBackground(poseStack, bufferSource, throughWalls);
        poseStack.popPose();

        VertexConsumer consumer = bufferSource.getBuffer(
                BubbleRenderType.texturedIcon(texture, throughWalls)
        );
        Matrix4f matrix = poseStack.last().pose();
        // A little in front of the bubble, so they don't z-fight.
        float z = 0.001f;
        float r = 0.5f; // half-extent of the icon quad in pose-local units
        float yc = ICON_CENTRE_Y_INNER;
        // Quad winding (camera-facing): bottom-left, bottom-right, top-right, top-left
        textureQuad(consumer, matrix, -r, yc - r, +r, yc - r, +r, yc + r, -r, yc + r, z);

        poseStack.popPose();
    }

    private static void textureQuad(
            VertexConsumer c,
            Matrix4f m,
            float x1, float y1,
            float x2, float y2,
            float x3, float y3,
            float x4, float y4,
            float z
    ) {
        emitTexVertex(c, m, x1, y1, z, 0f, 1f);
        emitTexVertex(c, m, x2, y2, z, 1f, 1f);
        emitTexVertex(c, m, x3, y3, z, 1f, 0f);
        emitTexVertex(c, m, x4, y4, z, 0f, 0f);
    }

    private static void emitTexVertex(
            VertexConsumer c,
            Matrix4f m,
            float x, float y, float z,
            float u, float v
    ) {
        c.vertex(m, x, y, z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .uv2(FULLBRIGHT)
                .normal(0f, 0f, 1f)
                .endVertex();
    }

    /** Draws a bubble over a block (a dead door). The pose is camera-relative world space; the bubble floats above the two-block door. */
    public static void renderIconBubbleAtBlock(
            net.minecraft.core.BlockPos pos,
            ItemStack icon,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            net.minecraft.world.phys.Vec3 cameraPos
    ) {
        if (icon.isEmpty()
                || cameraPos.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > VISIBILITY_RANGE_SQR) {
            return;
        }
        // After the translucent pass, the camera orientation is still valid.
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        poseStack.pushPose();
        poseStack.translate(
                pos.getX() + 0.5 - cameraPos.x,
                pos.getY() + 2.0 + HEAD_LIFT_BLOCKS - cameraPos.y,
                pos.getZ() + 0.5 - cameraPos.z
        );
        renderBubbleAssembly(poseStack, bufferSource, icon, false, dispatcher);
        poseStack.popPose();
    }

    /** Draws a bubble with a texture icon over a block: the "!" bubbles over the flag and the job board. */
    public static void renderTextureBubbleAtBlock(
            net.minecraft.core.BlockPos pos,
            net.minecraft.resources.ResourceLocation texture,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            net.minecraft.world.phys.Vec3 cameraPos
    ) {
        if (texture == null
                || cameraPos.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > VISIBILITY_RANGE_SQR) {
            return;
        }
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        poseStack.pushPose();
        // One block above: the flag and the board are one block tall.
        poseStack.translate(
                pos.getX() + 0.5 - cameraPos.x,
                pos.getY() + 1.0 + HEAD_LIFT_BLOCKS - cameraPos.y,
                pos.getZ() + 0.5 - cameraPos.z
        );
        poseStack.scale(ASSEMBLY_SCALE, ASSEMBLY_SCALE, ASSEMBLY_SCALE);
        poseStack.mulPose(dispatcher.cameraOrientation());

        poseStack.pushPose();
        poseStack.translate(0.0, ICON_CENTRE_Y_INNER, 0.0);
        renderBubbleBackground(poseStack, bufferSource, false);
        poseStack.popPose();

        VertexConsumer consumer = bufferSource.getBuffer(
                BubbleRenderType.texturedIcon(texture, false)
        );
        Matrix4f matrix = poseStack.last().pose();
        // A little in front of the bubble, so they don't z-fight.
        float z = 0.001f;
        float r = 0.5f;
        float yc = ICON_CENTRE_Y_INNER;
        textureQuad(consumer, matrix, -r, yc - r, +r, yc - r, +r, yc + r, -r, yc + r, z);

        poseStack.popPose();
    }

    private static void renderBubble(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Entity entity,
            ItemStack shown,
            boolean throughWalls,
            EntityRenderDispatcher dispatcher
    ) {
        poseStack.pushPose();
        poseStack.translate(0.0, bubbleLift(entity), 0.0);
        renderBubbleAssembly(poseStack, bufferSource, shown, throughWalls, dispatcher);
        poseStack.popPose();
    }

    /** Scales, faces the camera, and draws the bubble and its icon. The icon turns 180 degrees first, or blocks show their underside. */
    private static void renderBubbleAssembly(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            ItemStack shown,
            boolean throughWalls,
            EntityRenderDispatcher dispatcher
    ) {
        poseStack.pushPose();
        poseStack.scale(ASSEMBLY_SCALE, ASSEMBLY_SCALE, ASSEMBLY_SCALE);
        poseStack.mulPose(dispatcher.cameraOrientation());

        // The bubble first; the icon goes over it.
        poseStack.pushPose();
        poseStack.translate(0.0, ICON_CENTRE_Y_INNER, 0.0);
        renderBubbleBackground(poseStack, bufferSource, throughWalls);
        poseStack.popPose();

        MultiBufferSource effectiveBuffer = throughWalls
                ? new WrappingBufferSource(bufferSource)
                : bufferSource;
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        // GUI transform centres both 2D items and blocks around the local
        // origin (HEAD's translate is type-dependent — blocks land at origin
        // while 2D items land at +13/16 Y, which made block bubbles render in
        // the bottom-left corner). We translate to the bubble centre so the
        // icon sits inside it regardless of item kind.
        poseStack.pushPose();
        poseStack.translate(0.0, ICON_CENTRE_Y_INNER, 0.0);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F));
        Compat.renderItemStaticGui(
                itemRenderer,
                shown,
                FULLBRIGHT,
                poseStack,
                effectiveBuffer,
                Minecraft.getInstance().level,
                0
        );
        poseStack.popPose();
        poseStack.popPose();
    }

    /** A comic speech bubble from white quads: an octagon with a tail pointing down. +Y is screen-up. */
    private static void renderBubbleBackground(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            boolean throughWalls
    ) {
        VertexConsumer consumer = bufferSource.getBuffer(
                BubbleRenderType.solidWhite(throughWalls)
        );
        Matrix4f matrix = poseStack.last().pose();

        whiteQuad(consumer, matrix, -0.4f, +0.7f, +0.4f, +0.7f, +0.7f, +0.4f, -0.7f, +0.4f);
        whiteQuad(consumer, matrix, -0.7f, +0.4f, +0.7f, +0.4f, +0.7f, -0.4f, -0.7f, -0.4f);
        whiteQuad(consumer, matrix, -0.7f, -0.4f, +0.7f, -0.4f, +0.4f, -0.7f, -0.4f, -0.7f);
        whiteQuad(consumer, matrix, -0.2f, -0.7f, +0.1f, -0.7f, -0.1f, -1.1f, -0.1f, -1.1f);
    }

    private static void whiteQuad(
            VertexConsumer c,
            Matrix4f m,
            float x1, float y1,
            float x2, float y2,
            float x3, float y3,
            float x4, float y4
    ) {
        c.vertex(m, x1, y1, 0.0f).color(255, 255, 255, 255).endVertex();
        c.vertex(m, x2, y2, 0.0f).color(255, 255, 255, 255).endVertex();
        c.vertex(m, x3, y3, 0.0f).color(255, 255, 255, 255).endVertex();
        c.vertex(m, x4, y4, 0.0f).color(255, 255, 255, 255).endVertex();
    }

    /** The icon to show: A alone, or A and B alternating each second, A first. */
    public static ItemStack chooseDisplayedIcon(
            int tickCount,
            ItemStack iconA,
            ItemStack iconB
    ) {
        if (iconB.isEmpty()) {
            return iconA;
        }
        if (iconA.isEmpty()) {
            return iconB;
        }
        boolean showA = ((tickCount / ALTERNATION_TICKS) % 2) == 0;
        return showA ? iconA : iconB;
    }

    /** Swaps every requested render type for its through-walls version with the same texture. */
    private static final class WrappingBufferSource implements MultiBufferSource {
        private final MultiBufferSource delegate;

        WrappingBufferSource(MultiBufferSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public VertexConsumer getBuffer(RenderType original) {
            return delegate.getBuffer(BubbleRenderType.throughWalls(
                    net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS
            ));
        }
    }
}
