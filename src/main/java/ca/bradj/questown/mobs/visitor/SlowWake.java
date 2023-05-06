package ca.bradj.questown.mobs.visitor;

import net.minecraft.world.entity.ai.goal.Goal;

public class SlowWake extends Goal {
    private final VisitorMobEntity entity;
    private int sittingTicks = 0;

    public SlowWake(VisitorMobEntity entity) {
        this.entity = entity;
    }

    @Override
    public boolean canUse() {
        return entity.sitting;
    }

    @Override
    public void tick() {
        super.tick();
        if (sittingTicks > this.adjustedTickDelay(100)) {
            entity.sitting = false;
        }
        if (entity.hurtMarked) {
            entity.sitting = false;
        }
        sittingTicks++;
    }
}
