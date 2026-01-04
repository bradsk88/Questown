package ca.bradj.questown.jobs.special;

import ca.bradj.questown.integration.jobs.BeforeMoveToNextStateEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import net.minecraft.world.entity.Pose;

public class LieOnWorkspotSpecialRule extends
        JobPhaseModifier {
    @Override
    public Void beforeMoveToNextState(BeforeMoveToNextStateEvent event) {
        event.requestPose().accept(Pose.SLEEPING);
        return null;
    }
}
