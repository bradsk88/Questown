package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public record BeforeTickEvent(
        ImmutableList<MCHeldItem> heldItems,
        Set<Integer> states,
        Consumer<Function<Supplier<Map<Integer, Collection<MCRoom>>>, Supplier<Map<Integer, Collection<MCRoom>>>>> replaceRoomCheck
) {
}
