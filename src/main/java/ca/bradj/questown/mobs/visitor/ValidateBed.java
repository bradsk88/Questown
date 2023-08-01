package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.Questown;
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
    protected boolean canStillUse(
            ServerLevel p_22545_,
            VisitorMobEntity p_22546_,
            long p_22547_
    ) {
        return !p_22546_.isSleeping();
    }

    @Override
    protected boolean checkExtraStartConditions(
            ServerLevel p_22538_,
            VisitorMobEntity p_22539_
    ) {
        if (!super.checkExtraStartConditions(p_22538_, p_22539_)) {
            return false;
        }
        return !p_22539_.isSleeping();
    }

    @Override
    protected void start(
            ServerLevel level,
            VisitorMobEntity entity,
            long p_22542_
    ) {
        super.start(level, entity, p_22542_);
        if (entity.isSleeping()) {
            Questown.LOGGER.warn("Entity is validating home while sleeping. This is a bug and will cause them to not sleep.");
        }

        Optional<GlobalPos> home = entity.getBrain().getMemory(MemoryModuleType.HOME);
        BlockPos bp = home.get().pos();
        BlockState bs = level.getBlockState(bp);
        Optional<Boolean> occupied = bs.getOptionalValue(BedBlock.OCCUPIED);
        if (occupied.isPresent()) {
            if (occupied.get()) {
                entity.getBrain().eraseMemory(MemoryModuleType.HOME);
                Questown.LOGGER.debug("{} has abandoned home at {} because it's occupied", entity.getUUID(), bp);
            }
        }
    }
}
