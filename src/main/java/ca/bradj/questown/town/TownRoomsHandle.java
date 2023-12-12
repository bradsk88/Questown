package ca.bradj.questown.town;

import ca.bradj.questown.town.interfaces.RoomsHolder;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.serialization.MCRoom;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class TownRoomsHandle implements RoomsHolder {

    @Nullable
    private TownFlagBlockEntity town;

    public void initialize(TownFlagBlockEntity t) {
        this.town = t;
    }

    /**
     * Only safe to call after initialize
     */
    private @NotNull TownFlagBlockEntity unsafeGetTown() {
        if (town == null) {
            throw new IllegalStateException("Town has not been initialized on quest handle yet");
        }
        return town;
    }

    @Override
    public Collection<RoomRecipeMatch<MCRoom>> getRoomsMatching(ResourceLocation roomRecipeId) {
        @NotNull TownFlagBlockEntity t = unsafeGetTown();
        return t.getRoomsMatching(roomRecipeId);
    }
}
