package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.Questown;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix4f;
import com.mojang.math.Quaternion;
import com.mojang.math.Vector3f;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

public class SpinningCubeLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private static final ResourceLocation CUBE_TEXTURE = Questown.ResourceLocation("textures/blocks/block_of_progress.png");

    public SpinningCubeLayer(LivingEntityRenderer<T, M> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, T entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        packedLight = 0xF000F0;

        poseStack.pushPose();

        // Translate to the position above the entity's head
        poseStack.translate(0.0D, - (entity.getBbHeight()/2), 0.0D);

        // Make the cube smaller
        float scale = 0.3f;
        poseStack.scale(scale, scale, scale);

        // Apply rotation based on time
        float rotationSpeed = 2.0f; // Adjust for faster/slower rotation
        float angle = ageInTicks * rotationSpeed;
        float radians = (float) Math.toRadians(angle);

        // Rotate around the Y-axis
        poseStack.mulPose(new Quaternion(new Vector3f(0.0f, 1.0f, 0.0f), radians, false));

        VertexConsumer builder = bufferSource.getBuffer(RenderType.entitySolid(CUBE_TEXTURE));
        Matrix4f matrix = poseStack.last().pose();

        float minU = 0.0f;
        float maxU = 0.25f;
        float minV = 0.0f;
        float maxV = 0.25f;

        // Define the vertices of the cube using the top-left texture square
        // Front face
        builder.vertex(matrix, -0.5f, -0.5f, 0.5f).color(1.0f, 1.0f, 1.0f, 1.0f).uv(minU, minV).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 0, 1).endVertex();
        builder.vertex(matrix, 0.5f, -0.5f, 0.5f).color(1.0f, 1.0f, 1.0f, 1.0f).uv(maxU, minV).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 0, 1).endVertex();
        builder.vertex(matrix, 0.5f, 0.5f, 0.5f).color(1.0f, 1.0f, 1.0f, 1.0f).uv(maxU, maxV).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 0, 1).endVertex();
        builder.vertex(matrix, -0.5f, 0.5f, 0.5f).color(1.0f, 1.0f, 1.0f, 1.0f).uv(minU, maxV).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 0, 1).endVertex();

        // Back face
        builder.vertex(matrix, -0.5f, -0.5f, -0.5f).color(1.0f, 1.0f, 1.0f, 1.0f).uv(minU, minV).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 0, -1).endVertex();
        builder.vertex(matrix, -0.5f, 0.5f, -0.5f).color(1.0f, 1.0f, 1.0f, 1.0f).uv(minU, maxV).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 0, -1).endVertex();
        builder.vertex(matrix, 0.5f, 0.5f, -0.5f).color(1.0f, 1.0f, 1.0f, 1.0f).uv(maxU, maxV).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 0, -1).endVertex();
        builder.vertex(matrix, 0.5f, -0.5f, -0.5f).color(1.0f, 1.0f, 1.0f, 1.0f).uv(maxU, minV).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 0, -1).endVertex();

        // Top face
        builder.vertex(matrix, -0.5f, 0.5f, 0.5f).color(1.0f, 1.0f, 1.0f, 1.0f).uv(minU, minV).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, -1, 0).endVertex();
        builder.vertex(matrix, 0.5f, 0.5f, 0.5f).color(1.0f, 1.0f, 1.0f, 1.0f).uv(maxU, minV).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, -1, 0).endVertex();
        builder.vertex(matrix, 0.5f, 0.5f, -0.5f).color(1.0f, 1.0f, 1.0f, 1.0f).uv(maxU, maxV).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, -1, 0).endVertex();
        builder.vertex(matrix, -0.5f, 0.5f, -0.5f).color(1.0f, 1.0f, 1.0f, 1.0f).uv(minU, maxV).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, -1, 0).endVertex();

        // Bottom face
        builder.vertex(matrix, -0.5f, -0.5f, 0.5f).color(1.0f, 1.0f, 1.0f, 1.0f).uv(minU, minV).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 1, 0).endVertex();
        builder.vertex(matrix, -0.5f, -0.5f, -0.5f).color(1.0f, 1.0f, 1.0f, 1.0f).uv(minU, maxV).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 1, 0).endVertex();
        builder.vertex(matrix, 0.5f, -0.5f, -0.5f).color(1.0f, 1.0f, 1.0f, 1.0f).uv(maxU, maxV).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 1, 0).endVertex();
        builder.vertex(matrix, 0.5f, -0.5f, 0.5f).color(1.0f, 1.0f, 1.0f, 1.0f).uv(maxU, minV).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(0, 1, 0).endVertex();

        // Right face
        builder.vertex(matrix, 0.5f, -0.5f, 0.5f).color(1.0f, 1.0f, 1.0f, 1.0f).uv(minU, minV).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(1, 0, 0).endVertex();
        builder.vertex(matrix, 0.5f, -0.5f, -0.5f).color(1.0f, 1.0f, 1.0f, 1.0f).uv(minU, maxV).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(1, 0, 0).endVertex();
        builder.vertex(matrix, 0.5f, 0.5f, -0.5f).color(1.0f, 1.0f, 1.0f, 1.0f).uv(maxU, maxV).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(1, 0, 0).endVertex();
        builder.vertex(matrix, 0.5f, 0.5f, 0.5f).color(1.0f, 1.0f, 1.0f, 1.0f).uv(maxU, minV).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(1, 0, 0).endVertex();

        // Left face
        builder.vertex(matrix, -0.5f, -0.5f, 0.5f).color(1.0f, 1.0f, 1.0f, 1.0f).uv(minU, minV).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(-1, 0, 0).endVertex();
        builder.vertex(matrix, -0.5f, 0.5f, 0.5f).color(1.0f, 1.0f, 1.0f, 1.0f).uv(maxU, minV).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(-1, 0, 0).endVertex();
        builder.vertex(matrix, -0.5f, 0.5f, -0.5f).color(1.0f, 1.0f, 1.0f, 1.0f).uv(maxU, maxV).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(-1, 0, 0).endVertex();
        builder.vertex(matrix, -0.5f, -0.5f, -0.5f).color(1.0f, 1.0f, 1.0f, 1.0f).uv(minU, maxV).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(-1, 0, 0).endVertex();

        poseStack.popPose();
    }
}