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

public class ValidateBed extends Behavior<VisitorMobEntity> {
    public ValidateBed() {
        super(ImmutableMap.of(
                MemoryModuleType.HOME,
                MemoryStatus.VALUE_PRESENT
        ));
    }

    @Override
    protected void start(
            ServerLevel level,
            VisitorMobEntity entity,
            long p_22542_
    ) {
        super.start(level, entity, p_22542_);
        Optional<GlobalPos> home = entity.getBrain().getMemory(MemoryModuleType.HOME);
        BlockState bs = level.getBlockState(home.get().pos());
        Optional<Boolean> occupied = bs.getOptionalValue(BedBlock.OCCUPIED);
        if (occupied.isPresent()) {
            if (occupied.get()) {
                entity.getBrain().eraseMemory(MemoryModuleType.HOME);
            }
        }
    }
}
