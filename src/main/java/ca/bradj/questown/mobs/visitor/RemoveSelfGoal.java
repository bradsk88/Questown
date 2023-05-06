package ca.bradj.questown.mobs.visitor;

import net.minecraft.world.entity.ai.goal.Goal;

public class RemoveSelfGoal extends Goal {
    private final VisitorMobEntity entity;

    public RemoveSelfGoal(VisitorMobEntity entity) {
        this.entity = entity;
    }

    @Override
    public boolean canUse() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (entity.townFlag == null) {
            entity.kill();
        }
    }
}
