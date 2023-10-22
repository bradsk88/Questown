package ca.bradj.questown.jobs;

import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.core.Room;
import org.jetbrains.annotations.Nullable;

public interface EntityLocStateProvider<ROOM extends Room> {
    @Nullable RoomRecipeMatch<ROOM> getEntityCurrentJobSite();
}
