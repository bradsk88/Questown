package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.WorkLocation;
import ca.bradj.questown.jobs.production.RoomsNeedingVillagerInput;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.function.Function;

public record BeforeTickEvent(
        WorkLocation locInfo,
        java.util.function.Supplier<net.minecraft.server.level.ServerLevel> level, ImmutableList<MCHeldItem> heldItems,
        Consumer<Function<
                RoomsNeedingVillagerInput<MCRoom, ResourceLocation, BlockPos>,
                RoomsNeedingVillagerInput<MCRoom, ResourceLocation, BlockPos>
                >> replaceRoomCheck,
        Function<BlockPos, @NotNull State> getJobBlockState,
        boolean firstTick, java.util.function.Supplier<ImmutableList<BlockPos>> otherVillagerPositions,
        java.util.function.Supplier<BlockPos> randomWalkableTownPosition,
        java.util.function.BiConsumer<String, String> writeUnsafeDataToVillager
) {
}
