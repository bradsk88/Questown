package ca.bradj.questown.mobs.visitor;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;

import java.util.Optional;

public class LookAtWalkTarget extends Behavior<VisitorMobEntity> {

    private BlockPos look;

    public LookAtWalkTarget(
    ) {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_PRESENT
        ));
    }

    @Override
    protected void start(
            ServerLevel lvl,
            VisitorMobEntity e,
            long p_23884_
    ) {
        Optional<WalkTarget> target = e.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET);
        if (target.isEmpty()) {
            return;
        }
        this.look = target.get().getTarget().currentBlockPosition();
    }

    @Override
    protected boolean canStillUse(
            ServerLevel p_22545_,
            VisitorMobEntity p_22546_,
            long p_22547_
    ) {
        return p_22546_.getBrain().getMemory(MemoryModuleType.WALK_TARGET).isEmpty();
    }

    @Override
    protected void tick(
            ServerLevel p_22551_,
            VisitorMobEntity e,
            long p_22553_
    ) {
        super.tick(p_22551_, e, p_22553_);
        e.getLookControl().setLookAt(look.getX(), look.getY(), look.getZ());
    }
}
