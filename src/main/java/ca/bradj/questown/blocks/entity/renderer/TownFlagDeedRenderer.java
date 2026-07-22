package ca.bradj.questown.blocks.entity.renderer;

import ca.bradj.questown.Questown;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders the relocation deed spinning above a dormant flag while a deed is available to collect
 * (ADR-0009). Reuses the block-of-progress spin render ({@link SpinningCube}) with the deed's texture,
 * so the player sees the deed and picks it up by interacting with the flag rather than hunting for a
 * dropped item. Draws nothing unless {@link TownFlagBlockEntity#isDeedAvailable()}.
 */
public class TownFlagDeedRenderer implements BlockEntityRenderer<TownFlagBlockEntity> {

    private static final ResourceLocation DEED_TEXTURE = Questown.ResourceLocation(
            "textures/items/relocation_deed.png");

    public TownFlagDeedRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
            TownFlagBlockEntity flag,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay
    ) {
        if (!flag.isDeedAvailable() || flag.getLevel() == null) {
            return;
        }
        float ageInTicks = flag.getLevel().getGameTime() + partialTick;

        poseStack.pushPose();
        // Centre of the block, floating a block above the flag.
        poseStack.translate(0.5D, 1.5D, 0.5D);
        SpinningCube.render(poseStack, buffer, 0xF000F0, DEED_TEXTURE, ageInTicks, 0.4f, 0.0f, 1.0f, 0.0f, 1.0f);
        poseStack.popPose();
    }
}
