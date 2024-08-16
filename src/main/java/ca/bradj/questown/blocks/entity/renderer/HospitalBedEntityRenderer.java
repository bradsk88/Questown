package ca.bradj.questown.blocks.entity.renderer;

import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.HospitalBedBlock;
import ca.bradj.questown.blocks.entity.HospitalBedBlockEntity;
import ca.bradj.questown.core.init.TilesInit;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Vector3f;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BrightnessCombiner;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoubleBlockCombiner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.ForgeHooksClient;

@OnlyIn(Dist.CLIENT)
public class HospitalBedEntityRenderer implements BlockEntityRenderer<HospitalBedBlockEntity> {
    private static final Material MATERIAL;

    static {
        ResourceLocation TEXTURE = Questown.ResourceLocation("blocks/hospital_bed");
        MATERIAL = ForgeHooksClient.getBlockMaterial(TEXTURE);
    }

    private final ModelPart headRoot;
    private final ModelPart footRoot;

    public HospitalBedEntityRenderer(BlockEntityRendererProvider.Context p_173540_) {
        this.headRoot = p_173540_.bakeLayer(ModelLayers.BED_HEAD);
        this.footRoot = p_173540_.bakeLayer(ModelLayers.BED_FOOT);
    }

    public static LayerDefinition createHeadLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();
        partdefinition.addOrReplaceChild(
                "main",
                CubeListBuilder.create().texOffs(0, 0).addBox(0.0F, 0.0F, 0.0F, 16.0F, 16.0F, 6.0F),
                PartPose.ZERO
        );
        partdefinition.addOrReplaceChild(
                "left_leg",
                CubeListBuilder.create().texOffs(50, 6).addBox(0.0F, 6.0F, 0.0F, 3.0F, 3.0F, 3.0F),
                PartPose.rotation(((float) Math.PI / 2F), 0.0F, ((float) Math.PI / 2F))
        );
        partdefinition.addOrReplaceChild(
                "right_leg",
                CubeListBuilder.create().texOffs(50, 18).addBox(-16.0F, 6.0F, 0.0F, 3.0F, 3.0F, 3.0F),
                PartPose.rotation(((float) Math.PI / 2F), 0.0F, (float) Math.PI)
        );
        return LayerDefinition.create(meshdefinition, 64, 64);
    }

    public static LayerDefinition createFootLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();
        partdefinition.addOrReplaceChild(
                "main",
                CubeListBuilder.create().texOffs(0, 22).addBox(0.0F, 0.0F, 0.0F, 16.0F, 16.0F, 6.0F),
                PartPose.ZERO
        );
        partdefinition.addOrReplaceChild(
                "left_leg",
                CubeListBuilder.create().texOffs(50, 0).addBox(0.0F, 6.0F, -16.0F, 3.0F, 3.0F, 3.0F),
                PartPose.rotation(((float) Math.PI / 2F), 0.0F, 0.0F)
        );
        partdefinition.addOrReplaceChild(
                "right_leg",
                CubeListBuilder.create().texOffs(50, 12).addBox(-16.0F, 6.0F, -16.0F, 3.0F, 3.0F, 3.0F),
                PartPose.rotation(((float) Math.PI / 2F), 0.0F, ((float) Math.PI * 1.5F))
        );
        return LayerDefinition.create(meshdefinition, 64, 64);
    }

    public void render(
            HospitalBedBlockEntity p_112205_,
            float p_112206_,
            PoseStack p_112207_,
            MultiBufferSource p_112208_,
            int p_112209_,
            int p_112210_
    ) {
        Material material = MATERIAL;
        Level level = p_112205_.getLevel();
        if (level != null) {
            BlockState blockstate = p_112205_.getBlockState();
            DoubleBlockCombiner.NeighborCombineResult<? extends HospitalBedBlockEntity> neighborcombineresult = DoubleBlockCombiner.combineWithNeigbour(
                    TilesInit.HOSPITAL_BED.get(),
                    HospitalBedBlock::getBlockType,
                    HospitalBedBlock::getConnectedDirection,
                    ChestBlock.FACING,
                    blockstate,
                    level,
                    p_112205_.getBlockPos(),
                    (p_112202_, p_112203_) -> {
                        return false;
                    }
            );
            int i = neighborcombineresult.<Int2IntFunction>apply(new BrightnessCombiner<>()).get(p_112209_);
            this.renderPiece(
                    p_112207_,
                    p_112208_,
                    blockstate.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD ? this.headRoot : this.footRoot,
                    blockstate.getValue(HospitalBedBlock.FACING),
                    material,
                    i,
                    p_112210_,
                    false
            );
        } else {
            this.renderPiece(
                    p_112207_,
                    p_112208_,
                    this.headRoot,
                    Direction.SOUTH,
                    material,
                    p_112209_,
                    p_112210_,
                    false
            );
            this.renderPiece(
                    p_112207_,
                    p_112208_,
                    this.footRoot,
                    Direction.SOUTH,
                    material,
                    p_112209_,
                    p_112210_,
                    true
            );
        }

    }

    private void renderPiece(
            PoseStack stack,
            MultiBufferSource buffer,
            ModelPart p_173544_,
            Direction p_173545_,
            Material material,
            int p_173547_,
            int p_173548_,
            boolean p_173549_
    ) {
        stack.pushPose();
        stack.translate(0.0D, 0.5625D, p_173549_ ? -1.0D : 0.0D);
        stack.mulPose(Vector3f.XP.rotationDegrees(90.0F));
        stack.translate(0.5D, 0.5D, 0.5D);
        stack.mulPose(Vector3f.ZP.rotationDegrees(180.0F + p_173545_.toYRot()));
        stack.translate(-0.5D, -0.5D, -0.5D);
        VertexConsumer vertexconsumer = material.buffer(buffer, RenderType::entitySolid);
        p_173544_.render(stack, vertexconsumer, p_173547_, p_173548_);
        stack.popPose();
    }
}