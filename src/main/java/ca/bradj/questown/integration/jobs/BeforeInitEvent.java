package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.logic.PredicateCollection;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public record BeforeInitEvent(
        Supplier<ImmutableList<MCHeldItem>> heldItems,
        Consumer<Function<PredicateCollection<MCHeldItem, MCHeldItem>, PredicateCollection<MCHeldItem, MCHeldItem>>> replaceIngredients,
        Consumer<Function<PredicateCollection<MCTownItem, MCTownItem>, PredicateCollection<MCTownItem, MCTownItem>>> replaceTools,
        Consumer<Function<BiPredicate<ServerLevel, BlockPos>, BiPredicate<ServerLevel, BlockPos>>> jobBlockCheckReplacer) {
}
