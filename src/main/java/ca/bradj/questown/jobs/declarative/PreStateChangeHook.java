package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.integration.SpecialRulesRegistry;
import ca.bradj.questown.integration.jobs.BeforeMoveToNextStateEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.jobs.JobID;
import com.google.common.collect.ImmutableList;
import net.minecraft.world.entity.Pose;

import java.util.Collection;
import java.util.function.Consumer;

import static ca.bradj.questown.jobs.declarative.PrePostHooks.processMulti;

public class PreStateChangeHook {

    public static void run(
            Collection<String> rules,
            Consumer<Pose> requestPose,
            Consumer<JobID> requestJobChange
    ) {
        ImmutableList<JobPhaseModifier> appliers = SpecialRulesRegistry.getRuleAppliers(rules);
        BeforeMoveToNextStateEvent bxEvent = new BeforeMoveToNextStateEvent(requestPose, requestJobChange);
        processMulti(false, appliers, (o, a) -> {
            a.beforeMoveToNextState(bxEvent);
            return true;
        });
    }
}
