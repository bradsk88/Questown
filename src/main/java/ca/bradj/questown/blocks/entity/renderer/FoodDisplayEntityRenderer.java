package ca.bradj.questown.blocks.entity.renderer;

import ca.bradj.questown.blocks.entity.FoodDisplayEntity;
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
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

import java.util.Iterator;

public class FoodDisplayEntityRenderer implements BlockEntityRenderer<FoodDisplayEntity> {

    public FoodDisplayEntityRenderer(
            BlockEntityRendererProvider.Context context
    ) {

    }

    @Override
    public void render(
            FoodDisplayEntity pbe,
            float pPartialTick,
            PoseStack stack,
            MultiBufferSource buf,
            int p_112311_,
            int p_112312_
    ) {
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();

        Iterator<ItemStack> items = pbe.getItems().iterator();

        if (items.hasNext()) {
            renderFood(pbe, stack, buf, itemRenderer, items.next(), false, false);
        }
        if (items.hasNext()) {
            renderFood(pbe, stack, buf, itemRenderer, items.next(), true, false);
        }
        if (items.hasNext()) {
            renderFood(pbe, stack, buf, itemRenderer, items.next(), false, true);
        }
        if (items.hasNext()) {
            renderFood(pbe, stack, buf, itemRenderer, items.next(), true, true);
        }

    }

    private void renderFood(
            FoodDisplayEntity pbe,
            PoseStack stack,
            MultiBufferSource buf,
            ItemRenderer itemRenderer,
            ItemStack itemStack,
            boolean isLeft,
            boolean isTop
    ) {
        stack.pushPose();
        Direction value = pbe.getBlockState().getValue(HorizontalDirectionalBlock.FACING);

        float leftFromRight = isLeft ? 0.25f : 0.75f;
        float inFromBack = 0.5f;
        float upFromBottom = isTop ? 0.5f : 1f / 16f;

        if (value == Direction.EAST || value == Direction.WEST) {
            float temp = leftFromRight;
            leftFromRight = inFromBack;
            inFromBack = temp;
        }
        stack.translate(leftFromRight, upFromBottom, inFromBack);
        stack.scale(0.375f, 0.375f, 0.375f);
        stack.mulPose(Vector3f.XP.rotationDegrees(90));

        switch (value) {
            case NORTH -> stack.mulPose(Vector3f.ZP.rotationDegrees(0));
            case EAST -> stack.mulPose(Vector3f.ZP.rotationDegrees(90));
            case SOUTH -> stack.mulPose(Vector3f.ZP.rotationDegrees(180));
            case WEST -> stack.mulPose(Vector3f.ZP.rotationDegrees(270));
        }

        stack.mulPose(Vector3f.XP.rotationDegrees(-30));
        stack.translate(0, 0, -0.5f);

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
