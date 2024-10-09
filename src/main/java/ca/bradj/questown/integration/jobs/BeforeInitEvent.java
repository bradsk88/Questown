package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.logic.PredicateCollection;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public record BeforeInitEvent(
        Supplier<ImmutableList<MCHeldItem>> heldItems,
        Consumer<Function<PredicateCollection<MCHeldItem, MCHeldItem>, PredicateCollection<MCHeldItem, MCHeldItem>>> replaceIngredients,
        Consumer<Function<PredicateCollection<MCTownItem, MCTownItem>, PredicateCollection<MCTownItem, MCTownItem>>> replaceTools,
        Consumer<Function<Predicate<BlockPos>, Predicate<BlockPos>>> jobBlockCheckReplacer,
        Consumer<Function<Predicate<RoomRecipeMatch<MCRoom>>, Predicate<RoomRecipeMatch<MCRoom>>>> supplyRoomCheckReplacer
) {
}
