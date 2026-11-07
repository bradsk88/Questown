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

/** Draws the stone chicken statue: the chicken model in stone, sized to its block. */
public class ChickenStatueBlockEntityRenderer
        implements BlockEntityRenderer<ChickenStatueBlockEntity> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("minecraft", "textures/block/stone.png");
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
        // Entity models are upside down; flip and lift so the chicken stands on the block.
        poseStack.translate(0.5D, 1.501D * SCALE, 0.5D);
        poseStack.scale(-SCALE, -SCALE, SCALE);
        RenderType renderType = this.model.renderType(TEXTURE);
        this.model.renderToBuffer(
                poseStack,
                bufferSource.getBuffer(renderType),
                packedLight,
                packedOverlay,
                1.0F, 1.0F, 1.0F, 1.0F
        );
        poseStack.popPose();
    }
}
