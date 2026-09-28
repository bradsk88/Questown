package ca.bradj.questown.blocks.entity.renderer;

import ca.bradj.questown.blocks.entity.ChickenStatueBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.ChickenModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Chicken;

/**
 * Renders the helper-chicken onboarding trophy: the vanilla chicken model
 * coloured stone and scaled to sit inside the statue's block, so the
 * finished chicken reads as a pickable stone statue rather than a stone
 * cube. The block's own model is empty; this layer draws over it.
 */public class ChickenStatueBlockEntityRenderer
        implements BlockEntityRenderer<ChickenStatueBlockEntity> {

    // The chicken's own texture is not registered as a model layer in 1.19.2, so
    // bakeLayer(...) fails; bake the geometry straight from the static layer
    // builder and colour it with our stone texture at render time via renderType.
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("minecraft", "textures/blocks/stone.png");
    // A chicken is ~1.25 blocks tall; scale it down to sit inside the cube.
    private static final float SCALE = 0.8F;
    private final ChickenModel<Chicken> model;

    public ChickenStatueBlockEntityRenderer(
            BlockEntityRendererProvider.Context context
    ) {
        this.model = new ChickenModel<>(ChickenModel.createBodyLayer().bakeRoot());
    }

    @Override
    public void render(
            ChickenStatueBlockEntity blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.scale(SCALE, SCALE, SCALE);
        RenderType renderType = this.model.renderType(TEXTURE);
        this.model.renderToBuffer(
                poseStack,
                bufferSource.getBuffer(renderType),
                packedLight,
                0,
                1.0F, 1.0F, 1.0F, 1.0F
        );
        poseStack.popPose();
    }
}
