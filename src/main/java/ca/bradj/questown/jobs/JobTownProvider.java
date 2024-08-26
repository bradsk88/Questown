package ca.bradj.questown.jobs;

import java.util.Collection;
import java.util.Map;

public interface JobTownProvider<ROOM> extends TownProvider {
    Collection<ROOM> roomsWithCompletedProduct();

    Map<Integer, Collection<ROOM>> roomsNeedingIngredientsByState();

    boolean isUnfinishedTimeWorkPresent();

    Collection<Integer> getStatesWithUnfinishedItemlessWork();

    Collection<ROOM> roomsAtState(Integer state);
}
