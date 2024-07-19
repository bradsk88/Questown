package ca.bradj.questown.mobs.visitor;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;

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
            e.getLookControl()
             .setLookAt(look.getX() + 0.5f, look.getY(), look.getZ() + 0.5f);
        }
    }
}
