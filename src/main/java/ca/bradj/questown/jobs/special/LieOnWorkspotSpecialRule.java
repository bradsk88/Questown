package ca.bradj.questown.jobs.special;

import ca.bradj.questown.integration.jobs.AfterInsertItemEvent;
import ca.bradj.questown.integration.jobs.BeforeExtractEvent;
import ca.bradj.questown.integration.jobs.BeforeMoveToNextStateEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import net.minecraft.world.entity.Pose;
import org.jetbrains.annotations.Nullable;

public class LieOnWorkspotSpecialRule extends
        JobPhaseModifier {
    @Override
    public Void beforeMoveToNextState(BeforeMoveToNextStateEvent event) {
        event.requestPose().accept(Pose.SLEEPING);
        return null;
    }
}
