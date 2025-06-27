package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.integration.SpecialRulesRegistry;
import ca.bradj.questown.integration.jobs.AfterInsertItemEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.jobs.WorkedSpot;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.UUID;
import java.util.function.Function;

import static ca.bradj.questown.jobs.declarative.PrePostHooks.processMulti;

public class PostInsertHook {

    public static <TOWN> TOWN run(
            TOWN town,
            Collection<String> rules,
            ServerLevel level,
            WorkedSpot<BlockPos> position,
            ItemStack item,
            Function<TOWN, TOWN> bopClearer,
            UUID inserter
    ) {
        ImmutableList<JobPhaseModifier> appliers = SpecialRulesRegistry.getRuleAppliers(rules);
        AfterInsertItemEvent<TOWN> bxEvent = new AfterInsertItemEvent<>(level, item, position, bopClearer, inserter);
        return processMulti(town, appliers, (o, a) -> a.afterInsertItem(o, bxEvent));
    }
}
