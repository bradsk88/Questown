package ca.bradj.questown.jobs.special;

import ca.bradj.questown.integration.jobs.BeforeMoveToNextStateEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import net.minecraft.world.entity.Pose;
import ca.bradj.questown.integration.jobs.QTNativeRule;

public class LieOnWorkspotSpecialRule extends
        JobPhaseModifier implements QTNativeRule {
    @Override
    public Void beforeMoveToNextState(BeforeMoveToNextStateEvent event) {
        event.requestPose().accept(Pose.SLEEPING);
        return null;
    }
}
