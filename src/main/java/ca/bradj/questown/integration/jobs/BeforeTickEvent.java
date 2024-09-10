package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public record BeforeTickEvent(
        ImmutableList<MCHeldItem> heldItems,
        Consumer<Function<Supplier<Map<Integer, Collection<MCRoom>>>, Supplier<Map<Integer, Collection<MCRoom>>>>> replaceRoomCheck,
        Function<BlockPos, @NotNull State> getJobBlockState
) {
}
