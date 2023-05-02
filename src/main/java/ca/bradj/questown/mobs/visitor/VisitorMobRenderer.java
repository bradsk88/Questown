package ca.bradj.questown.mobs.visitor;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
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
}
