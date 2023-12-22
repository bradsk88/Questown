package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.jobs.IStatus;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

public class CoerceWalk extends Behavior<VisitorMobEntity> {

    public CoerceWalk(
    ) {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.DISABLE_WALK_TO_ADMIRE_ITEM, MemoryStatus.VALUE_ABSENT
        ));
    }

    @Override
    protected boolean checkExtraStartConditions(
            ServerLevel p_22538_,
            VisitorMobEntity p_22539_
    ) {
        if (!super.checkExtraStartConditions(p_22538_, p_22539_)) {
            return false;
        }
        IStatus<?> s = p_22539_.getStatusForServer();
        return s != null && s.isAllowedToTakeBreaks();
    }

    @Override
    protected void start(
            ServerLevel lvl,
            VisitorMobEntity e,
            long p_23884_
    ) {
        e.getBrain().setMemory(MemoryModuleType.DISABLE_WALK_TO_ADMIRE_ITEM, false);
    }

}
