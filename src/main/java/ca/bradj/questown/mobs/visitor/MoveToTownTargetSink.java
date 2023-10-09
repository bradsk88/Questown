package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.core.Config;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;

public class MoveToTownTargetSink extends MoveToTargetSink {

    public MoveToTownTargetSink() {
        super(Config.WANDER_GIVEUP_TICKS.get(), Config.WANDER_GIVEUP_TICKS.get());
    }

    @Override
    protected boolean checkExtraStartConditions(
            ServerLevel p_23583_,
            Mob p_23584_
    ) {
        p_23584_.getNavigation().setMaxVisitedNodesMultiplier(10.0F);
        boolean b = super.checkExtraStartConditions(p_23583_, p_23584_);
        p_23584_.getNavigation().resetMaxVisitedNodesMultiplier();
        return b;
    }

}
