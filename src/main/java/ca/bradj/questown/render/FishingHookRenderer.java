package ca.bradj.questown.render;

import ca.bradj.questown._vanilla.entities.FishingHook;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class FishingHookRenderer extends EntityRenderer<FishingHook> {
    private static final ResourceLocation TEXTURE_LOCATION = new ResourceLocation("textures/entity/fishing_hook.png");
    private static final RenderType RENDER_TYPE = RenderType.entityCutout(TEXTURE_LOCATION);
    private static final double VIEW_BOBBING_SCALE = 960.0D;

    public FishingHookRenderer(EntityRendererProvider.Context p_174117_) {
        super(p_174117_);
    }

    public void render(FishingHook fishingHook, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        LivingEntity player = fishingHook.getPlayerOwner();
        BlockPos attachPoint = fishingHook.getMountPos();
        if (player != null && attachPoint != null) {
            poseStack.pushPose();
            poseStack.pushPose();
            poseStack.scale(0.5F, 0.5F, 0.5F);
            poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
            poseStack.mulPose(Vector3f.YP.rotationDegrees(180.0F));
            PoseStack.Pose lastPose = poseStack.last();
            Matrix4f poseMatrix = lastPose.pose();
            Matrix3f normalMatrix = lastPose.normal();
            VertexConsumer quadConsumer = bufferSource.getBuffer(RENDER_TYPE);
            vertex(quadConsumer, poseMatrix, normalMatrix, packedLight, 0.0F, 0, 0, 1);
            vertex(quadConsumer, poseMatrix, normalMatrix, packedLight, 1.0F, 0, 1, 1);
            vertex(quadConsumer, poseMatrix, normalMatrix, packedLight, 1.0F, 1, 1, 0);
            vertex(quadConsumer, poseMatrix, normalMatrix, packedLight, 0.0F, 1, 0, 0);
            poseStack.popPose();

            // Attach string to attachPoint instead of player
            double attachX = attachPoint.getX() + 0.5D;
            double attachY = attachPoint.getY() + 0.5D;
            double attachZ = attachPoint.getZ() + 0.5D;

            double hookX = Mth.lerp((double)partialTicks, fishingHook.xo, fishingHook.getX());
            double hookY = Mth.lerp((double)partialTicks, fishingHook.yo, fishingHook.getY()) + 0.25D;
            double hookZ = Mth.lerp((double)partialTicks, fishingHook.zo, fishingHook.getZ());

            float deltaX = (float)(attachX - hookX);
            float deltaY = (float)(attachY - hookY);
            float deltaZ = (float)(attachZ - hookZ);

            VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lineStrip());
            PoseStack.Pose linePose = poseStack.last();
            int segments = 16;

            for (int seg = 0; seg <= segments; ++seg) {
                stringVertex(deltaX, deltaY, deltaZ, lineConsumer, linePose, fraction(seg, segments), fraction(seg + 1, segments));
            }

            poseStack.popPose();
            super.render(fishingHook, entityYaw, partialTicks, poseStack, bufferSource, packedLight);
        }
    }

    private static float fraction(int numerator, int denominator) {
        return (float)numerator / (float)denominator;
    }

    private static void vertex(VertexConsumer consumer, Matrix4f poseMatrix, Matrix3f normalMatrix, int packedLight, float u, int v, int texU, int texV) {
        consumer.vertex(poseMatrix, u - 0.5F, (float)v - 0.5F, 0.0F)
            .color(255, 255, 255, 255)
            .uv((float)texU, (float)texV)
            .overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(packedLight)
            .normal(normalMatrix, 0.0F, 1.0F, 0.0F)
            .endVertex();
    }

    private static void stringVertex(float deltaX, float deltaY, float deltaZ, VertexConsumer consumer, PoseStack.Pose pose, float t0, float t1) {
        float x0 = deltaX * t0;
        float y0 = deltaY * (t0 * t0 + t0) * 0.5F + 0.25F;
        float z0 = deltaZ * t0;
        float x1 = deltaX * t1 - x0;
        float y1 = deltaY * (t1 * t1 + t1) * 0.5F + 0.25F - y0;
        float z1 = deltaZ * t1 - z0;
        float length = Mth.sqrt(x1 * x1 + y1 * y1 + z1 * z1);
        x1 /= length;
        y1 /= length;
        z1 /= length;
        consumer.vertex(pose.pose(), x0, y0, z0)
            .color(0, 0, 0, 255)
            .normal(pose.normal(), x1, y1, z1)
            .endVertex();
    }

    public ResourceLocation getTextureLocation(FishingHook fishingHook) {
        return TEXTURE_LOCATION;
    }
}
