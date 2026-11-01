package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.entity.renderer.SpinningCube;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

public class SpinningCubeLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private static final ResourceLocation CUBE_TEXTURE = Questown.ResourceLocation(
            "textures/blocks/block_of_progress.png");

    public SpinningCubeLayer(LivingEntityRenderer<T, M> renderer) {
        super(renderer);
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            T entity,
            float limbSwing,
            float limbSwingAmount,
            float partialTicks,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
    ) {
        if (!(entity instanceof VisitorMobEntity vme)) {
            return;
        }
        if (!vme.hasBlockOfProgress()) {
            return;
        }

        poseStack.pushPose();
        // Position above the entity's head, then draw the shared spinning cube (top-left texture quad).
        poseStack.translate(0.0D, -(entity.getBbHeight() / 2), 0.0D);
        SpinningCube.render(poseStack, bufferSource, 0xF000F0, 0xFFFFFFFF, CUBE_TEXTURE, ageInTicks, 0.3f, 0.0f, 0.25f, 0.0f, 0.25f);
        poseStack.popPose();
    }
}
