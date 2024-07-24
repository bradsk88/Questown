package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.integration.SpecialRulesRegistry;
import ca.bradj.questown.integration.jobs.BeforeStateChangeEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.jobs.WorkSpot;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Collection;

import static ca.bradj.questown.jobs.declarative.PrePostHooks.processMulti;

public class PreStateChangeHook {

    public static void run(
            Collection<String> rules,
            ServerLevel level,
            WorkSpot<Integer, BlockPos> position
    ) {
        ImmutableList<JobPhaseModifier> appliers = SpecialRulesRegistry.getRuleAppliers(rules);
        BeforeStateChangeEvent bxEvent = new BeforeStateChangeEvent(level, position);
        processMulti(false, appliers, (o, a) -> {
            a.beforeStateChange(bxEvent);
            return true;
        });
    }
}
