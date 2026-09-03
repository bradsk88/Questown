package ca.bradj.questown.blocks.entity.renderer;

import ca.bradj.questown.blocks.entity.PlateBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
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
        stack.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(90), new Vector3f(1, 0, 0)));

        switch (pbe.getBlockState().getValue(HorizontalDirectionalBlock.FACING)) {
            case NORTH -> stack.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(0), new Vector3f(0, 0, 1)));
            case EAST -> stack.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(90), new Vector3f(0, 0, 1)));
            case SOUTH -> stack.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(180), new Vector3f(0, 0, 1)));
            case WEST -> stack.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(270), new Vector3f(0, 0, 1)));
        }

        stack.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(-30), new Vector3f(1, 0, 0)));
        stack.translate(0, 0, -0.5f);

        int ll = getLightLevel(pbe.getLevel(), pbe.getBlockPos());

        itemRenderer.renderStatic(
                itemStack, ItemDisplayContext.GUI, ll,
                OverlayTexture.NO_OVERLAY, stack, buf, pbe.getLevel(), 1
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
