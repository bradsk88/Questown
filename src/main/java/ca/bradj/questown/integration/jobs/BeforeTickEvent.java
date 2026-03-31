package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.WorkLocation;
import ca.bradj.questown.jobs.production.RoomsNeedingVillagerInput;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.questown.world.QTWorldAccess;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public record BeforeTickEvent(
        WorkLocation locInfo,
        Supplier<QTWorldAccess> world, ImmutableList<MCHeldItem> heldItems,
        Consumer<Function<
                RoomsNeedingVillagerInput<MCRoom, ResourceLocation, BlockPos>,
                RoomsNeedingVillagerInput<MCRoom, ResourceLocation, BlockPos>
                >> replaceRoomCheck,
        Function<BlockPos, @NotNull State> getJobBlockState,
        boolean firstTick,
        BlockPos position,
        Supplier<ImmutableList<BlockPos>> otherVillagerPositions,
        Supplier<BlockPos> randomWalkableTownPosition,
        UnsafeVillagerData villagerData
) {
}
