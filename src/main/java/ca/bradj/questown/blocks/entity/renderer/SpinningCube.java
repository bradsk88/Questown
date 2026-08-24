package ca.bradj.questown.blocks.entity.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix4f;
import com.mojang.math.Quaternion;
import com.mojang.math.Vector3f;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Shared spinning-cube render used for the block-of-progress hovering over visitors and for the
 * relocation deed hovering over a dormant flag. Extracted from {@code SpinningCubeLayer} so both the
 * visitor render layer and the flag block-entity renderer draw the same cube. The caller sets the
 * translation (where the cube floats); this scales, spins it about Y by {@code ageInTicks}, and draws
 * the six faces sampling {@code [minU,maxU]×[minV,maxV]} of {@code texture}.
 */
public final class SpinningCube {

    private static final float ROTATION_SPEED = 3.0f;

    private SpinningCube() {
    }

    public static void render(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int color,
            ResourceLocation texture,
            float ageInTicks,
            float scale,
            float minU,
            float maxU,
            float minV,
            float maxV
    ) {
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        float radians = (float) Math.toRadians(ageInTicks * ROTATION_SPEED);
        poseStack.mulPose(new Quaternion(new Vector3f(0.0f, 1.0f, 0.0f), radians, false));

        VertexConsumer builder = bufferSource.getBuffer(RenderType.beaconBeam(texture, true));
        Matrix4f matrix = poseStack.last().pose();

        // Front
        vertex(builder, matrix, packedLight, color, -0.5f, -0.5f, 0.5f, minU, minV, 0, 0, 1);
        vertex(builder, matrix, packedLight, color, 0.5f, -0.5f, 0.5f, maxU, minV, 0, 0, 1);
        vertex(builder, matrix, packedLight, color, 0.5f, 0.5f, 0.5f, maxU, maxV, 0, 0, 1);
        vertex(builder, matrix, packedLight, color, -0.5f, 0.5f, 0.5f, minU, maxV, 0, 0, 1);
        // Back
        vertex(builder, matrix, packedLight, color, -0.5f, -0.5f, -0.5f, minU, minV, 0, 0, -1);
        vertex(builder, matrix, packedLight, color, -0.5f, 0.5f, -0.5f, minU, maxV, 0, 0, -1);
        vertex(builder, matrix, packedLight, color, 0.5f, 0.5f, -0.5f, maxU, maxV, 0, 0, -1);
        vertex(builder, matrix, packedLight, color, 0.5f, -0.5f, -0.5f, maxU, minV, 0, 0, -1);
        // Top
        vertex(builder, matrix, packedLight, color, -0.5f, 0.5f, 0.5f, minU, minV, 0, -1, 0);
        vertex(builder, matrix, packedLight, color, 0.5f, 0.5f, 0.5f, maxU, minV, 0, -1, 0);
        vertex(builder, matrix, packedLight, color, 0.5f, 0.5f, -0.5f, maxU, maxV, 0, -1, 0);
        vertex(builder, matrix, packedLight, color, -0.5f, 0.5f, -0.5f, minU, maxV, 0, -1, 0);
        // Bottom
        vertex(builder, matrix, packedLight, color, -0.5f, -0.5f, 0.5f, minU, minV, 0, 1, 0);
        vertex(builder, matrix, packedLight, color, -0.5f, -0.5f, -0.5f, minU, maxV, 0, 1, 0);
        vertex(builder, matrix, packedLight, color, 0.5f, -0.5f, -0.5f, maxU, maxV, 0, 1, 0);
        vertex(builder, matrix, packedLight, color, 0.5f, -0.5f, 0.5f, maxU, minV, 0, 1, 0);
        // Right
        vertex(builder, matrix, packedLight, color, 0.5f, -0.5f, 0.5f, minU, minV, 1, 0, 0);
        vertex(builder, matrix, packedLight, color, 0.5f, -0.5f, -0.5f, minU, maxV, 1, 0, 0);
        vertex(builder, matrix, packedLight, color, 0.5f, 0.5f, -0.5f, maxU, maxV, 1, 0, 0);
        vertex(builder, matrix, packedLight, color, 0.5f, 0.5f, 0.5f, maxU, minV, 1, 0, 0);
        // Left
        vertex(builder, matrix, packedLight, color, -0.5f, -0.5f, 0.5f, minU, minV, -1, 0, 0);
        vertex(builder, matrix, packedLight, color, -0.5f, 0.5f, 0.5f, maxU, minV, -1, 0, 0);
        vertex(builder, matrix, packedLight, color, -0.5f, 0.5f, -0.5f, maxU, maxV, -1, 0, 0);
        vertex(builder, matrix, packedLight, color, -0.5f, -0.5f, -0.5f, minU, maxV, -1, 0, 0);

        poseStack.popPose();
    }

    private static void vertex(
            VertexConsumer builder,
            Matrix4f matrix,
            int packedLight,
            int color,
            float x, float y, float z,
            float u, float v,
            int nx, int ny, int nz
    ) {
        builder.vertex(matrix, x, y, z)
               .color(
                       ((color >> 16) & 0xFF) / 255f,
                       ((color >> 8) & 0xFF) / 255f,
                       (color & 0xFF) / 255f,
                       ((color >> 24) & 0xFF) / 255f
               )
               .uv(u, v)
               .overlayCoords(OverlayTexture.NO_OVERLAY)
               .uv2(packedLight)
               .normal(nx, ny, nz)
               .endVertex();
    }
}
