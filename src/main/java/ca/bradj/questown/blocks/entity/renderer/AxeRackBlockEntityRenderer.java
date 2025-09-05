package ca.bradj.questown.blocks.entity.renderer;

import ca.bradj.questown.blocks.entity.AxeRackBlockEntity;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import com.mojang.blaze3d.vertex.PoseStack;
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

public class AxeRackBlockEntityRenderer implements BlockEntityRenderer<AxeRackBlockEntity> {

    public AxeRackBlockEntityRenderer(
            BlockEntityRendererProvider.Context context
    ) {

    }

    @Override
    public void render(
            AxeRackBlockEntity pbe,
            float pPartialTick,
            PoseStack stack,
            MultiBufferSource buf,
            int p_112311_,
            int p_112312_
    ) {
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();

        MCTownItem items = pbe.getItem();

        if (items.isEmpty()) {
            return;
        }
        renderFood(pbe, stack, buf, itemRenderer, items.toMCItemStack(), false, false);
    }

    private void renderFood(
            AxeRackBlockEntity pbe,
            PoseStack stack,
            MultiBufferSource buf,
            ItemRenderer itemRenderer,
            ItemStack itemStack,
            boolean isLeft,
            boolean isTop
    ) {
        stack.pushPose();
        // TODO: Make it directional
//        Direction value = pbe.getBlockState().getValue(HorizontalDirectionalBlock.FACING);

//        stack.mulPose(Vector3f.XP.rotationDegrees(-30));
        stack.translate(0.5f, 0.5f, 0.5f);

        int ll = getLightLevel(pbe.getLevel(), pbe.getBlockPos());

        itemRenderer.renderStatic(
                itemStack,
                ItemTransforms.TransformType.GUI,
                ll,
                OverlayTexture.NO_OVERLAY,
                stack,
                buf,
                1
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
