package ca.bradj.questown.jobs;

import ca.bradj.roomrecipes.adapter.IRoomRecipeMatch;
import ca.bradj.roomrecipes.core.Room;
import com.google.common.collect.ImmutableList;

public interface Dependencies2<ROOM extends Room, MATCH extends IRoomRecipeMatch<ROOM, ?, ?, ?>> {
    ImmutableList<MATCH> getRoomsWithCompletedProduct();
}
