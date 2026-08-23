package ca.bradj.questown.mobs.helperchicken;

import ca.bradj.questown.render.BubbleRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ChickenRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Client-side renderer for the helper chicken.
 *
 * <p>Reuses the vanilla {@link ChickenRenderer} for the base model and adds
 * a world-space speech bubble above the chicken's head via
 * {@link BubbleRenderer#renderBubbleFor}.
 *
 * <p>The bubble is rendered from this class's {@code render} override after
 * {@code super.render} returns — at that point the pose stack is in OUTER
 * (camera-relative) state with the entity's body-yaw rotation popped, so
 * {@code dispatcher.cameraOrientation()} alone gives a true camera-facing
 * billboard. Driving the bubble from a {@code RenderLayer} would happen
 * INSIDE the entity's body-yaw transformation, so the icon would rotate
 * with the chicken's facing direction instead of with the camera.
 */
@OnlyIn(Dist.CLIENT)
public class HelperChickenRenderer extends ChickenRenderer {

    public HelperChickenRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(
            Chicken entity,
            float entityYaw,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight
    ) {
        super.render(entity, entityYaw, partialTicks, poseStack, bufferSource, packedLight);
        if (entity instanceof HelperChickenEntity helper) {
            BubbleRenderer.renderBubbleFor(
                    helper, poseStack, bufferSource, partialTicks, this.entityRenderDispatcher
            );
        }
    }
}
