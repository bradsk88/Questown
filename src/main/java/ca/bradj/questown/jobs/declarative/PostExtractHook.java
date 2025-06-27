package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.integration.SpecialRulesRegistry;
import ca.bradj.questown.integration.jobs.AfterExtractEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Collection;
import java.util.function.BiFunction;

import static ca.bradj.questown.jobs.declarative.PrePostHooks.processMulti;

public class PostExtractHook {

    public static <TOWN> TOWN run(
            TOWN town,
            BlockPos townPos,
            Collection<String> rules,
            ServerLevel level,
            BlockPos position,
            BiFunction<TOWN, ImmutableMap<String, Integer>, TOWN> itemDataApplier
    ) {
        ImmutableList<JobPhaseModifier> appliers = SpecialRulesRegistry.getRuleAppliers(rules);
        AfterExtractEvent<TOWN> bxEvent = new AfterExtractEvent<>(
                level, position, townPos, itemDataApplier
        );
        return processMulti(town, appliers, (o, a) -> a.afterExtract(o, bxEvent));
    }
}
