package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.QT;
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
        super(ImmutableMap.of());
    }

    @Override
    protected void start(
            ServerLevel lvl,
            VisitorMobEntity e,
            long p_23884_
    ) {
        this.look = e.getLookTarget();
        if (look != null) {
            QT.VILLAGER_LOGGER.debug("looking at {}", look);
            e.getLookControl()
             .setLookAt(look.getX(), look.getY(), look.getZ());
        }
    }
}
