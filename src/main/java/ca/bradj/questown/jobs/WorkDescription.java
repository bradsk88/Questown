package ca.bradj.questown.jobs;

import ca.bradj.questown.integration.minecraft.MCTownItem;
import com.google.common.collect.ImmutableSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public record WorkDescription(
        Function<WorksBehaviour.TownData, ImmutableSet<MCTownItem>> currentlyPossibleResults,
        Function<ServerLevel, @Nullable ItemStack> initialRequest
) {
}
