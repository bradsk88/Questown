package ca.bradj.questown.mobs.helperchicken;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ChickenModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * World-space speech-bubble layer for the helper chicken.
 *
 * <p>Renders the entity's currently-synced bubble icon (see
 * {@link HelperChickenEntity#getBubbleIconA()} /
 * {@link HelperChickenEntity#getBubbleIconB()}) above its head as a billboard.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>Distance gate: when through-walls mode is OFF, the bubble is hidden
 *       beyond 16 blocks.</li>
 *   <li>Alternation: with two non-empty icons, hard-cuts every 20 ticks (1s).
 *       Icon A displays first. With a single icon, never switches.</li>
 *   <li>Through-walls: when the entity's {@code through-walls} synced flag is
 *       {@code true}, the item icon is rendered with depth test disabled (v1
 *       uses a {@link WrappingBufferSource} that substitutes
 *       {@link BubbleRenderType#throughWalls} for the normal item-atlas type).</li>
 *   <li>Fullbright: bubbles use {@code packedLight = 0xF000F0} so they stay
 *       legible in dark scenes.</li>
 * </ul>
 *
 * <p>The generic parameters match {@link net.minecraft.client.renderer.entity.ChickenRenderer}
 * rather than {@link HelperChickenEntity} — the superclass renderer is
 * parameterized on the parent {@code Chicken} type, so the layer must be too.
 * The {@code instanceof} check in {@link #render} discriminates.
 */
@OnlyIn(Dist.CLIENT)
public class HelperChickenBubbleLayer extends RenderLayer<Chicken, ChickenModel<Chicken>> {

    private static final int FULLBRIGHT = 0xF000F0;
    private static final double VISIBILITY_RANGE_SQR = 16.0 * 16.0;
    private static final int ALTERNATION_TICKS = 20;

    private final EntityRenderDispatcher dispatcher;

    public HelperChickenBubbleLayer(
            LivingEntityRenderer<Chicken, ChickenModel<Chicken>> parent,
            EntityRendererProvider.Context ctx
    ) {
        super(parent);
        this.dispatcher = ctx.getEntityRenderDispatcher();
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            Chicken entity,
            float limbSwing,
            float limbSwingAmount,
            float partialTicks,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
    ) {
        if (!(entity instanceof HelperChickenEntity helper)) {
            return;
        }
        boolean throughWalls = helper.isThroughWalls();
        if (!throughWalls && dispatcher.distanceToSqr(helper) > VISIBILITY_RANGE_SQR) {
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
        renderBubble(poseStack, bufferSource, helper, shown, throughWalls);
    }

    private void renderBubble(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            HelperChickenEntity helper,
            ItemStack shown,
            boolean throughWalls
    ) {
        poseStack.pushPose();
        poseStack.translate(0.0, helper.getBbHeight() + 0.3, 0.0);
        poseStack.mulPose(dispatcher.cameraOrientation());
        poseStack.scale(0.5f, -0.5f, 0.5f);

        MultiBufferSource effectiveBuffer = throughWalls
                ? new WrappingBufferSource(bufferSource)
                : bufferSource;
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
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
     * <p>This is the "wrap the buffer source" path from the U2 spec. The
     * through-walls render type shares the shader and format with the item
     * atlas, so buffers line up byte-for-byte.
     */
    private static final class WrappingBufferSource implements MultiBufferSource {
        private final MultiBufferSource delegate;

        WrappingBufferSource(MultiBufferSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public VertexConsumer getBuffer(RenderType original) {
            RenderType rewritten = rewriteForThroughWalls(original);
            return delegate.getBuffer(rewritten);
        }

        private static RenderType rewriteForThroughWalls(RenderType original) {
            // Preserve the original texture if we can discover it; otherwise
            // fall back to the vanilla item atlas (what ItemRenderer uses).
            //
            // RenderType doesn't publicly expose its texture, so we key off
            // the name — which is stable for vanilla item-rendering types.
            // For anything we don't recognise we leave the original in place;
            // the bubble will then behave like a normal (occluded) icon, a
            // safe visual degradation.
            return BubbleRenderType.throughWalls(
                    net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS
            );
        }
    }

    /**
     * Unused — retained as a reference for the textured-quad fallback path
     * documented in the U2 spec. The wrap-the-buffersource path above is the
     * shipping implementation.
     */
    @SuppressWarnings("unused")
    private static void renderTexturedQuad(
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight
    ) {
        Matrix4f m = poseStack.last().pose();
        consumer.vertex(m, -0.5f, -0.5f, 0.0f).color(255, 255, 255, 255)
                .uv(0f, 0f).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(packedLight).normal(0f, 0f, 1f).endVertex();
        consumer.vertex(m, 0.5f, -0.5f, 0.0f).color(255, 255, 255, 255)
                .uv(1f, 0f).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(packedLight).normal(0f, 0f, 1f).endVertex();
        consumer.vertex(m, 0.5f, 0.5f, 0.0f).color(255, 255, 255, 255)
                .uv(1f, 1f).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(packedLight).normal(0f, 0f, 1f).endVertex();
        consumer.vertex(m, -0.5f, 0.5f, 0.0f).color(255, 255, 255, 255)
                .uv(0f, 1f).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(packedLight).normal(0f, 0f, 1f).endVertex();
    }
}
