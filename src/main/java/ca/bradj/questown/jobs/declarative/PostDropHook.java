package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.integration.SpecialRulesRegistry;
import ca.bradj.questown.integration.jobs.AfterDropLootEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.world.QTWorldAccess;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.function.Consumer;

import static ca.bradj.questown.jobs.declarative.PrePostHooks.processMulti;

public class PostDropHook {

    public static void run(
            TownInterface town,
            Collection<String> rules,
            QTWorldAccess world,
            BlockPos chestPos,
            ImmutableList<MCHeldItem> itemsBeforeDrop,
            ImmutableList<MCHeldItem> itemsAfterDrop,
            Consumer<BlockPos> clearStatus
    ) {
        ImmutableList<JobPhaseModifier> appliers = SpecialRulesRegistry.getRuleAppliers(rules);
        AfterDropLootEvent bxEvent = new AfterDropLootEvent(world, chestPos, itemsBeforeDrop, itemsAfterDrop, clearStatus);
        processMulti(town, appliers, (o, a) -> a.afterDropLoot(o, bxEvent));
    }
}
