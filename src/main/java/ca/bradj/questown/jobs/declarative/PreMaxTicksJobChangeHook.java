package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.integration.SpecialRulesRegistry;
import ca.bradj.questown.integration.jobs.BeforeMaxTicksJobChangeEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.integration.jobs.UnsafeVillagerData;
import com.google.common.collect.ImmutableList;

import java.util.Collection;

import static ca.bradj.questown.jobs.declarative.PrePostHooks.processMulti;

public class PreMaxTicksJobChangeHook {

    public static void run(
            Collection<String> rules,
            UnsafeVillagerData villagerData
    ) {
        ImmutableList<JobPhaseModifier> appliers = SpecialRulesRegistry.getRuleAppliers(rules);
        BeforeMaxTicksJobChangeEvent bxEvent = new BeforeMaxTicksJobChangeEvent(villagerData);
        processMulti(false, appliers, (o, a) -> {
            a.beforeMaxTicksJobChange(bxEvent);
            return true;
        });
    }
}
