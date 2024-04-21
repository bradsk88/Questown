package ca.bradj.questown.jobs;

import ca.bradj.roomrecipes.core.Room;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.Collection;

public interface JobTownProvider<ROOM extends Room, STATUS> extends TownProvider {
    Collection<ROOM> roomsWithCompletedProduct();

    ImmutableMap<STATUS, ImmutableList<Room>> roomsToGetSuppliesForByState();

    boolean isUnfinishedTimeWorkPresent();

    Collection<Integer> getStatesWithUnfinishedItemlessWork();
}
