package ca.bradj.questown.mobs.visitor;

import net.minecraft.world.entity.Mob;

public class OpenGateGoal extends GateInteractGoal {
    private final boolean closeDoor;
    private int forgetTime;

    public OpenGateGoal(Mob p_25678_, boolean p_25679_) {
        super(p_25678_);
        this.mob = p_25678_;
        this.closeDoor = p_25679_;
    }

    public boolean canContinueToUse() {
        return this.closeDoor && this.forgetTime > 0 && super.canContinueToUse();
    }

    public void start() {
        this.forgetTime = 20;
        this.setOpen(true);
    }

    public void stop() {
        this.setOpen(false);
    }

    public void tick() {
        --this.forgetTime;
        super.tick();
    }
}
