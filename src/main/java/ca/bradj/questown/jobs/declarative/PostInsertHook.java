package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.integration.SpecialRules;
import ca.bradj.questown.integration.jobs.AfterInsertItemEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.jobs.WorkSpot;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;

import static ca.bradj.questown.jobs.declarative.PrePostHooks.processMulti;

public class PostInsertHook {

    public static <TOWN> TOWN run(
            TOWN town,
            Collection<String> rules,
            ServerLevel level,
            WorkSpot<Integer, BlockPos> position,
            ItemStack item
    ) {
        ImmutableList<JobPhaseModifier> appliers = SpecialRules.getRuleAppliers(rules);
        AfterInsertItemEvent bxEvent = new AfterInsertItemEvent(level, item, position);
        return processMulti(town, appliers, (o, a) -> a.afterInsertItem(o, bxEvent));
    }
}
