package ca.bradj.questown.mobs.visitor;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

public class VisitorMobRenderer extends HumanoidMobRenderer<VisitorMobEntity, PlayerModel<VisitorMobEntity>> {

    public VisitorMobRenderer(
            EntityRendererProvider.Context ctx
    ) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(VisitorMobEntity entity) {
        return DefaultPlayerSkin.getDefaultSkin(entity.getUUID());
    }

    @Override
    public void render(
            VisitorMobEntity entity,
            float yaw,
            float pTicks,
            PoseStack stack,
            MultiBufferSource buffer,
            int light
    ) {
        if (entity.isSitting()) {
            this.renderSiting(entity, yaw, pTicks, stack, buffer, light);
            return;
        }
        super.render(entity, yaw, pTicks, stack, buffer, light);
    }

    private void renderSiting(
            VisitorMobEntity entity,
            float entityYaw,
            float partialTicks,
            PoseStack matrixStack,
            MultiBufferSource buffer,
            int packedLight
    ) {
        super.render(entity, entityYaw, partialTicks, matrixStack, buffer, packedLight);

        // Sit the player down
        PlayerModel<VisitorMobEntity> model = this.getModel();
        model.setAllVisible(false);
        model.head.visible = true;
        model.hat.visible = true;
        model.body.visible = true;
        model.rightArm.visible = true;
        model.leftArm.visible = true;
        model.rightLeg.visible = true;
        model.leftLeg.visible = true;
        model.body.xRot = 1.4f;
        model.body.y = 10.0f;
        model.rightArm.xRot = -0.5f;
        model.rightArm.yRot = -0.1f;
        model.rightArm.zRot = -1.5f;
        model.rightArm.y = 11.0f;
        model.leftArm.xRot = -0.5f;
        model.leftArm.yRot = 0.1f;
        model.leftArm.zRot = 1.5f;
        model.leftArm.y = 11.0f;
        model.rightLeg.xRot = -1.5f;
        model.rightLeg.y = 18.0f;
        model.leftLeg.xRot = -1.5f;
        model.leftLeg.y = 18.0f;
    }
}
