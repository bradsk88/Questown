package ca.bradj.questown.blocks.entity.renderer;

import ca.bradj.questown.blocks.entity.PlateBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

public class PlateBlockEntityRenderer implements BlockEntityRenderer<PlateBlockEntity> {

    public PlateBlockEntityRenderer(
            BlockEntityRendererProvider.Context context
    ) {

    }

    @Override
    public void render(
            PlateBlockEntity pbe,
            float pPartialTick,
            PoseStack stack,
            MultiBufferSource buf,
            int p_112311_,
            int p_112312_
    ) {
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();

        ItemStack itemStack = pbe.getFood();

        stack.pushPose();
        stack.translate(0.5f, 0, 0.5f);
        stack.scale(0.375f, 0.375f, 0.375f);
        stack.mulPose(Vector3f.XP.rotationDegrees(90));

        switch (pbe.getBlockState().getValue(HorizontalDirectionalBlock.FACING)) {
            case NORTH -> stack.mulPose(Vector3f.ZP.rotationDegrees(0));
            case EAST -> stack.mulPose(Vector3f.ZP.rotationDegrees(90));
            case SOUTH -> stack.mulPose(Vector3f.ZP.rotationDegrees(180));
            case WEST -> stack.mulPose(Vector3f.ZP.rotationDegrees(270));
        }

        stack.mulPose(Vector3f.XP.rotationDegrees(-30));
        stack.translate(0, 0, -0.5f);

        int ll = getLightLevel(pbe.getLevel(), pbe.getBlockPos());

        itemRenderer.renderStatic(
                itemStack, ItemTransforms.TransformType.GUI, ll,
                OverlayTexture.NO_OVERLAY, stack, buf, 1
        );
        stack.popPose();

    }

    private int getLightLevel(
            Level level,
            BlockPos blockPos
    ) {
        int bLight = level.getBrightness(LightLayer.BLOCK, blockPos);
        int sLight = level.getBrightness(LightLayer.SKY, blockPos);
        return LightTexture.pack(bLight, sLight);
    }
}
