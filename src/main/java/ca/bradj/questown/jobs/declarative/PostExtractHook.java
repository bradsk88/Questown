package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.integration.SpecialRulesRegistry;
import ca.bradj.questown.integration.jobs.AfterExtractEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.world.QTWorldAccess;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.function.BiFunction;

import static ca.bradj.questown.jobs.declarative.PrePostHooks.processMulti;

public class PostExtractHook {

    public static <TOWN> TOWN run(
            TOWN town,
            BlockPos townPos,
            Collection<String> rules,
            QTWorldAccess world,
            BlockPos position,
            BiFunction<TOWN, ImmutableMap<String, Integer>, TOWN> itemDataApplier,
            BiFunction<TOWN, Float, TOWN> hungerUpdater,
            AfterExtractEvent.MoodApplier<TOWN> moodUpdater
    ) {
        ImmutableList<JobPhaseModifier> appliers = SpecialRulesRegistry.getRuleAppliers(rules);
        AfterExtractEvent<TOWN> bxEvent = new AfterExtractEvent<>(
                world, position, townPos, itemDataApplier, hungerUpdater, moodUpdater
        );
        return processMulti(town, appliers, (o, a) -> a.afterExtract(o, bxEvent));
    }
}
