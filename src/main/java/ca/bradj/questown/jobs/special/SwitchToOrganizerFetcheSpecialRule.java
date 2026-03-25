package ca.bradj.questown.jobs.special;

import ca.bradj.questown.integration.jobs.BeforeMoveToNextStateEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.integration.jobs.QTNativeRule;

public class SwitchToOrganizerFetcheSpecialRule extends
        JobPhaseModifier implements QTNativeRule {

    @Override
    public Void beforeMoveToNextState(BeforeMoveToNextStateEvent event) {
        event.requestJobChange().accept(new JobID("organizer", "fetch"));
        return null;
    }
}
