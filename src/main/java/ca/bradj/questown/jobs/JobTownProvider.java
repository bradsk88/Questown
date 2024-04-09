package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.roomrecipes.core.Room;

import java.util.Collection;
import java.util.Map;

public interface JobTownProvider<ROOM extends Room, STATUS> extends TownProvider {
    Collection<ROOM> roomsWithCompletedProduct();

    Map<STATUS, Collection<ROOM>> roomsToGetSuppliesForByState();

    boolean isUnfinishedTimeWorkPresent();

    Collection<Integer> getStatesWithUnfinishedItemlessWork();
}
