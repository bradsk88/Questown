package ca.bradj.questown.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import net.minecraft.server.level.ServerLevel;

import java.util.Collection;
import java.util.function.BiFunction;

public record WorkWorldInteractions(
        int actionDuration,
        BiFunction<ServerLevel, Collection<MCHeldItem>, Iterable<MCHeldItem>> resultGenerator
) {
}
