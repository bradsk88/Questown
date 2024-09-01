package ca.bradj.questown.jobs.special;

import ca.bradj.questown.integration.jobs.BeforeMoveToNextStateEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.jobs.JobID;

public class SwitchToOrganizerFetcheSpecialRule extends
        JobPhaseModifier {

    @Override
    public Void beforeMoveToNextState(BeforeMoveToNextStateEvent event) {
        event.requestJobChange().accept(new JobID("organizer", "fetch"));
        return null;
    }
}
