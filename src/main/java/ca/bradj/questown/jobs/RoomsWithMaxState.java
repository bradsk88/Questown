package ca.bradj.questown.jobs;

import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.adapter.IRoomRecipeMatch;
import ca.bradj.roomrecipes.core.Room;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

public class RoomsWithMaxState< ROOM extends Room, POS, MATCH extends IRoomRecipeMatch<ROOM, ?, POS, ?>, LOCATION> {
    private final LOCATION location;
    private final Integer maxState;

    public RoomsWithMaxState(
            LOCATION location,
            int maxState
    ) {
        this.location = location;
        this.maxState = maxState;
    }

    public Collection<MATCH> get(
            Function<LOCATION, ImmutableList<MATCH>> getRoomsMatching,
            BiPredicate<LOCATION, POS> isCorrectBlock,
            Function<POS, State> getJobBlockState
    ) {
//        Collection<RoomRecipeMatch<MCRoom>> rooms = town.getRoomHandle().getRoomsMatching(location.baseRoom());
        ImmutableList<MATCH> rooms = getRoomsMatching.apply(location);
        return JobsClean.roomsWithState(
                rooms, p -> isCorrectBlock.test(location, p), (bp) -> {
                    State jbs = getJobBlockState.apply(bp);
                    if (jbs == null) {
                        return false;
                    }
                    return maxState.equals(jbs.processingState());
                }
        );
    }
}
