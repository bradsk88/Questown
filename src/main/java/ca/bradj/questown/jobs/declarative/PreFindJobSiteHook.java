package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.integration.SpecialRulesRegistry;
import ca.bradj.questown.integration.jobs.BeforeFindJobSiteEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.integration.jobs.UnsafeVillagerData;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.function.Consumer;

import static ca.bradj.questown.jobs.declarative.PrePostHooks.processMulti;

public class PreFindJobSiteHook {
    public static void run(
            Collection<String> rules,
            UnsafeVillagerData unsafeVillagerData,
            Consumer<WithReason<BlockPos>> applyWorkspotOverride
    ) {
        ImmutableList<JobPhaseModifier> appliers = SpecialRulesRegistry.getRuleAppliers(rules);
        BeforeFindJobSiteEvent evt = new BeforeFindJobSiteEvent(unsafeVillagerData, applyWorkspotOverride);
        processMulti(false, appliers, (o, a) -> {
            a.beforeFindJobSite(evt);
            return true;
        });
    }
}

