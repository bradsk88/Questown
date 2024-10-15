package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.logic.PredicateCollection;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.function.*;

public record BeforeInitEvent(
        Supplier<ServerLevel> level,
        Consumer<Function<PredicateCollection<MCHeldItem, MCHeldItem>, PredicateCollection<MCHeldItem, MCHeldItem>>> replaceIngredients,
        Consumer<Function<PredicateCollection<MCTownItem, MCTownItem>, PredicateCollection<MCTownItem, MCTownItem>>> replaceTools,
        Consumer<Function<Predicate<BlockPos>, BiPredicate<ImmutableList<MCHeldItem>, BlockPos>>> jobBlockCheckReplacer,
        Consumer<Function<Predicate<RoomRecipeMatch<MCRoom>>, Predicate<RoomRecipeMatch<MCRoom>>>> supplyRoomCheckReplacer
) {
}
