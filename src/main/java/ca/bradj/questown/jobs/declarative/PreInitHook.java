package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.integration.SpecialRulesRegistry;
import ca.bradj.questown.integration.jobs.BeforeInitEvent;
import ca.bradj.questown.integration.jobs.JobPhaseModifier;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.logic.PredicateCollection;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Collection;
import java.util.function.*;

import static ca.bradj.questown.jobs.declarative.PrePostHooks.processMulti;

public class PreInitHook {

    public static void run(
            Collection<String> rules,
            Supplier<ServerLevel> level,
            Supplier<ImmutableList<MCHeldItem>> heldItems,
            Consumer<Function<PredicateCollection<MCHeldItem, MCHeldItem>, PredicateCollection<MCHeldItem, MCHeldItem>>> ingrReplacer,
            Consumer<Function<PredicateCollection<MCTownItem, MCTownItem>, PredicateCollection<MCTownItem, MCTownItem>>> toolReplacer,
            Consumer<Function<Predicate<BlockPos>, BiPredicate<ImmutableList<MCHeldItem>, BlockPos>>> jobBlockCheckReplacer,
            Consumer<Function<Predicate<RoomRecipeMatch<MCRoom>>, Predicate<RoomRecipeMatch<MCRoom>>>> supplyRoomCheckReplacer
    ) {
        ImmutableList<JobPhaseModifier> appliers = SpecialRulesRegistry.getRuleAppliers(rules);
        BeforeInitEvent bxEvent = new BeforeInitEvent(level, heldItems, ingrReplacer, toolReplacer, jobBlockCheckReplacer, supplyRoomCheckReplacer);
        processMulti(false, appliers, (o, a) -> {
            a.beforeInit(bxEvent);
            return true;
        });
    }
}
