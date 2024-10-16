package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.serialization.MCRoom;

import java.util.Collection;
import java.util.function.BiPredicate;

public interface SupplyRoomCheck extends BiPredicate<Collection<MCHeldItem>, RoomRecipeMatch<MCRoom>> {
}
