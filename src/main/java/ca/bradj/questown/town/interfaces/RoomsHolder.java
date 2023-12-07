package ca.bradj.questown.town.interfaces;

import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.serialization.MCRoom;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;

public interface RoomsHolder {
    Collection<RoomRecipeMatch<MCRoom>> getRoomsMatching(ResourceLocation roomRecipeId);
}
