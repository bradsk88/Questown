package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.QT;
import ca.bradj.questown.jobs.GathererJournal;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public class TownWalk extends Behavior<VisitorMobEntity> {
    private static final int REPEAT_BUFFER = 20;
    private static final int PAUSE_TICKS = 100;

    final float walkSpeed;
    private long nextUpdate;
    private BlockPos target;

    public TownWalk(
            float speedModifier
    ) {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.DISABLE_WALK_TO_ADMIRE_ITEM, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryStatus.REGISTERED
        ));
        this.walkSpeed = speedModifier;
    }

    @Override
    protected boolean checkExtraStartConditions(
            ServerLevel level,
            VisitorMobEntity e
    ) {
        if (e.isTickFrozen()) {
            return false;
        }

        if (e.getBrain().getMemory(MemoryModuleType.WALK_TARGET).isPresent()) {
            return false;
        }
        if (e.getBrain().getMemory(MemoryModuleType.DISABLE_WALK_TO_ADMIRE_ITEM).orElse(false)) {
            return false;
        }
        if (level.getGameTime() - this.nextUpdate < REPEAT_BUFFER) {
            return false;
        }
        if (e.shouldStandStill()) {
            return false;
        }
        this.target = e.newWanderTarget();
        if (target == null) {
            return false;
        }
        if (e.getStatusForServer() == GathererJournal.Status.GATHERING) {
            return true;
        }
        QT.VILLAGER_LOGGER.trace("Visitor has chosen {} as their target [{}]", target, e.getUUID());
        return true;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VisitorMobEntity entity, long p_22547_) {
        Optional<Long> since = entity.getBrain().getMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        if (since.isEmpty()) {
            entity.getBrain().setMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, level.getDayTime());
            return true;
        }
        long trying = level.getDayTime() - since.get();
        if (trying > PAUSE_TICKS) {
            QT.VILLAGER_LOGGER.debug("Giving up on target because it took too long to get there");
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
        // DO NOT CHANGE THIS. It makes path finding way less reliable.
        // If you need the entity to stand nearby a block rather than on it,
        // YOU need to do that math when choosing a target.
        int dist = 0;
        float speed = this.walkSpeed;
        double distToTarget = e.blockPosition().distSqr(bp);
        if (distToTarget > 500) { // TODO: Use navigation distance to account for complex paths
            speed = speed * 1.5f; // TODO: Define this as a field on the class?
        }
        if (distToTarget > 1) {
            e.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(bp, speed, dist));
        } else {
            Vec3 p20220 = Vec3.atCenterOf(bp);
            e.moveTo(p20220);
        }
        e.getBrain().eraseMemory(MemoryModuleType.DISABLE_WALK_TO_ADMIRE_ITEM);
        QT.VILLAGER_LOGGER.trace("{} navigating to {}", e.getUUID(), bp);
        e.getBrain().setMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, lvl.getDayTime());
    }
}
