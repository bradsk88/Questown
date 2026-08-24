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
    private static final ResourceLocation BOP_TEXTURE = Questown.ResourceLocation(
            "textures/blocks/block_of_progress.png");
    // The packedLight args below drive lighting only; this colour param tints the cube's vertices.
    private static final int WHITE = 0xFFFFFFFF;
    // A full flag tints its floating BOP red to warn that further BOPs are silently lost.
    private static final int FULL_BOP_RED = 0xFFFF0000;

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
        if (flag.getLevel() == null) {
            return;
        }
        float ageInTicks = flag.getLevel().getGameTime() + partialTick;

        if (flag.isDeedAvailable()) {
            poseStack.pushPose();
            // Centre of the block, floating a block above the flag.
            poseStack.translate(0.5D, 1.5D, 0.5D);
            SpinningCube.render(poseStack, buffer, packedLight, WHITE, DEED_TEXTURE, ageInTicks, 0.4f, 0.0f, 1.0f, 0.0f, 1.0f);
            poseStack.popPose();
        }
        if (flag.isBopFull()) {
            poseStack.pushPose();
            // Same floating position as the deed; the red tint marks a full flag.
            poseStack.translate(0.5D, 1.5D, 0.5D);
            SpinningCube.render(poseStack, buffer, packedLight, FULL_BOP_RED, BOP_TEXTURE, ageInTicks, 0.4f, 0.0f, 0.25f, 0.0f, 0.25f);
            poseStack.popPose();
        }
    }
}
