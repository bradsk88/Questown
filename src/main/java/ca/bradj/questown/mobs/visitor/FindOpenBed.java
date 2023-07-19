package ca.bradj.questown.mobs.visitor;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;
import java.util.Optional;

public class FindOpenBed extends Behavior<VisitorMobEntity> {
    public FindOpenBed() {
        super(ImmutableMap.of(
                MemoryModuleType.HOME,
                MemoryStatus.VALUE_ABSENT
        ));
    }

    @Override
    protected void start(
            ServerLevel p_22540_,
            VisitorMobEntity entity,
            long p_22542_
    ) {
        super.start(p_22540_, entity, p_22542_);
        Collection<BlockPos> bss = entity.town.findMatchedRecipeBlocks(
                i -> i instanceof BedBlock
        );
        for (BlockPos bp : bss) {
            BlockState bs = p_22540_.getBlockState(bp);
            if (bs.getBlock() instanceof BedBlock) {
                Optional<Boolean> occupied = bs.getOptionalValue(BedBlock.OCCUPIED);
                if (occupied.isPresent()) {
                    if (!occupied.get()) {
                        entity.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(p_22540_.dimension(), bp));
                        entity.setWanderTarget(bp);
                    }
                }
            }
        }
    }
}
