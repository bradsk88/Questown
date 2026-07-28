package ca.bradj.questown.mobs.helperchicken;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * World-space speech-bubble renderer for the helper chicken.
 *
 * <p>Driven from {@link HelperChickenRenderer#render} after {@code super.render}
 * returns — at that point the pose stack is in OUTER (world-space, camera-
 * relative) state with the chicken's body-yaw rotation popped, so applying
 * {@link EntityRenderDispatcher#cameraOrientation()} alone gives a true
 * billboard. Drawing from inside a {@code RenderLayer} would happen INSIDE the
 * entity's body-yaw rotation and the icon would rotate with the chicken's
 * facing direction; that is why this class is no longer a {@code RenderLayer}.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>Distance gate: when through-walls mode is OFF, the bubble is hidden
 *       beyond 16 blocks.</li>
 *   <li>Alternation: with two non-empty icons, hard-cuts every 20 ticks (1s).
 *       Icon A displays first. With a single icon, never switches.</li>
 *   <li>Through-walls: when the entity's {@code through-walls} synced flag is
 *       {@code true}, both the bubble background and the item icon render
 *       without depth test (icon via {@link WrappingBufferSource}, background
 *       via {@link BubbleRenderType#solidWhite(boolean)}).</li>
 *   <li>Fullbright: bubbles use {@code packedLight = 0xF000F0} so they stay
 *       legible in dark scenes.</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class HelperChickenBubbleLayer {

    private static final int FULLBRIGHT = 0xF000F0;
    private static final double VISIBILITY_RANGE_SQR = 16.0 * 16.0;
    private static final int ALTERNATION_TICKS = 20;

    /** Additional lift in world-Y above the chicken's head. */
    private static final float HEAD_LIFT_BLOCKS = 0.5f;

    /** Outer scale applied before drawing bubble + icon. */
    private static final float ASSEMBLY_SCALE = 0.5f;

    /**
     * Y in inner pose-local space where both bubble background and icon are
     * centred. Picked to sit a bit above the chicken (matching the
     * head-lifted assembly origin) — the icon translates to this Y under a
     * GUI transform so the bubble's centre rectangle and the item centre
     * coincide regardless of whether the icon is a 2D item or a block.
     */
    private static final float ICON_CENTRE_Y_INNER = 13.0f / 16.0f;

    private HelperChickenBubbleLayer() {
    }

    /**
     * Render the chicken's currently-synced bubble icon at its head.
     *
     * <p>Must be called with {@code poseStack} in OUTER (camera-relative)
     * state — i.e. after {@code super.render} returns from
     * {@link HelperChickenRenderer#render}. Pose origin must be at the
     * entity's feet position; +Y must point world-up (no entity-yaw or
     * Y-flip applied).
     */
    public static void renderBubbleFor(
            HelperChickenEntity helper,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            float partialTicks,
            EntityRenderDispatcher dispatcher
    ) {
        boolean throughWalls = helper.isThroughWalls();
        if (!throughWalls && dispatcher.distanceToSqr(helper) > VISIBILITY_RANGE_SQR) {
            return;
        }
        String texturePath = helper.getBubbleTexturePath();
        if (!texturePath.isEmpty()) {
            renderTextureBubble(
                    poseStack, bufferSource, helper,
                    new net.minecraft.resources.ResourceLocation(texturePath),
                    throughWalls, dispatcher
            );
            return;
        }
        ItemStack shown = chooseDisplayedIcon(
                helper.tickCount,
                helper.getBubbleIconA(),
                helper.getBubbleIconB()
        );
        if (shown.isEmpty()) {
            return;
        }
        renderBubble(poseStack, bufferSource, helper, shown, throughWalls, dispatcher);
    }

    /**
     * Draws the same comic bubble over any entity, for callers that already know which icon to
     * show — the townie need-bubbles of ADR-0011, which pick their icon from a synched need rather
     * than from the chicken's alternating pair.
     *
     * <p>Same pose contract as {@link #renderBubbleFor}: call it after {@code super.render}, with
     * the pose still camera-relative and the origin at the entity's feet.
     */
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

    /**
     * Texture-mode renderer. Same outer pose / billboard as the item path,
     * but the icon is a UV-mapped quad sampling the synced texture instead of
     * an item model. Quad spans roughly the bubble's inner area so the icon
     * sits inside the comic outline.
     */
    private static void renderTextureBubble(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Entity entity,
            net.minecraft.resources.ResourceLocation texture,
            boolean throughWalls,
            EntityRenderDispatcher dispatcher
    ) {
        poseStack.pushPose();
        poseStack.translate(0.0, entity.getBbHeight() + HEAD_LIFT_BLOCKS, 0.0);
        poseStack.scale(ASSEMBLY_SCALE, ASSEMBLY_SCALE, ASSEMBLY_SCALE);
        poseStack.mulPose(dispatcher.cameraOrientation());

        poseStack.pushPose();
        poseStack.translate(0.0, ICON_CENTRE_Y_INNER, 0.0);
        renderBubbleBackground(poseStack, bufferSource, throughWalls);
        poseStack.popPose();

        // Icon quad — sized to fit inside the bubble centre rectangle.
        VertexConsumer consumer = bufferSource.getBuffer(
                BubbleRenderType.texturedIcon(texture, throughWalls)
        );
        Matrix4f matrix = poseStack.last().pose();
        // Slight forward offset so the quad sits in front of the bubble bg
        // and never z-fights with it.
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
        // NEW_ENTITY format: position, color, uv, overlay, lightmap, normal.
        // u/v: bottom-left=(0,1), bottom-right=(1,1), top-right=(1,0), top-left=(0,0)
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

    private static void renderBubble(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Entity entity,
            ItemStack shown,
            boolean throughWalls,
            EntityRenderDispatcher dispatcher
    ) {
        // Outer pose: +Y is world-up. Translate up to chicken's head + lift.
        // Then scale, then billboard via cameraOrientation. HEAD transform
        // brings its own 180°Y rotation that orients the model toward the
        // camera, so we DO NOT apply our own 180°Y here.
        poseStack.pushPose();
        poseStack.translate(0.0, entity.getBbHeight() + HEAD_LIFT_BLOCKS, 0.0);
        poseStack.scale(ASSEMBLY_SCALE, ASSEMBLY_SCALE, ASSEMBLY_SCALE);
        poseStack.mulPose(dispatcher.cameraOrientation());

        // Bubble drawn FIRST (icon overlays it). Lifted to share vertical
        // space with the icon (icon's HEAD-transform centre is at +Y =
        // ICON_CENTRE_Y_INNER in inner pose-local).
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
        itemRenderer.renderStatic(
                shown,
                ItemTransforms.TransformType.GUI,
                FULLBRIGHT,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                effectiveBuffer,
                0
        );
        poseStack.popPose();
        poseStack.popPose();
    }

    /**
     * Comic-style speech bubble drawn as solid-white quads with vertex
     * emission (no texture). Octagonal body decomposed into 3 quads
     * (top trapezoid, centre rectangle, bottom trapezoid) plus a
     * downward-pointing tail (degenerate quad acting as a triangle).
     *
     * <p>Sized 1.4 × 1.4 in pose-local units — at the assembly's 0.5×
     * scale this matches roughly 1.4× the icon's footprint. Tail tip
     * offset slightly left of centre for comic-style asymmetry.
     *
     * <p>Coordinate convention: this method runs in inner pose-local
     * with cameraOrientation already applied, so +Y is screen-up,
     * +X is screen-right. The bubble's TOP edge sits at +Y, BOTTOM at
     * -Y, and the tail extends further into -Y to point down toward
     * the chicken below.
     */
    private static void renderBubbleBackground(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            boolean throughWalls
    ) {
        VertexConsumer consumer = bufferSource.getBuffer(
                BubbleRenderType.solidWhite(throughWalls)
        );
        Matrix4f matrix = poseStack.last().pose();

        // Top trapezoid (visually screen-up, at +Y)
        whiteQuad(consumer, matrix, -0.4f, +0.7f, +0.4f, +0.7f, +0.7f, +0.4f, -0.7f, +0.4f);
        // Centre rectangle
        whiteQuad(consumer, matrix, -0.7f, +0.4f, +0.7f, +0.4f, +0.7f, -0.4f, -0.7f, -0.4f);
        // Bottom trapezoid (visually screen-down, at -Y)
        whiteQuad(consumer, matrix, -0.7f, -0.4f, +0.7f, -0.4f, +0.4f, -0.7f, -0.4f, -0.7f);
        // Tail: degenerate quad pointing further screen-down toward chicken
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

    /**
     * Static helper extracted so it can be unit-tested without rendering.
     *
     * <p>With a single icon (B empty), returns A regardless of tick. With two
     * icons, alternates on 20-tick (1s) boundaries, showing A first.
     */
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

    /**
     * Delegates to an underlying {@link MultiBufferSource} but rewrites any
     * requested {@link RenderType} (usually the item-atlas entity-translucent
     * type) to a through-walls variant bound to the same texture.
     *
     * <p>The through-walls render type shares the shader and format with the
     * item atlas, so buffers line up byte-for-byte.
     */
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
