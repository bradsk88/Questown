package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.Questown;
import ca.bradj.questown.jobs.GathererJournal;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;

public class TownWalk extends Behavior<VisitorMobEntity> {
    private static final int REPEAT_BUFFER = 20;
    private static final int PAUSE_TICKS = 100;

    final float speedModifier;
    private long nextUpdate;
    private BlockPos target;

    public TownWalk(
            float speedModifier
    ) {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.DISABLE_WALK_TO_ADMIRE_ITEM, MemoryStatus.VALUE_PRESENT
        ));
        this.speedModifier = speedModifier;
    }

    @Override
    protected boolean checkExtraStartConditions(
            ServerLevel p_23879_,
            VisitorMobEntity e
    ) {
        if (e.getBrain().getMemory(MemoryModuleType.WALK_TARGET).isPresent()) {
            return false;
        }
        if (e.getBrain().getMemory(MemoryModuleType.DISABLE_WALK_TO_ADMIRE_ITEM).orElse(false)) {
            return false;
        }
        if (p_23879_.getGameTime() - this.nextUpdate < REPEAT_BUFFER) {
            return false;
        }
        this.target = e.newWanderTarget();
        if (target == null) {
            return false;
        }
        return true;
    }

    @Override
    protected void start(
            ServerLevel lvl,
            VisitorMobEntity e,
            long p_23884_
    ) {
        this.nextUpdate = lvl.getGameTime() + (long) lvl.getRandom().nextInt(REPEAT_BUFFER);
        BlockPos bp = this.target;
        int dist = 1;
        if (e.getStatus() == GathererJournal.Status.GATHERING) {
            dist = 0;
        }
        e.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(bp, this.speedModifier, dist));
        e.getBrain().eraseMemory(MemoryModuleType.DISABLE_WALK_TO_ADMIRE_ITEM);
        Questown.LOGGER.trace("{} navigating to {}", e.getUUID(), bp);
    }
}
