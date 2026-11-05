package ca.bradj.questown.mobs.helperchicken;

import ca.bradj.questown.render.BubbleRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ChickenRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** The helper chicken, with its speech bubble drawn after the body so it faces the camera, not the chicken's heading. */
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
