package ca.bradj.questown.town.interfaces;

import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.core.Room;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;

public interface RoomMatchFinder<T extends Room> {
    Collection<RoomRecipeMatch<T>> getRoomsMatching(ResourceLocation roomRecipeId);
}
