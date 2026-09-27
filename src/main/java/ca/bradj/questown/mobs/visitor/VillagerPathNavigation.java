package ca.bradj.questown.mobs.visitor;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.tags.ITag;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("NullableProblems")
public class VillagerPathNavigation extends GroundPathNavigation {
    public VillagerPathNavigation(
            VisitorMobEntity visitorMobEntity,
            Level p21480
    ) {
        super(visitorMobEntity, p21480);
        setCanOpenDoors(true);
        setCanPassDoors(true);
    }

    @Override
    protected PathFinder createPathFinder(int p_26453_) {
        this.nodeEvaluator = new WalkNodeEvaluator() {

            @Override
            public BlockPathTypes getBlockPathType(
                    BlockGetter getr,
                    int x,
                    int y,
                    int z
            ) {
                BlockPos pos = new BlockPos(x, y, z);
                // The town's campfire is furniture, not a route: vanilla classifies a lit
                // campfire as DANGER_FIRE (walkable, cost 8), so a townie whose path crosses
                // it burns to death in about a second (CampfireBlock.entityInside deals 1
                // fire damage per tick with no armor and no burn time). Hard-block it so no
                // townie path ever routes through a lit campfire.
                if (CampfireBlock.isLitCampfire(getr.getBlockState(pos))) {
                    return BlockPathTypes.BLOCKED;
                }
                FluidState fs = getr.getFluidState(pos);
                if (!fs.isEmpty()) {
                    BlockState above = getr.getBlockState(pos.above());
                    if (above.isAir()) {
                        BlockState roof = getr.getBlockState(pos.above()
                                                                .above());
                        if (!roof.isAir()) {
                            return BlockPathTypes.BLOCKED;
                        }
                    }
                }

                TagKey<Block> fenceGates = BlockTags.FENCE_GATES;
                ITag<Block> gates = getTag(fenceGates);
                if (gates.contains(getr.getBlockState(pos).getBlock())) {
                    return BlockPathTypes.DOOR_WOOD_CLOSED;
                }

                ITag<Block> fences = getTag(BlockTags.FENCES);
                if (fences.contains(getr.getBlockState(pos).getBlock())) {
                    return BlockPathTypes.BLOCKED;
                }
                if (fences.contains(getr.getBlockState(pos.below()).getBlock())) {
                    return BlockPathTypes.BLOCKED;
                }

                return super.getBlockPathType(getr, x, y, z);
            }

            @Override
            protected BlockPathTypes evaluateBlockPathType(
                    BlockGetter getr,
                    BlockPos pos,
                    BlockPathTypes defaultType
            ) {
                defaultType = super.evaluateBlockPathType(getr, pos, defaultType);

                if (CampfireBlock.isLitCampfire(getr.getBlockState(pos))) {
                    return BlockPathTypes.BLOCKED;
                }

                if (defaultType == BlockPathTypes.FENCE && (getr.getBlockState(pos)
                                                                .getBlock() instanceof FenceGateBlock)) {
                    return BlockPathTypes.DOOR_OPEN;
                }

                ITag<Block> fences = getTag(BlockTags.FENCES);
                if (fences.contains(getr.getBlockState(pos).getBlock())) {
                    return BlockPathTypes.BLOCKED;
                }
                if (fences.contains(getr.getBlockState(pos.below()).getBlock())) {
                    return BlockPathTypes.BLOCKED;
                }

                return defaultType;
            }
        };
        this.nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(this.nodeEvaluator, p_26453_);
    }

    @SuppressWarnings("DataFlowIssue")
    private static @NotNull ITag<Block> getTag(TagKey<Block> fenceGates) {
        return ForgeRegistries.BLOCKS.tags().getTag(fenceGates);
    }
}
