package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.WorkLocation;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.adapter.IRoomRecipeMatch;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public record BeforeTickEvent(
        WorkLocation locInfo,
        ImmutableList<MCHeldItem> heldItems,
        Consumer<Function<
                Supplier<Map<Integer, Collection<IRoomRecipeMatch<MCRoom, ResourceLocation, BlockPos, ?>>>>,
                Supplier<Map<Integer, Collection<IRoomRecipeMatch<MCRoom, ResourceLocation, BlockPos, ?>>>>
                >> replaceRoomCheck,
        Function<BlockPos, @NotNull State> getJobBlockState
) {
}
