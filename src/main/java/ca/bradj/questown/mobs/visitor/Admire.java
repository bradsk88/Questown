package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.jobs.IStatus;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

public class Admire extends Behavior<VisitorMobEntity> {

    private final int maxAdmireTicks;
    private int admireTicks;
    private BlockPos look;

    public Admire(
            int maxAdmireTicks
    ) {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.DISABLE_WALK_TO_ADMIRE_ITEM, MemoryStatus.VALUE_ABSENT
        ));
        this.maxAdmireTicks = maxAdmireTicks;
    }

    @Override
    protected boolean checkExtraStartConditions(
            ServerLevel p_22538_,
            VisitorMobEntity entity
    ) {
        if (!super.checkExtraStartConditions(p_22538_, entity)) {
            return false;
        }
        IStatus<?> s = entity.getStatusForServer();
        return !s.isAllowedToTakeBreaks();
    }

    @Override
    protected void start(
            ServerLevel lvl,
            VisitorMobEntity e,
            long p_23884_
    ) {
        e.getBrain().setMemory(MemoryModuleType.DISABLE_WALK_TO_ADMIRE_ITEM, true);
        BlockPos ref = e.getOnPos();
        if (lvl.getRandom().nextBoolean()) {
            ref = e.blockPosition().above();
        }
        this.look = ref.relative(e.getDirection(), 4);
        if (lvl.getRandom().nextBoolean()) {
            this.look = look.relative(e.getDirection().getClockWise(), 4);
        } else if (lvl.getRandom().nextBoolean()) {
            this.look = look.relative(e.getDirection().getCounterClockWise(), 4);
        }
    }

    @Override
    protected void stop(
            ServerLevel p_22548_,
            VisitorMobEntity p_22549_,
            long p_22550_
    ) {
        super.stop(p_22548_, p_22549_, p_22550_);
        p_22549_.getBrain().setMemory(MemoryModuleType.DISABLE_WALK_TO_ADMIRE_ITEM, false);
        this.admireTicks = 0;
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
    protected boolean timedOut(long p_22537_) {
        return this.admireTicks > this.maxAdmireTicks;
    }

    @Override
    protected void tick(
            ServerLevel p_22551_,
            VisitorMobEntity e,
            long p_22553_
    ) {
        super.tick(p_22551_, e, p_22553_);
        if (e.getBrain().getMemory(MemoryModuleType.WALK_TARGET).isPresent()) {
            e.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        }
        e.getLookControl().setLookAt(look.getX(), look.getY(), look.getZ());
        this.admireTicks++;
    }
}
